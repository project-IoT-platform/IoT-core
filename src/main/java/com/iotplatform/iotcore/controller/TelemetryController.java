package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.service.NotificationService;
import com.iotplatform.iotcore.service.TelemetryService;

public class TelemetryController {
    private final TelemetryService service;
    private final NotificationService notifications;

    public TelemetryController(TelemetryService service, NotificationService notifications) {
        this.service = service;
        this.notifications = notifications;
    }

    public void receive(TelemetryDto dto) {
        service.process(dto);
        notifications.checkTelemetry(dto);
    }
}