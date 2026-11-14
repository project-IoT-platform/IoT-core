package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.RuleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuleRepository {
    RuleEntity save(RuleEntity rule);                 // создаёт правило
    Optional<RuleEntity> findById(UUID id);           // ищет правило по id
    boolean deleteById(UUID id);                      // true, если что-то удалили
    void attachToDevice(UUID ruleId, UUID deviceId);  // привязывает правило к устройству
    boolean detachFromDevice(UUID ruleId, UUID deviceId); // отвязывает, true если связь была
    List<RuleEntity> findByDeviceId(UUID deviceId);   // все правила устройства
}
