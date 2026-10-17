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

    public TelemetryService(DeviceRepository devices, TelemetryRepository telemetry) {
        this.devices = devices;
        this.telemetry = telemetry;
    }

    public void process(TelemetryDto dto) {
        dto.validate();
        DeviceEntity device = devices.findById(dto.deviceId())
                .orElseThrow(() -> new DeviceNotFoundException(dto.deviceId()));

        telemetry.save(new TelemetryEntity(UUID.randomUUID(), dto.deviceId(),
                dto.time(), dto.telemetryType(), dto.value()));

        device.setLastDataTime(dto.time());
        devices.save(device);
    }
}