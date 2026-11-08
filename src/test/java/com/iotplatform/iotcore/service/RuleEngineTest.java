package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.fake.FakeRuleRepository;
import com.iotplatform.iotcore.model.RuleOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {
    private FakeRuleRepository rules;
    private RuleEngine engine;
    private UUID device;

    @BeforeEach
    void setUp() {
        rules = new FakeRuleRepository();
        engine = new RuleEngine(rules);
        device = UUID.randomUUID();
    }

    private TelemetryDto t(int type, double value) {
        return new TelemetryDto(device, Instant.now(), (short) type, value);
    }

    @Test
    void noRulesNoViolations() {
        assertTrue(engine.check(t(1, 1e9)).isEmpty());
    }

    @Test
    void softViolationIsReturnedWithTeammateText() {
        RuleEntity r = rules.add(device, 3, RuleOperator.LE, 30.0, false);
        Map<String, String> out = engine.check(t(3, 35.0));
        assertEquals(Map.of("rule_" + r.id(), "значение 35.0 нарушает правило: должно быть ≤ 30.0"), out);
    }

    @Test
    void strictViolationThrowsWithOnlyStrictRules() {
        rules.add(device, 2, RuleOperator.LE, 50.0, false);
        RuleEntity strict = rules.add(device, 2, RuleOperator.LE, 100.0, true);

        ValidationException e = assertThrows(ValidationException.class, () -> engine.check(t(2, 120.0)));
        assertEquals(Map.of("rule_" + strict.id(), "значение 120.0 нарушает правило: должно быть ≤ 100.0"),
                e.getErrors());
    }

    @Test
    void satisfiedRulesAndOtherTypesAreIgnored() {
        rules.add(device, 2, RuleOperator.GE, 0.0, true);
        rules.add(device, 2, RuleOperator.LE, 100.0, true);
        rules.add(device, 5, RuleOperator.LE, 1.0, true);   // другой тип телеметрии
        assertTrue(engine.check(t(2, 100.0)).isEmpty());
    }

    @Test
    void severalSoftViolations() {
        rules.add(device, 1, RuleOperator.LE, 30.0, false);
        rules.add(device, 1, RuleOperator.LT, 20.0, false);
        assertEquals(2, engine.check(t(1, 40.0)).size());
    }
}