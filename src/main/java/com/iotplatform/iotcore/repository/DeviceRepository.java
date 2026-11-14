package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.DeviceEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository {
    Optional<DeviceEntity> findById(UUID id);   // ищет устройство в бае по id
    boolean existsById(UUID id); // существует ли устройство с таким id?
    DeviceEntity save(DeviceEntity device);     // создаёт, а если id уже есть, обновляет
    boolean deleteById(UUID id);                // true, если что-то удалили
    // обновляет только время последних данных и только если новое время позже текущего
    int updateLastDataTime(UUID id, Instant lastDataTime);

    // флаг battery_notify_allowed: можно ли отправить уведомление о низком заряде
    /** Ставит false, если было true. Вернёт true, только если флаг поменялся (значит, уведомление надо отправить). */
    boolean disableBatteryNotification(UUID id);
    /** Ставит true, если было false. Вернёт true, если флаг поменялся. */
    boolean enableBatteryNotification(UUID id);

    /** Все устройства пользователя (пустой список, если их нет). */
    List<DeviceEntity> findByUserId(UUID userId);
}