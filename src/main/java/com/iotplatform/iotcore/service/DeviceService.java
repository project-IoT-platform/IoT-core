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

public class DeviceService {
    private final DeviceRepository devices;
    private final UserRepository users;

    public DeviceService(DeviceRepository devices, UserRepository users) {
        this.devices = devices;
        this.users = users;
    }

    public DeviceDto findById(UUID id) {
        return devices.findById(id).map(this::toDto)
                .orElseThrow(() -> new DeviceNotFoundException(id));
    }

    public void register(DeviceDto dto) {
        dto.validate();
        checkUserExists(dto.getUserId());
        Instant lastDataTime = devices.findById(dto.getDeviceId())
                .map(DeviceEntity::getLastDataTime).orElse(null);
        devices.save(toEntity(dto, lastDataTime));
    }

    public DeviceDto update(UUID id, DeviceDto dto) {
        DeviceDto withId = dto.withDeviceId(id);
        withId.validate();
        checkUserExists(withId.getUserId());
        DeviceEntity existing = devices.findById(id)
                .orElseThrow(() -> new DeviceNotFoundException(id));
        return toDto(devices.save(toEntity(withId, existing.getLastDataTime())));
    }

    public void delete(UUID id) {
        if (!devices.deleteById(id)) {
            throw new DeviceNotFoundException(id);
        }
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