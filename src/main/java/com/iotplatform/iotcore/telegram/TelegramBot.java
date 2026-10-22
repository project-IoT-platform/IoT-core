package com.iotplatform.iotcore.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.repository.TelegramLinkRepository;
import com.iotplatform.iotcore.repository.UserRepository;
import com.iotplatform.iotcore.service.NotificationService;
import com.iotplatform.iotcore.service.NotificationService.Settings;

import java.util.Optional;
import java.util.UUID;

public class TelegramBot {
    private static final System.Logger LOG = System.getLogger(TelegramBot.class.getName());

    private static final String ASK_ID =
            "Привет! Чтобы пользоваться ботом, отправьте ваш id пользователя на платформе "
                    + "(в формате 123e4567-e89b-12d3-a456-426614174000).";

    private static final String HELP = """
            Команды:
            /status <id устройства> — включено или выключено
            /avg <id устройства> — среднее значение за последний час
            /settings — ваши норма и отклонение
            /norm <число> — изменить норму
            /deviation <число> — изменить допустимое отклонение
            """;

    private final TelegramClient telegram;
    private final NotificationService notifications;
    private final UserRepository users;
    private final TelegramLinkRepository links;

    private volatile boolean running;
    private Thread thread;

    public TelegramBot(TelegramClient telegram, NotificationService notifications,
                       UserRepository users, TelegramLinkRepository links) {
        this.telegram = telegram;
        this.notifications = notifications;
        this.users = users;
        this.links = links;
    }

    public void start() {
        if (!telegram.isConfigured()) {
            LOG.log(System.Logger.Level.WARNING, "Telegram-бот не запущен: не задан TELEGRAM_BOT_TOKEN");
            return;
        }
        running = true;
        thread = new Thread(this::pollLoop, "telegram-bot");
        thread.setDaemon(true);
        thread.start();
        LOG.log(System.Logger.Level.INFO, "Telegram-бот запущен");
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    private void pollLoop() {
        long offset = 0;
        while (running) {
            try {
                for (JsonNode update : telegram.getUpdates(offset)) {
                    offset = update.path("update_id").asLong() + 1;
                    processUpdate(update);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOG.log(System.Logger.Level.ERROR, "Ошибка опроса Telegram", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void processUpdate(JsonNode update) {
        JsonNode message = update.path("message");
        JsonNode chat = message.path("chat");
        String text = message.path("text").asText("").trim();
        // только личные переписки, в группах бот молчит
        if (text.isEmpty() || !"private".equals(chat.path("type").asText())) return;
        long chatId = chat.path("id").asLong();
        telegram.sendMessage(chatId, handle(chatId, text));
    }

    private String handle(long chatId, String text) {
        try {
            Optional<UUID> sentId = parseUuid(text);
            if (sentId.isPresent()) return login(chatId, sentId.get());

            Optional<UUID> userId = links.findUserId(chatId);
            if (userId.isEmpty()) return ASK_ID;

            Optional<UserEntity> user = users.findById(userId.get());
            if (user.isEmpty()) {
                links.delete(chatId);
                return "Ваш аккаунт на платформе не найден.\n" + ASK_ID;
            }

            return handleCommand(text, user.get());
        } catch (UnsupportedOperationException e) {
            return "Сервис временно недоступен, попробуйте позже.";
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Ошибка обработки сообщения", e);
            return "Внутренняя ошибка.";
        }
    }

    private String login(long chatId, UUID userId) {
        Optional<UserEntity> user = users.findById(userId);
        if (user.isEmpty()) {
            return "Пользователь с таким id не найден. Проверьте id и отправьте ещё раз.";
        }
        links.save(chatId, userId);
        return "Готово, " + displayName(user.get()) + "! Теперь вам будут приходить уведомления.\n\n" + HELP;
    }

    private static String displayName(UserEntity user) {
        return user.getUsername() != null ? user.getUsername() : user.getEmail();
    }

    private static Optional<UUID> parseUuid(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String handleCommand(String text, UserEntity user) {
        String[] p = text.split("\\s+");
        String cmd = p[0].toLowerCase();
        int at = cmd.indexOf('@');
        if (at > 0) cmd = cmd.substring(0, at);

        try {
            return switch (cmd) {
                case "/start", "/help" -> "Вы вошли как " + displayName(user) + ".\n\n" + HELP;
                case "/status" -> notifications.deviceStatusText(UUID.fromString(arg(p)), user);
                case "/avg" -> notifications.deviceAverageText(UUID.fromString(arg(p)), user);
                case "/settings" -> notifications.settingsText(user.getId());
                case "/norm" -> {
                    notifications.updateUserSettings(user.getId(), new Settings(number(arg(p)), null));
                    yield "Норма изменена.\n" + notifications.settingsText(user.getId());
                }
                case "/deviation" -> {
                    notifications.updateUserSettings(user.getId(), new Settings(null, number(arg(p))));
                    yield "Отклонение изменено.\n" + notifications.settingsText(user.getId());
                }
                default -> "Неизвестная команда.\n" + HELP;
            };
        } catch (DeviceNotFoundException e) {
            return e.getMessage();
        } catch (ValidationException e) {
            return "Некорректное значение: " + e.getErrors();
        } catch (IllegalArgumentException e) {
            return "Неверный формат команды.\n" + HELP;
        }
    }

    private static String arg(String[] p) {
        if (p.length < 2) throw new IllegalArgumentException("нет аргумента");
        return p[1];
    }

    private static double number(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }
}