package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.UserRepository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Бизнес-логика устройств: поиск, регистрация, обновление и удаление.
 */
public class DeviceService {
    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository devices;
    private final UserRepository users;

    /**
     * Создаёт сервис.
     *
     * @param devices хранилище устройств
     * @param users хранилище пользователей (проверка владельца)
     */
    public DeviceService(DeviceRepository devices, UserRepository users) {
        this.devices = devices;
        this.users = users;
    }

    /**
     * Ищет устройство по id.
     *
     * @param id идентификатор устройства
     * @return найденное устройство
     * @throws DeviceNotFoundException если устройства нет
     */
    public DeviceDto findById(UUID id) {
        return devices.findById(id).map(this::toDto)
                .orElseThrow(() -> new DeviceNotFoundException(id));
    }

    /**
     * Регистрирует устройство. Если устройство с таким id уже есть, оно обновляется, а время последних данных сохраняется.
     *
     * @param dto данные устройства
     * @throws ValidationException если данные некорректны или указанного владельца нет
     */
    public void register(DeviceDto dto) {
        dto.validate();
        checkUserExists(dto.getUserId());
        Instant lastDataTime = devices.findById(dto.getDeviceId())
                .map(DeviceEntity::getLastDataTime).orElse(null);
        devices.save(toEntity(dto, lastDataTime));
        log.info("Device {} saved (user {})", dto.getDeviceId(), dto.getUserId());
    }

    /**
     * Обновляет существующее устройство. Идентификатор берётся из аргумента {@code id}, а не из тела.
     * Время последних данных не меняется.
     *
     * @param id идентификатор устройства
     * @param dto новые данные
     * @return сохранённое устройство
     * @throws DeviceNotFoundException если устройства нет
     * @throws ValidationException если данные некорректны или указанного владельца нет
     */
    public DeviceDto update(UUID id, DeviceDto dto) {
        DeviceDto withId = dto.withDeviceId(id);
        withId.validate();
        checkUserExists(withId.getUserId());
        DeviceEntity existing = devices.findById(id)
                .orElseThrow(() -> new DeviceNotFoundException(id));
        log.info("Device {} updated", id);
        return toDto(devices.save(toEntity(withId, existing.getLastDataTime())));
    }

    /**
     * Удаляет устройство.
     *
     * @param id идентификатор устройства
     * @throws DeviceNotFoundException если устройства нет
     */
    public void delete(UUID id) {
        if (!devices.deleteById(id)) {
            throw new DeviceNotFoundException(id);
        }
        log.info("Device {} deleted", id);
    }

    private void checkUserExists(UUID userId) {
        if (userId != null && !users.existsById(userId)) {
            throw new ValidationException(Map.of("userId", "пользователь не найден"));
        }
    }

    private DeviceEntity toEntity(DeviceDto d, Instant lastDataTime) {
        return new DeviceEntity(d.getDeviceId(), d.getUserId(), d.getName(), lastDataTime,
                d.getBatteryLevel(), d.getManufacturer(), d.getState(), d.getGeolocation());
    }

    private DeviceDto toDto(DeviceEntity e) {
        return new DeviceDto(e.getId(), e.getUserId(), e.getName(), e.getManufacturer(),
                e.getBatteryLevel(), e.getState(), e.getGeolocation(), e.getLastDataTime());
    }
}