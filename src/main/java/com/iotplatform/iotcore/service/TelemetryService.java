package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.entity.TelemetryEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.TelemetryRepository;

import java.util.UUID;

public class TelemetryService {
    private final DeviceRepository devices;
    private final TelemetryRepository telemetry;
    private final RuleEngine ruleEngine;

    public TelemetryService(DeviceRepository devices, TelemetryRepository telemetry, RuleEngine ruleEngine) {
        this.devices = devices;
        this.telemetry = telemetry;
        this.ruleEngine = ruleEngine;
    }

    public void process(TelemetryDto dto) {
        dto.validate();
        DeviceEntity device = devices.findById(dto.deviceId())
                .orElseThrow(() -> new DeviceNotFoundException(dto.deviceId()));

        ruleEngine.check(dto);   // <-- новое: если правило нарушено, дальше не идём

        telemetry.save(new TelemetryEntity(UUID.randomUUID(), dto.deviceId(),
                dto.time(), dto.telemetryType(), dto.value()));

        devices.updateLastDataTime(dto.deviceId(), dto.time());
    }
}