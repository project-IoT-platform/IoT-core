package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.service.AlertService;
import com.iotplatform.iotcore.service.DeviceService;
import com.iotplatform.iotcore.service.NotificationService;

import java.util.UUID;

public class DeviceController {
    private final DeviceService service;
    private final NotificationService notifications;
    private final AlertService alerts;

    public DeviceController(DeviceService service, NotificationService notifications, AlertService alerts) {
        this.service = service;
        this.notifications = notifications;
        this.alerts = alerts;
    }

    public DeviceDto getById(UUID id) {
        return service.findById(id);
    }

    public void register(DeviceDto dto) {
        service.register(dto);                          // проверка на валидность + сохранение; ошибка -> дальше не идём
        notifications.notifyDeviceRegistered(dto);      // письмо о регистрации устройства
        alerts.onBattery(dto.getDeviceId(), dto.getBatteryLevel());
    }

    public DeviceDto update(UUID id, DeviceDto dto) {
        DeviceDto updated = service.update(id, dto);
        alerts.onBattery(updated.getDeviceId(), updated.getBatteryLevel());
        return updated;
    }

    public void delete(UUID id) {
        service.delete(id);
    }
}