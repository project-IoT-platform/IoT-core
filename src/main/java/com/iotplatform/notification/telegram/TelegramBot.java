package com.iotplatform.notification.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.iotplatform.notification.NotificationService;
import com.iotplatform.notification.NotificationService.Settings;
import com.iotplatform.notification.exception.NotificationValidationException;
import com.iotplatform.notification.exception.UnknownDeviceException;
import com.iotplatform.notification.port.PlatformData;
import com.iotplatform.notification.port.PlatformData.UserInfo;
import com.iotplatform.notification.port.TelegramLinks;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelegramBot {
    private static final System.Logger LOG = System.getLogger(TelegramBot.class.getName());

    private static final String ASK_ID =
            "Привет! Чтобы пользоваться ботом, отправьте ваш id пользователя на платформе "
                    + "(в формате 123e4567-e89b-12d3-a456-426614174000).";

    private static final String HELP = """
            Команды:
            /status <id устройства> — включено или выключено
            /avg <id устройства> [с какого момента] — среднее значение телеметрии с этого момента
                (без момента — за последний час). Примеры момента: 30m, 2h, 1d, 2026-10-31 12:00, 31.10.2026, 12:00
            /settings — ваши норма и отклонение
            /norm <число> — изменить норму
            /deviation <число> — изменить допустимое отклонение
            """;

    private static final String AVG_USAGE = """
            Не удалось понять момент начала.
            Формат: /avg <id устройства> [момент]
            Момент: 30m (минут назад), 2h (часов назад), 1d (дней назад),
            2026-10-31 12:00, 2026-10-31, 31.10.2026 12:00, 12:00 (сегодня).
            Без момента считается последний час.""";

    private static final Pattern RELATIVE = Pattern.compile("^(\\d{1,6})\\s*([mhdмчд])$");
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final TelegramClient telegram;
    private final NotificationService notifications;
    private final PlatformData platform;
    private final TelegramLinks links;
    private final ZoneId zone;

    private volatile boolean running;
    private Thread thread;

    public TelegramBot(TelegramClient telegram, NotificationService notifications,
                       PlatformData platform, TelegramLinks links, ZoneId zone) {
        this.telegram = telegram;
        this.notifications = notifications;
        this.platform = platform;
        this.links = links;
        this.zone = zone;
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

    /** Ответ бота на входящее сообщение. */
    public String handle(long chatId, String text) {
        try {
            Optional<UUID> sentId = parseUuid(text);
            if (sentId.isPresent()) return login(chatId, sentId.get());

            Optional<UUID> userId = links.findUserId(chatId);
            if (userId.isEmpty()) return ASK_ID;

            Optional<UserInfo> user = platform.findUser(userId.get());
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
        Optional<UserInfo> user = platform.findUser(userId);
        if (user.isEmpty()) {
            return "Пользователь с таким id не найден. Проверьте id и отправьте ещё раз.";
        }
        links.save(chatId, userId);
        return "Готово, " + user.get().displayName() + "! Теперь вам будут приходить уведомления.\n\n" + HELP;
    }

    private static Optional<UUID> parseUuid(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String handleCommand(String text, UserInfo user) {
        String[] p = text.split("\\s+");
        String cmd = p[0].toLowerCase(Locale.ROOT);
        int at = cmd.indexOf('@');
        if (at > 0) cmd = cmd.substring(0, at);

        try {
            return switch (cmd) {
                case "/start", "/help" -> "Вы вошли как " + user.displayName() + ".\n\n" + HELP;
                case "/status" -> notifications.deviceStatusText(UUID.fromString(arg(p)), user);
                case "/avg" -> avgCommand(p, user);
                case "/settings" -> notifications.settingsText(user.id());
                case "/norm" -> {
                    notifications.updateUserSettings(user.id(), new Settings(number(arg(p)), null));
                    yield "Норма изменена.\n" + notifications.settingsText(user.id());
                }
                case "/deviation" -> {
                    notifications.updateUserSettings(user.id(), new Settings(null, number(arg(p))));
                    yield "Отклонение изменено.\n" + notifications.settingsText(user.id());
                }
                default -> "Неизвестная команда.\n" + HELP;
            };
        } catch (UnknownDeviceException e) {
            return e.getMessage();
        } catch (NotificationValidationException e) {
            return "Некорректное значение: " + e.getErrors();
        } catch (IllegalArgumentException e) {
            return "Неверный формат команды.\n" + HELP;
        }
    }

    /** /avg <id> [момент]: среднее значение телеметрии начиная с момента. */
    private String avgCommand(String[] p, UserInfo user) {
        UUID deviceId = UUID.fromString(arg(p));
        Optional<Instant> since = parseSince(p);
        if (since.isEmpty()) return AVG_USAGE;
        if (since.get().isAfter(Instant.now())) {
            return "Этот момент ещё не наступил, укажите момент в прошлом.";
        }
        return notifications.deviceAverageText(deviceId, user, since.get(), zone);
    }

    /** Часть команды после id превращается в момент. Нет момента: последний час. Не распознано: пусто. */
    private Optional<Instant> parseSince(String[] p) {
        if (p.length < 3) return Optional.of(Instant.now().minus(Duration.ofHours(1)));
        String s = String.join(" ", Arrays.copyOfRange(p, 2, p.length)).trim();

        Matcher m = RELATIVE.matcher(s.toLowerCase(Locale.ROOT));
        if (m.matches()) {
            long n = Long.parseLong(m.group(1));
            Duration d = switch (m.group(2)) {
                case "m", "м" -> Duration.ofMinutes(n);
                case "h", "ч" -> Duration.ofHours(n);
                default -> Duration.ofDays(n);
            };
            return Optional.of(Instant.now().minus(d));
        }
        for (DateTimeFormatter f : DATE_TIME_FORMATS) {
            try {
                return Optional.of(LocalDateTime.parse(s, f).atZone(zone).toInstant());
            } catch (DateTimeParseException ignored) { }
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(s, f).atStartOfDay(zone).toInstant());
            } catch (DateTimeParseException ignored) { }
        }
        try {
            return Optional.of(LocalDate.now(zone).atTime(LocalTime.parse(s, TIME_FORMAT)).atZone(zone).toInstant());
        } catch (DateTimeParseException ignored) { }
        try {
            return Optional.of(Instant.parse(s));
        } catch (DateTimeParseException ignored) { }
        return Optional.empty();
    }

    private static String arg(String[] p) {
        if (p.length < 2) throw new IllegalArgumentException("нет аргумента");
        return p[1];
    }

    private static double number(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }
}