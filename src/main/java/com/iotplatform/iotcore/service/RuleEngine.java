package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.repository.RuleRepository;

import java.util.LinkedHashMap;
import java.util.Map;

public class RuleEngine {
    private final RuleRepository rules;

    public RuleEngine(RuleRepository rules) {
        this.rules = rules;
    }

    public void check(TelemetryDto t) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (RuleEntity rule : rules.findByDeviceId(t.deviceId())) {
            if (!rule.telemetryType().equals(t.telemetryType())) continue;

            if (!rule.operator().test(t.value(), rule.value())) {
                errors.put("rule_" + rule.id(),
                        "значение " + t.value() + " нарушает правило: должно быть "
                                + rule.operator().getSymbol() + " " + rule.value());
            }
        }

        if (!errors.isEmpty()) throw new ValidationException(errors);
    }
}
