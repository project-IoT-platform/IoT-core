package com.iotplatform.iotcore.fake;

import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.model.RuleOperator;
import com.iotplatform.iotcore.repository.RuleRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Правила в памяти: устройство -> список правил. */
public class FakeRuleRepository implements RuleRepository {
    public final Map<UUID, List<RuleEntity>> byDevice = new HashMap<>();

    public RuleEntity add(UUID deviceId, int telemetryType, RuleOperator op, double value, boolean strict) {
        RuleEntity r = new RuleEntity(UUID.randomUUID(), (short) telemetryType, op, value, strict);
        byDevice.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(r);
        return r;
    }

    @Override
    public List<RuleEntity> findByDeviceId(UUID deviceId) {
        return byDevice.getOrDefault(deviceId, List.of());
    }
}