package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.RuleDto;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.RuleNotFoundException;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.RuleRepository;

import java.util.List;
import java.util.UUID;

public class RuleService {
    private final RuleRepository rules;
    private final DeviceRepository devices;

    public RuleService(RuleRepository rules, DeviceRepository devices) {
        this.rules = rules;
        this.devices = devices;
    }

    public RuleEntity create(RuleDto dto) {
        dto.validate();
        return rules.save(new RuleEntity(UUID.randomUUID(),
                dto.telemetryType(), dto.operator(), dto.value()));
    }

    public RuleEntity findById(UUID id) {
        return rules.findById(id).orElseThrow(() -> new RuleNotFoundException(id));
    }

    public void delete(UUID id) {
        if (!rules.deleteById(id)) throw new RuleNotFoundException(id);
    }

    public void attach(UUID deviceId, UUID ruleId) {
        if (!devices.existsById(deviceId)) throw new DeviceNotFoundException(deviceId);
        if (rules.findById(ruleId).isEmpty()) throw new RuleNotFoundException(ruleId);
        rules.attachToDevice(ruleId, deviceId);
    }

    public void detach(UUID deviceId, UUID ruleId) {
        if (!rules.detachFromDevice(ruleId, deviceId)) throw new RuleNotFoundException(ruleId);
    }

    public List<RuleEntity> findByDevice(UUID deviceId) {
        if (!devices.existsById(deviceId)) throw new DeviceNotFoundException(deviceId);
        return rules.findByDeviceId(deviceId);
    }
}