package com.iotplatform.notification.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Одно уведомление для всех каналов.
 * telegramText: готовый текст Telegram (он же текстовая часть письма).
 * description и details: для HTML-письма (details выводятся строками «параметр: значение»).
 */
public record Notification(NotificationType type, UUID userId, UUID deviceId, String deviceName,
                           Instant time, String description, Map<String, String> details,
                           String telegramText) {
    public Notification {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(telegramText, "telegramText");
        deviceName = deviceName == null ? "" : deviceName;
        description = description == null ? "" : description;
        details = details == null ? Map.of() : new LinkedHashMap<>(details);
    }
}