package com.iotplatform.notification.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Фабрика уведомлений: из данных устройства и результатов проверок собирает Notification. */
public final class Notifications {
    private Notifications() {}

    public static Notification deviceRegistered(Device d, Instant now) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("ID устройства", String.valueOf(d.id()));
        if (d.manufacturer() != null) details.put("Производитель", d.manufacturer());
        if (d.batteryLevel() != null) details.put("Заряд батареи", d.batteryLevel() + "%");
        if (d.geolocation() != null) details.put("Геолокация", d.geolocation());
        return new Notification(NotificationType.DEVICE_REGISTERED, d.userId(), d.id(), d.name(), now,
                "Устройство зарегистрировано на платформе.", details,
                "📟 Зарегистрировано устройство\n"
                        + "Название: " + d.name() + "\n"
                        + "ID: " + d.id());
    }

    /** Письмо новому пользователю с его id (id нужен, чтобы привязать Telegram-бота). */
    public static Notification userRegistered(UUID userId, String username, Instant now) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("ID пользователя", String.valueOf(userId));
        if (username != null && !username.isBlank()) details.put("Имя", username);
        return new Notification(NotificationType.USER_REGISTERED, userId, null, null, now,
                "Ваш аккаунт создан. Чтобы получать уведомления в Telegram, отправьте боту ваш ID пользователя.",
                details,
                "👤 Вы зарегистрированы на платформе\nВаш ID пользователя: " + userId);
    }

    /** Заряд ниже нижней границы: level, на сколько ниже (belowBy) и сама граница. */
    public static Notification lowBattery(Device d, int level, Integer belowBy, Integer lowerBound, Instant now) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Заряд", level + "%");
        if (lowerBound != null) details.put("Нижняя допустимая граница", lowerBound + "%");
        if (belowBy != null) details.put("Ниже границы на", belowBy + "%");
        String text = "🪫 Батарея разряжается\n"
                + "Устройство: " + d.name() + "\n"
                + "Заряд: " + level + "%"
                + (lowerBound != null ? " (нижняя граница " + lowerBound + "%"
                + (belowBy != null ? ", ниже на " + belowBy + "%" : "") + ")" : "");
        return new Notification(NotificationType.LOW_BATTERY, d.userId(), d.id(), d.name(), now,
                "Батарея устройства разряжается: заряд ниже допустимой границы.", details, text);
    }

    /**
     * Показание вне нормы. deviation < 0: меньше нормы на |deviation|; deviation > 0: больше нормы на deviation.
     * lowerBound и upperBound могут быть null (у нормы может быть только одна граница).
     */
    public static Notification telemetryAlert(Device d, Reading r, Double deviation,
                                              Double lowerBound, Double upperBound, Instant now) {
        String deviationText = deviationText(deviation);
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Показание", format(r.value()));
        if (lowerBound != null) details.put("Нижняя граница нормы", format(lowerBound));
        if (upperBound != null) details.put("Верхняя граница нормы", format(upperBound));
        details.put("Отклонение", deviationText);
        return new Notification(NotificationType.TELEMETRY_ALERT, d.userId(), d.id(), d.name(),
                r.time() != null ? r.time() : now,
                deviationText + ".", details,
                "⚠️ Устройство " + d.name() + ": показание " + format(r.value()) + "\n" + deviationText);
    }

    /** «Телеметрия меньше нормы на 3» / «Телеметрия больше нормы на 5». */
    public static String deviationText(Double deviation) {
        if (deviation == null || deviation == 0) return "Телеметрия вне нормы";
        if (deviation < 0) return "Телеметрия меньше нормы на " + format(Math.abs(deviation));
        return "Телеметрия больше нормы на " + format(deviation);
    }

    /** Число без лишних нулей: 5.0 -> «5», 2.50 -> «2.5», 1.23456 -> «1.23». */
    public static String format(Double v) {
        if (v == null) return "—";
        if (!Double.isFinite(v)) return String.valueOf(v);
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public static Notification deviceStatus(Device d, Instant now) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Состояние", stateText(d));
        if (d.batteryLevel() != null) details.put("Заряд батареи", d.batteryLevel() + "%");
        return new Notification(NotificationType.DEVICE_STATUS, d.userId(), d.id(), d.name(), now,
                "Текущее состояние устройства: " + stateText(d) + ".", details, statusLine(d));
    }

    /** Строка статуса для бота и уведомления. */
    public static String statusLine(Device d) {
        return "Устройство " + d.name() + ": " + stateText(d);
    }

    private static String stateText(Device d) {
        return d.on() ? "ВКЛ" : "ВЫКЛ";
    }
}