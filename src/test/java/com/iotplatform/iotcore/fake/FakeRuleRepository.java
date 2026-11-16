package com.iotplatform.iotcore.fake;

import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.model.RuleOperator;
import com.iotplatform.iotcore.repository.RuleRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Правила в памяти: хранит сами правила и связь устройство -> правила. */
public class FakeRuleRepository implements RuleRepository {

    // Хранилище всех правил по их ID
    public final Map<UUID, RuleEntity> allRules = new HashMap<>();
    // Связь: ID устройства -> список ID правил
    public final Map<UUID, List<UUID>> deviceToRules = new HashMap<>();

    // Вспомогательный метод для тестов
    public RuleEntity add(UUID deviceId, int telemetryType, RuleOperator op, double value, boolean strict) {
        RuleEntity r = new RuleEntity(UUID.randomUUID(), (short) telemetryType, op, value, strict);
        save(r);
        attachToDevice(r.id(), deviceId);
        return r;
    }

    @Override
    public RuleEntity save(RuleEntity rule) {
        allRules.put(rule.id(), rule);
        return rule;
    }

    @Override
    public Optional<RuleEntity> findById(UUID id) {
        return Optional.ofNullable(allRules.get(id));
    }

    @Override
    public boolean deleteById(UUID id) {
        RuleEntity removed = allRules.remove(id);
        if (removed != null) {
            // Удаляем связь с устройством, если она была
            for (List<UUID> rules : deviceToRules.values()) {
                rules.remove(id);
            }
            return true;
        }
        return false;
    }

    @Override
    public void attachToDevice(UUID ruleId, UUID deviceId) {
        deviceToRules.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(ruleId);
    }

    @Override
    public boolean detachFromDevice(UUID ruleId, UUID deviceId) {
        List<UUID> rules = deviceToRules.get(deviceId);
        if (rules != null) {
            return rules.remove(ruleId);
        }
        return false;
    }

    @Override
    public List<RuleEntity> findByDeviceId(UUID deviceId) {
        List<UUID> ruleIds = deviceToRules.get(deviceId);
        if (ruleIds == null) return List.of();

        return ruleIds.stream()
                .map(allRules::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}