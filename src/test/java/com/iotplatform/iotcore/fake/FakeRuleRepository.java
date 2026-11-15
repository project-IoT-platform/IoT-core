package com.iotplatform.iotcore.fake;

import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.model.RuleOperator;
import com.iotplatform.iotcore.repository.RuleRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Правила в памяти: устройство -> список правил. */
public class FakeRuleRepository implements RuleRepository {
    public final Map<UUID, List<RuleEntity>> byDevice = new HashMap<>();
    private final Map<UUID, RuleEntity> all = new LinkedHashMap<>();

    public RuleEntity add(UUID deviceId, int telemetryType, RuleOperator op, double value, boolean strict) {
        RuleEntity r = new RuleEntity(UUID.randomUUID(), (short) telemetryType, op, value, strict);
        all.put(r.id(), r);
        byDevice.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(r);
        return r;
    }

    @Override
    public RuleEntity save(RuleEntity rule) {
        all.put(rule.id(), rule);
        return rule;
    }

    @Override
    public Optional<RuleEntity> findById(UUID id) {
        return Optional.ofNullable(all.get(id));
    }

    @Override
    public boolean deleteById(UUID id) {
        byDevice.values().forEach(list -> list.removeIf(r -> r.id().equals(id)));
        return all.remove(id) != null;
    }

    @Override
    public void attachToDevice(UUID ruleId, UUID deviceId) {
        RuleEntity rule = all.get(ruleId);
        if (rule == null) return;
        List<RuleEntity> list = byDevice.computeIfAbsent(deviceId, k -> new ArrayList<>());
        if (list.stream().noneMatch(r -> r.id().equals(ruleId))) list.add(rule);
    }

    @Override
    public boolean detachFromDevice(UUID ruleId, UUID deviceId) {
        List<RuleEntity> list = byDevice.get(deviceId);
        return list != null && list.removeIf(r -> r.id().equals(ruleId));
    }

    @Override
    public List<RuleEntity> findByDeviceId(UUID deviceId) {
        return byDevice.getOrDefault(deviceId, List.of());
    }
}