package com.iotplatform.notification.data;

import com.iotplatform.notification.model.Device;
import com.iotplatform.notification.model.User;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/** Какие данные платформы нужны сервису уведомлений (только чтение). */
public interface PlatformData {
    Optional<Device> findDevice(UUID id);

    Optional<User> findUser(UUID id);

    /** Среднее значение телеметрии устройства с момента since; пусто, если показаний нет. */
    OptionalDouble averageSince(UUID deviceId, Instant since);
}