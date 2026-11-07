package com.iotplatform.notification.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * Все настройки сервиса в одном месте. Секреты (токен, пароли) берутся только из переменных окружения
 * или из файла, путь к которому в NOTIFICATION_CONFIG_FILE. Окружение приоритетнее файла. В коде секретов нет.
 */
public class Config {
    public final int httpPort;                  // NOTIFICATION_PORT, порт этого сервиса (8081)
    public final String dbUrl, dbUser, dbPassword;   // DB_URL, DB_USER, DB_PASSWORD (те же, что в db.properties IoT Core)
    public final String telegramToken;          // TELEGRAM_BOT_TOKEN
    public final String telegramApiUrl;         // TELEGRAM_API_URL, необязательно (зеркало API)
    public final String telegramProxy;          // TELEGRAM_PROXY=host:port, необязательно
    public final double defaultNorm;            // NORM: норма для устройств, у которых нет строки в таблице rute
    public final double defaultDeviation;       // DEVIATION: отклонение для таких устройств
    public final int defaultBatteryLimit;       // BATTERY_LIMIT: порог заряда (%) для таких устройств
    public final String deviceUrlTemplate;      // DEVICE_URL_TEMPLATE, например https://host/devices/{deviceId}
    public final String platformName;           // PLATFORM_NAME
    public final ZoneId zone;                   // NOTIFICATION_ZONE, часовой пояс времени в письмах и в /avg
    public final String smtpHost, smtpUser, smtpPassword, smtpFrom, smtpFromName;
    public final int smtpPort, smtpTimeoutMs;
    public final String smtpSecurity;           // STARTTLS, SSL или NONE

    private Config(Map<String, String> v) {
        httpPort = integer(v, "NOTIFICATION_PORT", 8081);
        dbUrl = text(v, "DB_URL");
        dbUser = text(v, "DB_USER");
        dbPassword = text(v, "DB_PASSWORD");
        telegramToken = text(v, "TELEGRAM_BOT_TOKEN");
        telegramApiUrl = text(v, "TELEGRAM_API_URL");
        telegramProxy = text(v, "TELEGRAM_PROXY");
        defaultNorm = number(v, "NORM", 25);
        defaultDeviation = number(v, "DEVIATION", 5);
        int battery = integer(v, "BATTERY_LIMIT", 15);
        if (battery < 0 || battery > 100) {
            throw new IllegalArgumentException("BATTERY_LIMIT: ожидается число от 0 до 100, получено " + battery);
        }
        defaultBatteryLimit = battery;
        deviceUrlTemplate = text(v, "DEVICE_URL_TEMPLATE");
        String platform = text(v, "PLATFORM_NAME");
        platformName = platform != null ? platform : "IoT Platform";
        zone = zone(text(v, "NOTIFICATION_ZONE"));

        smtpHost = text(v, "SMTP_HOST");
        smtpPort = integer(v, "SMTP_PORT", 587);
        smtpUser = text(v, "SMTP_USER");
        smtpPassword = text(v, "SMTP_PASSWORD");
        String from = text(v, "SMTP_FROM");
        if (from == null && smtpUser != null && smtpUser.contains("@")) from = smtpUser;
        smtpFrom = from;
        String fromName = text(v, "SMTP_FROM_NAME");
        smtpFromName = fromName != null ? fromName : "IoT Platform";
        smtpTimeoutMs = integer(v, "SMTP_TIMEOUT_MS", 10_000);
        String sec = text(v, "SMTP_SECURITY");
        sec = sec == null ? "STARTTLS" : sec.toUpperCase(Locale.ROOT);
        if (!sec.equals("STARTTLS") && !sec.equals("SSL") && !sec.equals("NONE")) {
            throw new IllegalArgumentException("SMTP_SECURITY: допустимо STARTTLS, SSL или NONE, получено «" + sec + "»");
        }
        smtpSecurity = sec;
    }

    /** Читает файл (если указан NOTIFICATION_CONFIG_FILE) и переменные окружения. */
    public static Config fromEnvironment() {
        Map<String, String> values = new HashMap<>();
        String file = text(System.getenv(), "NOTIFICATION_CONFIG_FILE");
        if (file != null) {
            Properties props = new Properties();
            try (Reader in = Files.newBufferedReader(Path.of(file), StandardCharsets.UTF_8)) {
                props.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Не удалось прочитать NOTIFICATION_CONFIG_FILE=" + file, e);
            }
            props.forEach((k, val) -> values.put(String.valueOf(k), String.valueOf(val)));
        }
        values.putAll(System.getenv());   // окружение приоритетнее файла
        return new Config(values);
    }

    /** Настройки из готового словаря (нужно для тестов, окружение не читается). */
    public static Config fromMap(Map<String, String> values) {
        return new Config(values);
    }

    /** Ссылка на устройство, если шаблон задан и это http(s)-адрес. */
    public Optional<String> deviceUrl(UUID deviceId) {
        if (deviceUrlTemplate == null || deviceId == null) return Optional.empty();
        String url = deviceUrlTemplate.replace("{deviceId}", deviceId.toString());
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") ? Optional.of(url) : Optional.empty();
    }

    /** Список проблем SMTP-настроек; пустой список означает, что письма можно отправлять. */
    public List<String> smtpProblems() {
        List<String> p = new ArrayList<>();
        if (smtpHost == null) p.add("не задан SMTP_HOST");
        if (smtpFrom == null || !smtpFrom.contains("@")) p.add("не задан корректный SMTP_FROM (или SMTP_USER в виде адреса)");
        if (smtpPort < 1 || smtpPort > 65535) p.add("SMTP_PORT должен быть от 1 до 65535");
        if (smtpUser != null && smtpPassword == null) p.add("задан SMTP_USER, но не задан SMTP_PASSWORD");
        if (smtpPassword != null && smtpUser == null) p.add("задан SMTP_PASSWORD, но не задан SMTP_USER");
        if (smtpSecurity.equals("NONE") && smtpUser != null) p.add("SMTP_SECURITY=NONE с паролем: пароль уйдёт открытым текстом");
        if (smtpTimeoutMs <= 0) p.add("SMTP_TIMEOUT_MS должен быть больше 0");
        return p;
    }

    public boolean smtpUsable() {
        return smtpHost != null && smtpProblems().isEmpty();
    }

    private static String text(Map<String, String> v, String key) {
        String s = v.get(key);
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static int integer(Map<String, String> v, String key, int def) {
        String raw = text(v, key);
        if (raw == null) return def;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + ": ожидается целое число, получено «" + raw + "»");
        }
    }

    private static double number(Map<String, String> v, String key, double def) {
        String raw = text(v, key);
        if (raw == null) return def;
        try {
            double d = Double.parseDouble(raw.replace(',', '.'));
            if (!Double.isFinite(d)) throw new NumberFormatException();
            return d;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + ": ожидается число, получено «" + raw + "»");
        }
    }

    private static ZoneId zone(String raw) {
        if (raw == null) return ZoneId.of("UTC");
        try {
            return ZoneId.of(raw);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("NOTIFICATION_ZONE: неизвестный часовой пояс «" + raw + "»");
        }
    }
}