package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.service.NotificationService;
import com.iotplatform.iotcore.service.NotificationService.Settings;

import java.util.Map;
import java.util.UUID;

public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    public Map<String, Object> sendDeviceStatus(UUID deviceId) {
        return Map.of("deviceId", deviceId, "state", service.sendDeviceStatus(deviceId));
    }

    public Settings getSettings() {
        return service.getDefaults();
    }

    public Settings updateSettings(Settings settings) {
        return service.updateDefaults(settings);
    }
}