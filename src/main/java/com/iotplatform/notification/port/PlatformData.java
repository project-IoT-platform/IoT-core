package com.iotplatform.notification.port;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/** Данные платформы, которые нужны NotificationService. Реализуют IoT Core или тиммейты. */
public interface PlatformData {

    Optional<DeviceInfo> findDevice(UUID deviceId);

    Optional<UserInfo> findUser(UUID userId);

    /** Среднее значение телеметрии устройства с момента since; пусто, если показаний нет. */
    OptionalDouble averageValueSince(UUID deviceId, Instant since);

    enum DeviceStatus { ON, OFF }

    record DeviceInfo(UUID id, UUID ownerUserId, String name, String manufacturer, Integer batteryLevel,
                      DeviceStatus status, String geolocation, Instant lastDataTime) {}

    record UserInfo(UUID id, String username, String email, boolean admin) {
        public String displayName() {
            return username != null ? username : email;
        }
    }

    record TelemetryReading(UUID deviceId, Instant time, Short telemetryType, Double value) {}
}