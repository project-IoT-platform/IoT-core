package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.entity.TelemetryEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.RuleOperator;
import com.iotplatform.iotcore.testsupport.InMemoryDeviceRepository;
import com.iotplatform.iotcore.testsupport.InMemoryRuleRepository;
import com.iotplatform.iotcore.testsupport.RecordingTelemetryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryServiceTest {
    private static final short HUMIDITY = 2;
    private static final short TEMPERATURE = 3;

    private final UUID deviceId = UUID.randomUUID();
    private InMemoryDeviceRepository devices;
    private InMemoryRuleRepository rules;
    private RecordingTelemetryRepository telemetry;
    private TelemetryService service;

    @BeforeEach
    void setUp() {
        devices = new InMemoryDeviceRepository();
        devices.addDevice(deviceId);
        rules = new InMemoryRuleRepository();
        telemetry = new RecordingTelemetryRepository();
        service = new TelemetryService(devices, telemetry, new RuleEngine(rules));
    }

    private void addRule(short type, RuleOperator op, double limit, boolean strict) {
        RuleEntity rule = rules.save(new RuleEntity(UUID.randomUUID(), type, op, limit, strict));
        rules.attachToDevice(rule.id(), deviceId);
    }

    private TelemetryDto dto(short type, double value, Instant time) {
        return new TelemetryDto(deviceId, time, type, value);
    }

    @Test
    void validTelemetryIsSavedAndLastDataTimeIsUpdated() {
        Instant time = Instant.parse("2026-11-07T10:00:00Z");

        Map<String, String> result = service.process(dto(HUMIDITY, 55, time));

        assertTrue(result.isEmpty());
        assertEquals(1, telemetry.saved().size());
        assertEquals(time, devices.findById(deviceId).orElseThrow().getLastDataTime());
    }

    @Test
    void savedEntityCarriesTheValuesFromTheDto() {
        Instant time = Instant.parse("2026-11-07T10:00:00Z");

        service.process(dto(TEMPERATURE, 21.5, time));

        TelemetryEntity saved = telemetry.saved().get(0);
        assertEquals(deviceId, saved.deviceId());
        assertEquals(time, saved.time());
        assertEquals(Short.valueOf(TEMPERATURE), saved.telemetryType());
        assertEquals(Double.valueOf(21.5), saved.value());
    }

    @Test
    void strictViolationSavesNothingAndDoesNotTouchLastDataTime() {
        addRule(HUMIDITY, RuleOperator.LE, 100, true);

        assertThrows(ValidationException.class,
                () -> service.process(dto(HUMIDITY, 120, Instant.now())));

        assertTrue(telemetry.saved().isEmpty());
        assertEquals(0, devices.updateLastDataTimeCalls());
        assertNull(devices.findById(deviceId).orElseThrow().getLastDataTime());
    }

    @Test
    void softViolationIsSavedAndReturned() {
        addRule(TEMPERATURE, RuleOperator.LE, 30, false);
        Instant time = Instant.parse("2026-11-07T10:00:00Z");

        Map<String, String> outOfRange = service.process(dto(TEMPERATURE, 35, time));

        assertEquals(1, outOfRange.size());
        assertEquals(1, telemetry.saved().size());
        assertEquals(Double.valueOf(35.0), telemetry.saved().get(0).value());
        assertEquals(time, devices.findById(deviceId).orElseThrow().getLastDataTime());
    }

    @Test
    void strictViolationWinsEvenIfSoftRuleIsAlsoViolated() {
        addRule(TEMPERATURE, RuleOperator.LE, 100, true);
        addRule(TEMPERATURE, RuleOperator.LE, 30, false);

        assertThrows(ValidationException.class,
                () -> service.process(dto(TEMPERATURE, 150, Instant.now())));

        assertTrue(telemetry.saved().isEmpty());
    }

    @Test
    void unknownDeviceThrowsDeviceNotFound() {
        TelemetryDto unknown = new TelemetryDto(UUID.randomUUID(), Instant.now(), HUMIDITY, 55.0);

        assertThrows(DeviceNotFoundException.class, () -> service.process(unknown));

        assertTrue(telemetry.saved().isEmpty());
    }

    @Test
    void unknownDeviceIsReportedBeforeRulesAreChecked() {
        addRule(HUMIDITY, RuleOperator.LE, 100, true);
        TelemetryDto unknown = new TelemetryDto(UUID.randomUUID(), Instant.now(), HUMIDITY, 500.0);

        assertThrows(DeviceNotFoundException.class, () -> service.process(unknown));
    }

    @Test
    void emptyDtoReportsAllMissingFields() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.process(new TelemetryDto(null, null, null, null)));

        assertEquals(Set.of("deviceId", "time", "telemetryType", "value"), e.getErrors().keySet());
        assertTrue(telemetry.saved().isEmpty());
    }

    @Test
    void missingValueIsReportedByName() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.process(new TelemetryDto(deviceId, Instant.now(), HUMIDITY, null)));

        assertEquals(Set.of("value"), e.getErrors().keySet());
    }

    @Test
    void telemetryOfTypeWithoutRulesIsAccepted() {
        addRule(HUMIDITY, RuleOperator.LE, 100, true);

        Map<String, String> result = service.process(dto(TEMPERATURE, 500, Instant.now()));

        assertTrue(result.isEmpty());
        assertEquals(1, telemetry.saved().size());
    }
}