package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.RuleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuleRepository {
    RuleEntity save(RuleEntity rule);
    Optional<RuleEntity> findById(UUID id);
    boolean deleteById(UUID id);
    void attachToDevice(UUID ruleId, UUID deviceId);
    boolean detachFromDevice(UUID ruleId, UUID deviceId);
    List<RuleEntity> findByDeviceId(UUID deviceId);
}