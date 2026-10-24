package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.service.DeviceService;
import com.iotplatform.iotcore.service.NotificationService;

import java.util.UUID;

public class DeviceController {
    private final DeviceService service;
    private final NotificationService notifications;

    public DeviceController(DeviceService service, NotificationService notifications) {
        this.service = service;
        this.notifications = notifications;
    }

    public DeviceDto getById(UUID id) {
        return service.findById(id);
    }

    public void register(DeviceDto dto) {
        service.register(dto);
        notifications.notifyDeviceRegistered(dto);
        notifications.checkBattery(dto);
    }

    public DeviceDto update(UUID id, DeviceDto dto) {
        DeviceDto updated = service.update(id, dto);
        notifications.checkBattery(updated);
        return updated;
    }

    public void delete(UUID id) {
        service.delete(id);
    }
}