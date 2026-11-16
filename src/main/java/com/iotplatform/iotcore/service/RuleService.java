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
    private static final System.Logger LOG = System.getLogger(RuleService.class.getName());

    private final RuleRepository rules;
    private final DeviceRepository devices;

    public RuleService(RuleRepository rules, DeviceRepository devices) {
        this.rules = rules;
        this.devices = devices;
    }

    public RuleEntity create(RuleDto dto) {
        dto.validate();
        boolean strict = dto.strict() == null || dto.strict();   // по умолчанию правило строгое
        RuleEntity rule = rules.save(new RuleEntity(UUID.randomUUID(),
                dto.telemetryType(), dto.operator(), dto.value(), strict));
        LOG.log(System.Logger.Level.INFO, "Правило " + rule.id() + " создано: тип " + rule.telemetryType()
                + ", " + rule.operator() + " " + rule.value() + ", strict=" + rule.strict());
        return rule;
    }

    public RuleEntity findById(UUID id) {
        return rules.findById(id).orElseThrow(() -> new RuleNotFoundException(id));
    }

    public void delete(UUID id) {
        if (!rules.deleteById(id)) throw new RuleNotFoundException(id);
        LOG.log(System.Logger.Level.INFO, "Правило " + id + " удалено");
    }

    public void attach(UUID deviceId, UUID ruleId) {
        if (!devices.existsById(deviceId)) throw new DeviceNotFoundException(deviceId);
        if (rules.findById(ruleId).isEmpty()) throw new RuleNotFoundException(ruleId);
        rules.attachToDevice(ruleId, deviceId);
        LOG.log(System.Logger.Level.INFO, "Правило " + ruleId + " привязано к устройству " + deviceId);
    }

    public void detach(UUID deviceId, UUID ruleId) {
        if (!rules.detachFromDevice(ruleId, deviceId)) throw new RuleNotFoundException(ruleId);
        LOG.log(System.Logger.Level.INFO, "Правило " + ruleId + " отвязано от устройства " + deviceId);
    }

    public List<RuleEntity> findByDevice(UUID deviceId) {
        if (!devices.existsById(deviceId)) throw new DeviceNotFoundException(deviceId);
        return rules.findByDeviceId(deviceId);
    }
}