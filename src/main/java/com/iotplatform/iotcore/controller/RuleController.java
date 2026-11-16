package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.RuleDto;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.service.RuleService;

import java.util.List;
import java.util.UUID;

public class RuleController {
    private final RuleService service;

    public RuleController(RuleService service) {
        this.service = service;
    }

    public RuleEntity create(RuleDto dto) { return service.create(dto); }
    public RuleEntity getById(UUID id) { return service.findById(id); }
    public void delete(UUID id) { service.delete(id); }
    public void attach(UUID deviceId, UUID ruleId) { service.attach(deviceId, ruleId); }
    public void detach(UUID deviceId, UUID ruleId) { service.detach(deviceId, ruleId); }
    public List<RuleEntity> getByDevice(UUID deviceId) { return service.findByDevice(deviceId); }
}