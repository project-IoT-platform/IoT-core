package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.TelemetryEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.TelemetryRepository;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelemetryService {
    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final DeviceRepository devices;
    private final TelemetryRepository telemetry;
    private final RuleEngine ruleEngine;

    public TelemetryService(DeviceRepository devices, TelemetryRepository telemetry, RuleEngine ruleEngine) {
        this.devices = devices;
        this.telemetry = telemetry;
        this.ruleEngine = ruleEngine;
    }

    public Map<String, String> process(TelemetryDto dto) {
        dto.validate();
        if (!devices.existsById(dto.deviceId())) {
            throw new DeviceNotFoundException(dto.deviceId());
        }

        Map<String, String> outOfRange = ruleEngine.check(dto);

        telemetry.save(new TelemetryEntity(UUID.randomUUID(), dto.deviceId(),
                dto.time(), dto.telemetryType(), dto.value()));

        devices.updateLastDataTime(dto.deviceId(), dto.time());

        log.debug("Telemetry saved: device {}, type {}, value {}",
                dto.deviceId(), dto.telemetryType(), dto.value());
        return outOfRange;
    }
    static String describe(TelemetryDto dto) {
        return "устройство " + dto.deviceId() + ", тип " + dto.telemetryType() + ", значение " + dto.value();
    }
}