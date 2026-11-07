package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.entity.TelemetryEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.fake.FakeDeviceRepository;
import com.iotplatform.iotcore.fake.FakeRuleRepository;
import com.iotplatform.iotcore.fake.FakeTelemetryRepository;
import com.iotplatform.iotcore.model.DeviceState;
import com.iotplatform.iotcore.model.RuleOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryServiceTest {
    private FakeDeviceRepository devices;
    private FakeTelemetryRepository telemetry;
    private FakeRuleRepository rules;
    private TelemetryService service;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        devices = new FakeDeviceRepository();
        telemetry = new FakeTelemetryRepository();
        rules = new FakeRuleRepository();
        service = new TelemetryService(devices, telemetry, new RuleEngine(rules));
        deviceId = UUID.randomUUID();
        devices.save(new DeviceEntity(deviceId, null, "n", null, 50, "m", DeviceState.ON, null));
        devices.saveCalls = 0;
    }

    private TelemetryDto reading(int type, double value) {
        return new TelemetryDto(deviceId, Instant.parse("2026-11-06T12:00:00Z"), (short) type, value);
    }

    // ---------- без правил ----------

    @Test
    void processSavesTelemetryAndUpdatesLastDataTime() {
        Instant t = Instant.parse("2026-11-06T12:00:00Z");
        service.process(new TelemetryDto(deviceId, t, (short) 1, 21.5));

        assertEquals(1, telemetry.rows.size());
        TelemetryEntity saved = telemetry.rows.get(0);
        assertNotNull(saved.id());
        assertEquals(deviceId, saved.deviceId());
        assertEquals(t, saved.time());
        assertEquals((short) 1, saved.telemetryType());
        assertEquals(21.5, saved.value());
        assertEquals(t, devices.rows.get(deviceId).getLastDataTime());
    }

    @Test
    void processDoesNotRewriteWholeDevice() {
        service.process(reading(1, 1.0));
        assertEquals(0, devices.saveCalls);
    }

    @Test
    void olderTelemetryDoesNotMoveLastDataTimeBack() {
        Instant newer = Instant.parse("2026-11-06T12:00:00Z");
        Instant older = Instant.parse("2026-11-06T11:00:00Z");
        service.process(new TelemetryDto(deviceId, newer, (short) 1, 1.0));
        service.process(new TelemetryDto(deviceId, older, (short) 1, 2.0));

        assertEquals(2, telemetry.rows.size());
        assertEquals(newer, devices.rows.get(deviceId).getLastDataTime());
    }

    @Test
    void unknownDeviceThrowsAndSavesNothing() {
        UUID unknown = UUID.randomUUID();
        assertThrows(DeviceNotFoundException.class,
                () -> service.process(new TelemetryDto(unknown, Instant.now(), (short) 1, 1.0)));
        assertTrue(telemetry.rows.isEmpty());
    }

    @Test
    void invalidTelemetryThrowsAndSavesNothing() {
        assertThrows(ValidationException.class,
                () -> service.process(new TelemetryDto(deviceId, null, null, null)));
        assertTrue(telemetry.rows.isEmpty());
        assertNull(devices.rows.get(deviceId).getLastDataTime());
    }

    // ---------- правила is_strict ----------

    @Test
    void strictViolationIsRejectedAndNotSaved() {
        RuleEntity rule = rules.add(deviceId, 2, RuleOperator.LE, 100.0, true);

        ValidationException e = assertThrows(ValidationException.class, () -> service.process(reading(2, 120.0)));

        assertEquals("значение 120.0 нарушает правило: должно быть ≤ 100.0", e.getErrors().get("rule_" + rule.id()));
        assertTrue(telemetry.rows.isEmpty());
        assertNull(devices.rows.get(deviceId).getLastDataTime());
    }

    @Test
    void softViolationIsSaved() {
        RuleEntity soft = rules.add(deviceId, 3, RuleOperator.LE, 30.0, false);
        java.util.Map<String, String> outOfRange = service.process(reading(3, 35.0));
        assertEquals(1, telemetry.rows.size());
        assertEquals(java.util.Map.of("rule_" + soft.id(), "значение 35.0 нарушает правило: должно быть ≤ 30.0"),
                outOfRange);
    }

    @Test
    void strictWinsOverSoftAndErrorListsOnlyStrictRules() {
        RuleEntity soft = rules.add(deviceId, 2, RuleOperator.LE, 50.0, false);
        RuleEntity strict = rules.add(deviceId, 2, RuleOperator.LE, 100.0, true);

        ValidationException e = assertThrows(ValidationException.class, () -> service.process(reading(2, 120.0)));

        assertTrue(e.getErrors().containsKey("rule_" + strict.id()));
        assertFalse(e.getErrors().containsKey("rule_" + soft.id()));
        assertTrue(telemetry.rows.isEmpty());
    }

    @Test
    void valueInsideStrictRuleIsSaved() {
        rules.add(deviceId, 2, RuleOperator.GE, 0.0, true);
        rules.add(deviceId, 2, RuleOperator.LE, 100.0, true);
        assertDoesNotThrow(() -> service.process(reading(2, 100.0)));   // граница включена
        assertEquals(1, telemetry.rows.size());
    }

    @Test
    void rulesOfOtherTelemetryTypeAreIgnored() {
        rules.add(deviceId, 2, RuleOperator.LE, 100.0, true);
        assertDoesNotThrow(() -> service.process(reading(1, 500.0)));
    }

    @Test
    void rulesOfOtherDeviceAreIgnored() {
        rules.add(UUID.randomUUID(), 2, RuleOperator.LE, 100.0, true);
        assertDoesNotThrow(() -> service.process(reading(2, 500.0)));
    }
}