package com.iotplatform.notification.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Событие уведомления.
 * telegramText — готовый текст Telegram (идентичен прежнему поведению), он же текстовая часть письма.
 * description и details — для HTML-письма (details выводятся строками «параметр — значение»).
 */
public record NotificationEvent(NotificationType type, UUID recipientUserId, UUID deviceId, String deviceName,
                                Instant time, String description, Map<String, String> details,
                                String telegramText) {
    public NotificationEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(telegramText, "telegramText");
        deviceName = deviceName == null ? "" : deviceName;
        description = description == null ? "" : description;
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }
}
