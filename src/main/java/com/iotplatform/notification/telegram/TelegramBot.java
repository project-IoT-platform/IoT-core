package com.iotplatform.notification.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.iotplatform.notification.data.ChatLinks;
import com.iotplatform.notification.data.PlatformData;
import com.iotplatform.notification.exception.NotFoundException;
import com.iotplatform.notification.exception.ValidationException;
import com.iotplatform.notification.model.NormOperator;
import com.iotplatform.notification.model.Rule;
import com.iotplatform.notification.model.User;
import com.iotplatform.notification.service.NotificationService;

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

/**
 * Telegram-бот: опрашивает Telegram, привязывает чат, когда пользователь присылает свой UUID,
 * и отвечает на команды. /settings показывает границы устройства, /norm меняет норму в таблицах rule и
 * device_to_rule. Команда /deviation убрана. В группах бот молчит.
 */
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
            /settings <id устройства> — нижняя граница батареи и границы по каждому типу телеметрии
            /norm <id устройства> <тип телеметрии> <>= или <=> <число> — изменить норму
                (пример: /norm 123e4567-e89b-12d3-a456-426614174000 1 <= 30)
            /battery <id устройства> <процент> — изменить минимальный заряд батареи
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
    private final ChatLinks links;
    private final ZoneId zone;   // в каком поясе читать время без указания пояса

    private volatile boolean running;
    private Thread thread;

    public TelegramBot(TelegramClient telegram, NotificationService notifications,
                       PlatformData platform, ChatLinks links, ZoneId zone) {
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
        if (text.isEmpty() || !"private".equals(chat.path("type").asText())) return;   // только личные чаты
        long chatId = chat.path("id").asLong();
        telegram.sendMessage(chatId, handle(chatId, text));
    }

    /** Ответ бота на входящее сообщение. public, чтобы его можно было проверить без сети. */
    public String handle(long chatId, String text) {
        LOG.log(System.Logger.Level.INFO, "Telegram, чат " + chatId + ": "
                + (parseUuid(text).isPresent() ? "прислан id пользователя" : command(text)));
        try {
            Optional<UUID> sentId = parseUuid(text);
            if (sentId.isPresent()) return login(chatId, sentId.get());

            Optional<UUID> userId = links.findUserId(chatId);
            if (userId.isEmpty()) return ASK_ID;

            Optional<User> user = platform.findUser(userId.get());
            if (user.isEmpty()) {
                links.delete(chatId);
                return "Ваш аккаунт на платформе не найден.\n" + ASK_ID;
            }
            return handleCommand(text, user.get());
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Ошибка обработки сообщения из чата " + chatId + ": " + command(text), e);
            return "Внутренняя ошибка.";
        }
    }

    private String login(long chatId, UUID userId) {
        Optional<User> user = platform.findUser(userId);
        if (user.isEmpty()) {
            return "Пользователь с таким id не найден. Проверьте id и отправьте ещё раз.";
        }
        links.save(chatId, userId);
        return "Готово, " + user.get().displayName() + "! Теперь вам будут приходить уведомления.\n\n" + HELP;
    }

    /** Для логов: только сама команда и аргументы, первые 100 символов. */
    private static String command(String text) {
        return text.length() > 100 ? text.substring(0, 100) + "…" : text;
    }

    private static Optional<UUID> parseUuid(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String handleCommand(String text, User user) {
        String[] p = text.split("\\s+");
        String cmd = p[0].toLowerCase(Locale.ROOT);
        int at = cmd.indexOf('@');
        if (at > 0) cmd = cmd.substring(0, at);

        try {
            return switch (cmd) {
                case "/start", "/help" -> "Вы вошли как " + user.displayName() + ".\n\n" + HELP;
                case "/status" -> notifications.statusText(UUID.fromString(arg(p, 1)), user);
                case "/avg" -> avgCommand(p, user);
                case "/settings" -> notifications.settingsText(UUID.fromString(arg(p, 1)), user);
                case "/norm" -> normCommand(p, user);
                case "/battery" -> {
                    UUID id = UUID.fromString(arg(p, 1));
                    notifications.updateRule(id, user, new Rule(Integer.parseInt(arg(p, 2)), null, null));
                    yield "Минимальный заряд изменён.\n" + notifications.settingsText(id, user);
                }
                default -> "Неизвестная команда.\n" + HELP;
            };
        } catch (NotFoundException e) {
            return e.getMessage();
        } catch (ValidationException e) {
            return "Некорректное значение: " + e.getErrors();
        } catch (IllegalArgumentException e) {
            return "Неверный формат команды.\n" + HELP;
        }
    }

    private static final String NORM_USAGE = """
            Формат: /norm <id устройства> <тип телеметрии> <>= или <=> <число>
            Пример: /norm 123e4567-e89b-12d3-a456-426614174000 1 <= 30""";

    /** /norm <id> <тип> <>= или <=> <число>: создать или изменить норму устройства. */
    private String normCommand(String[] p, User user) {
        if (p.length != 5) return NORM_USAGE;
        UUID id = UUID.fromString(p[1]);
        short type;
        try {
            type = Short.parseShort(p[2]);
        } catch (NumberFormatException e) {
            return "Тип телеметрии должен быть целым числом.\n" + NORM_USAGE;
        }
        Optional<NormOperator> op = NormOperator.fromSymbol(p[3]);
        if (op.isEmpty()) return "Оператор должен быть >= или <=.\n" + NORM_USAGE;
        return notifications.setNorm(id, user, type, op.get(), number(p[4]));
    }

    /** /avg <id> [момент]: среднее значение телеметрии начиная с момента. */
    private String avgCommand(String[] p, User user) {
        UUID deviceId = UUID.fromString(arg(p, 1));
        Optional<Instant> since = parseSince(p);
        if (since.isEmpty()) return AVG_USAGE;
        if (since.get().isAfter(Instant.now())) {
            return "Этот момент ещё не наступил, укажите момент в прошлом.";
        }
        return notifications.averageText(deviceId, user, since.get(), zone);
    }

    /** Часть команды после id превращается в момент. Нет момента: последний час. Не распознано: пусто. */
    private Optional<Instant> parseSince(String[] p) {
        if (p.length < 3) return Optional.of(Instant.now().minus(Duration.ofHours(1)));
        String s = String.join(" ", Arrays.copyOfRange(p, 2, p.length)).trim();

        Matcher m = RELATIVE.matcher(s.toLowerCase(Locale.ROOT));
        if (m.matches()) {   // «сколько назад»: 30m, 2h, 1d
            long n = Long.parseLong(m.group(1));
            Duration d = switch (m.group(2)) {
                case "m", "м" -> Duration.ofMinutes(n);
                case "h", "ч" -> Duration.ofHours(n);
                default -> Duration.ofDays(n);
            };
            return Optional.of(Instant.now().minus(d));
        }
        for (DateTimeFormatter f : DATE_TIME_FORMATS) {   // дата и время
            try {
                return Optional.of(LocalDateTime.parse(s, f).atZone(zone).toInstant());
            } catch (DateTimeParseException ignored) { }
        }
        for (DateTimeFormatter f : DATE_FORMATS) {   // только дата: с начала суток
            try {
                return Optional.of(LocalDate.parse(s, f).atStartOfDay(zone).toInstant());
            } catch (DateTimeParseException ignored) { }
        }
        try {   // только время: сегодня
            return Optional.of(LocalDate.now(zone).atTime(LocalTime.parse(s, TIME_FORMAT)).atZone(zone).toInstant());
        } catch (DateTimeParseException ignored) { }
        try {   // полный ISO-момент
            return Optional.of(Instant.parse(s));
        } catch (DateTimeParseException ignored) { }
        return Optional.empty();
    }

    private static String arg(String[] p, int index) {
        if (p.length <= index) throw new IllegalArgumentException("нет аргумента");
        return p[index];
    }

    private static double number(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }
}