package com.iotplatform.iotcore.check;

import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.model.RuleOperator;
import com.iotplatform.iotcore.repository.RuleRepository;

import java.util.UUID;

public class RuleEngineTelemetryChecker implements TelemetryNormChecker {
    private final RuleRepository rules;

    public RuleEngineTelemetryChecker(RuleRepository rules) {
        this.rules = rules;
    }

    @Override
    public TelemetryCheckResult check(UUID deviceId, short telemetryType, double value) {
        for (RuleEntity rule : rules.findByDeviceId(deviceId)) {
            if (!rule.telemetryType().equals(telemetryType)) continue;

            if (rule.operator().test(value, rule.value())) continue; // В норме

            // Если не в норме - формируем результат
            double deviation = value - rule.value();
            Double lower = null;
            Double upper = null;

            if (rule.operator() == RuleOperator.GE || rule.operator() == RuleOperator.GT) {
                lower = rule.value();
            } else if (rule.operator() == RuleOperator.LE || rule.operator() == RuleOperator.LT) {
                upper = rule.value();
            }
            return new TelemetryCheckResult(true, value, deviation, lower, upper);
        }
        return TelemetryCheckResult.normal();
    }
}