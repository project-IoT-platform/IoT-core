package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.RuleOperator;
import com.iotplatform.iotcore.testsupport.InMemoryRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {
    private static final short HUMIDITY = 2;
    private static final short TEMPERATURE = 3;

    private final UUID deviceId = UUID.randomUUID();
    private InMemoryRuleRepository rules;
    private RuleEngine engine;

    @BeforeEach
    void setUp() {
        rules = new InMemoryRuleRepository();
        engine = new RuleEngine(rules);
    }

    // Создаёт правило и привязывает его к устройству; возвращает правило.
    private RuleEntity addRule(short type, RuleOperator op, double limit, boolean strict) {
        RuleEntity rule = rules.save(new RuleEntity(UUID.randomUUID(), type, op, limit, strict));
        rules.attachToDevice(rule.id(), deviceId);
        return rule;
    }

    private TelemetryDto telemetry(short type, double value) {
        return new TelemetryDto(deviceId, Instant.now(), type, value);
    }

    @Test
    void noRulesGivesEmptyMap() {
        assertTrue(engine.check(telemetry(HUMIDITY, 12345)).isEmpty());
    }

    @Test
    void strictViolationThrowsValidationException() {
        RuleEntity rule = addRule(HUMIDITY, RuleOperator.LE, 100, true);

        ValidationException e = assertThrows(ValidationException.class,
                () -> engine.check(telemetry(HUMIDITY, 120)));

        assertEquals(1, e.getErrors().size());
        assertTrue(e.getErrors().containsKey("rule_" + rule.id()));
    }

    @Test
    void strictErrorNamesTheLimit() {
        addRule(HUMIDITY, RuleOperator.LE, 100, true);

        ValidationException e = assertThrows(ValidationException.class,
                () -> engine.check(telemetry(HUMIDITY, 120)));

        String message = e.getErrors().values().iterator().next();
        assertTrue(message.contains("120.0"), message);
        assertTrue(message.contains("<= 100.0"), message);
    }

    @Test
    void softViolationReturnsMapAndDoesNotThrow() {
        RuleEntity rule = addRule(TEMPERATURE, RuleOperator.LE, 30, false);

        Map<String, String> outOfRange = engine.check(telemetry(TEMPERATURE, 35));

        assertEquals(1, outOfRange.size());
        assertTrue(outOfRange.containsKey("rule_" + rule.id()));
    }

    @Test
    void severalSoftViolationsAreAllReported() {
        addRule(TEMPERATURE, RuleOperator.LE, 30, false);
        addRule(TEMPERATURE, RuleOperator.LT, 20, false);

        assertEquals(2, engine.check(telemetry(TEMPERATURE, 35)).size());
    }

    @Test
    void whenStrictAndSoftAreViolatedOnlyStrictErrorIsThrown() {
        RuleEntity strict = addRule(TEMPERATURE, RuleOperator.LE, 100, true);
        RuleEntity soft = addRule(TEMPERATURE, RuleOperator.LE, 30, false);

        ValidationException e = assertThrows(ValidationException.class,
                () -> engine.check(telemetry(TEMPERATURE, 150)));

        assertEquals(1, e.getErrors().size());
        assertTrue(e.getErrors().containsKey("rule_" + strict.id()));
        assertFalse(e.getErrors().containsKey("rule_" + soft.id()));
    }

    @Test
    void softViolationIsReportedWhenStrictRuleIsSatisfied() {
        addRule(TEMPERATURE, RuleOperator.LE, 100, true);
        RuleEntity soft = addRule(TEMPERATURE, RuleOperator.LE, 30, false);

        Map<String, String> outOfRange = engine.check(telemetry(TEMPERATURE, 35));

        assertEquals(1, outOfRange.size());
        assertTrue(outOfRange.containsKey("rule_" + soft.id()));
    }

    @Test
    void ruleOfAnotherTelemetryTypeIsIgnored() {
        addRule(HUMIDITY, RuleOperator.LE, 100, true);

        assertTrue(engine.check(telemetry(TEMPERATURE, 500)).isEmpty());
    }

    @Test
    void onlyRulesOfTheMatchingTypeAreApplied() {
        addRule(HUMIDITY, RuleOperator.LE, 100, true);
        RuleEntity temp = addRule(TEMPERATURE, RuleOperator.LE, 30, true);

        ValidationException e = assertThrows(ValidationException.class,
                () -> engine.check(telemetry(TEMPERATURE, 35)));

        assertEquals(1, e.getErrors().size());
        assertTrue(e.getErrors().containsKey("rule_" + temp.id()));
    }

    @Test
    void valueExactlyOnTheBoundaryPasses() {
        addRule(HUMIDITY, RuleOperator.LE, 100, true);
        addRule(HUMIDITY, RuleOperator.GE, 0, true);

        assertTrue(engine.check(telemetry(HUMIDITY, 100)).isEmpty());
        assertTrue(engine.check(telemetry(HUMIDITY, 0)).isEmpty());
    }

    @Test
    void valueJustOutsideTheBoundaryFails() {
        addRule(HUMIDITY, RuleOperator.LE, 100, true);
        addRule(HUMIDITY, RuleOperator.GE, 0, true);

        assertThrows(ValidationException.class, () -> engine.check(telemetry(HUMIDITY, 100.01)));
        assertThrows(ValidationException.class, () -> engine.check(telemetry(HUMIDITY, -0.01)));
    }

    @Test
    void rulesOfAnotherDeviceAreNotApplied() {
        UUID other = UUID.randomUUID();
        RuleEntity rule = rules.save(new RuleEntity(UUID.randomUUID(), HUMIDITY, RuleOperator.LE, 100.0, true));
        rules.attachToDevice(rule.id(), other);

        assertTrue(engine.check(telemetry(HUMIDITY, 500)).isEmpty());
    }
}