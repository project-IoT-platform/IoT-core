package com.iotplatform.notification.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * Настройки NotificationService.
 * Источники: переменные окружения и (необязательно) файл .properties, путь к которому в NOTIFICATION_CONFIG_FILE.
 * Переменные окружения приоритетнее файла.
 *
 * TELEGRAM_BOT_TOKEN, NORM, DEVIATION, DEVICE_URL_TEMPLATE (например https://host/devices/{deviceId}),
 * NOTIFICATION_ZONE (по умолчанию UTC), PLATFORM_NAME, SMTP_* (см. EmailConfig).
 */
public record NotificationConfig(String telegramToken, double defaultNorm, double defaultDeviation,
                                 String deviceUrlTemplate, ZoneId zone, String platformName,
                                 EmailConfig email) {

    public static Map<String, String> loadValues() {
        Map<String, String> values = new HashMap<>();
        String file = blankToNull(System.getenv("NOTIFICATION_CONFIG_FILE"));
        if (file != null) {
            Properties props = new Properties();
            try (Reader in = Files.newBufferedReader(Path.of(file), StandardCharsets.UTF_8)) {
                props.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Не удалось прочитать NOTIFICATION_CONFIG_FILE=" + file, e);
            }
            props.forEach((k, val) -> values.put(String.valueOf(k), String.valueOf(val)));
        }
        values.putAll(System.getenv());
        return values;
    }

    public static NotificationConfig fromEnvironment() {
        return fromMap(loadValues());
    }

    public static NotificationConfig fromMap(Map<String, String> v) {
        String platform = blankToNull(v.get("PLATFORM_NAME"));
        return new NotificationConfig(
                blankToNull(v.get("TELEGRAM_BOT_TOKEN")),
                number(v, "NORM", 25),
                number(v, "DEVIATION", 5),
                blankToNull(v.get("DEVICE_URL_TEMPLATE")),
                zone(v.get("NOTIFICATION_ZONE")),
                platform != null ? platform : "IoT Platform",
                EmailConfig.fromMap(v));
    }

    public boolean telegramConfigured() {
        return telegramToken != null;
    }

    /** Ссылка на устройство, если шаблон задан и это http(s)-адрес. */
    public Optional<String> deviceUrl(UUID deviceId) {
        if (deviceUrlTemplate == null || deviceId == null) return Optional.empty();
        String url = deviceUrlTemplate.replace("{deviceId}", deviceId.toString());
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") ? Optional.of(url) : Optional.empty();
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static double number(Map<String, String> v, String key, double def) {
        String raw = blankToNull(v.get(key));
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
        String s = blankToNull(raw);
        if (s == null) return ZoneId.of("UTC");
        try {
            return ZoneId.of(s);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("NOTIFICATION_ZONE: неизвестный часовой пояс «" + s + "»");
        }
    }

    @Override
    public String toString() {
        return "NotificationConfig[telegramToken=" + (telegramToken == null ? "не задан" : "***")
                + ", norm=" + defaultNorm + ", deviation=" + defaultDeviation
                + ", deviceUrlTemplate=" + deviceUrlTemplate + ", zone=" + zone + ", email=" + email + "]";
    }
}