package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.DeviceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository {
    Optional<DeviceEntity> findById(UUID id);   // ищет устройство в бае по id
    boolean existsById(UUID id); // существует ли устройство с таким id?
    DeviceEntity save(DeviceEntity device);     // создаёт, а если id уже есть, обновляет
    boolean deleteById(UUID id);                // true, если что-то удалили
}