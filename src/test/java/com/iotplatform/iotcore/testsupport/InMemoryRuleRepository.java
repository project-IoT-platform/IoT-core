package com.iotplatform.iotcore.testsupport;

import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.repository.RuleRepository;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Фейк вместо БД: правила и их привязки к устройствам в памяти. */
public class InMemoryRuleRepository implements RuleRepository {
    private final Map<UUID, RuleEntity> rules = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> links = new HashMap<>();   // deviceId -> id правил

    public boolean isAttached(UUID ruleId, UUID deviceId) {
        return links.getOrDefault(deviceId, Set.of()).contains(ruleId);
    }

    @Override
    public RuleEntity save(RuleEntity rule) {
        rules.put(rule.id(), rule);
        return rule;
    }

    @Override
    public Optional<RuleEntity> findById(UUID id) {
        return Optional.ofNullable(rules.get(id));
    }

    @Override
    public boolean deleteById(UUID id) {
        links.values().forEach(set -> set.remove(id));
        return rules.remove(id) != null;
    }

    @Override
    public void attachToDevice(UUID ruleId, UUID deviceId) {
        links.computeIfAbsent(deviceId, k -> new LinkedHashSet<>()).add(ruleId);
    }

    @Override
    public boolean detachFromDevice(UUID ruleId, UUID deviceId) {
        Set<UUID> set = links.get(deviceId);
        return set != null && set.remove(ruleId);
    }

    @Override
    public List<RuleEntity> findByDeviceId(UUID deviceId) {
        return links.getOrDefault(deviceId, Set.of()).stream()
                .map(rules::get)
                .filter(Objects::nonNull)
                .toList();
    }
}