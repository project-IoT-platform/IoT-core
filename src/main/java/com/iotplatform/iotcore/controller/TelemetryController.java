package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.service.TelemetryService;

public class TelemetryController {
    private final TelemetryService service;

    public TelemetryController(TelemetryService service) {
        this.service = service;
    }

    public void receive(TelemetryDto dto) {
        service.process(dto);
    }
}