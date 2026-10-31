package com.iotplatform.notification.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Настройки SMTP. Секреты берутся только из окружения/внешнего файла, в коде их нет. */
public record EmailConfig(String host, int port, String username, String password,
                          String fromAddress, String fromName, Security security, int timeoutMillis) {

    public enum Security { STARTTLS, SSL, NONE }

    public static EmailConfig fromMap(Map<String, String> v) {
        String user = NotificationConfig.blankToNull(v.get("SMTP_USER"));
        String from = NotificationConfig.blankToNull(v.get("SMTP_FROM"));
        if (from == null && user != null && user.contains("@")) from = user;
        String name = NotificationConfig.blankToNull(v.get("SMTP_FROM_NAME"));
        return new EmailConfig(
                NotificationConfig.blankToNull(v.get("SMTP_HOST")),
                integer(v, "SMTP_PORT", 587),
                user,
                NotificationConfig.blankToNull(v.get("SMTP_PASSWORD")),
                from,
                name != null ? name : "IoT Platform",
                security(v.get("SMTP_SECURITY")),
                integer(v, "SMTP_TIMEOUT_MS", 10_000));
    }

    /** Задан ли хотя бы адрес сервера (иначе email-канал просто выключен). */
    public boolean isConfigured() {
        return host != null;
    }

    /** Список проблем настройки; пустой список = можно отправлять. */
    public List<String> problems() {
        List<String> p = new ArrayList<>();
        if (host == null) p.add("не задан SMTP_HOST");
        if (fromAddress == null || !fromAddress.contains("@")) {
            p.add("не задан корректный SMTP_FROM (или SMTP_USER в виде адреса)");
        }
        if (port < 1 || port > 65535) p.add("SMTP_PORT должен быть от 1 до 65535");
        if (username != null && password == null) p.add("задан SMTP_USER, но не задан SMTP_PASSWORD");
        if (password != null && username == null) p.add("задан SMTP_PASSWORD, но не задан SMTP_USER");
        if (security == Security.NONE && username != null) {
            p.add("SMTP_SECURITY=NONE с логином и паролем: пароль уйдёт открытым текстом");
        }
        if (timeoutMillis <= 0) p.add("SMTP_TIMEOUT_MS должен быть больше 0");
        return p;
    }

    public boolean isUsable() {
        return isConfigured() && problems().isEmpty();
    }

    private static int integer(Map<String, String> v, String key, int def) {
        String raw = NotificationConfig.blankToNull(v.get(key));
        if (raw == null) return def;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + ": ожидается целое число, получено «" + raw + "»");
        }
    }

    private static Security security(String raw) {
        String s = NotificationConfig.blankToNull(raw);
        if (s == null) return Security.STARTTLS;
        try {
            return Security.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("SMTP_SECURITY: допустимо STARTTLS, SSL или NONE, получено «" + s + "»");
        }
    }

    @Override
    public String toString() {
        return "EmailConfig[host=" + host + ", port=" + port + ", username=" + username
                + ", password=" + (password == null ? "не задан" : "***") + ", from=" + fromAddress
                + ", security=" + security + "]";
    }
}