package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.service.NotificationService;
import com.iotplatform.iotcore.service.TelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class TelemetryController {
    private static final Logger log = LoggerFactory.getLogger(TelemetryController.class);

    private final TelemetryService service;
    private final NotificationService notifications;

    public TelemetryController(TelemetryService service, NotificationService notifications) {
        this.service = service;
        this.notifications = notifications;
    }

    public Map<String, String> receive(TelemetryDto dto) {
        Map<String, String> outOfRange;
        try {
            outOfRange = service.process(dto);
        } catch (ValidationException e) {
            log.warn("Telemetry rejected: device {}, type {}, value {} -> {}",
                    dto.deviceId(), dto.telemetryType(), dto.value(), e.getErrors());
            throw e;
        }

        if (!outOfRange.isEmpty()) {
            log.warn("Soft rule violation, data saved: device {}, type {}, value {} -> {}",
                    dto.deviceId(), dto.telemetryType(), dto.value(), outOfRange);
        }

        notifications.checkTelemetry(dto);
        return outOfRange;
    }
}