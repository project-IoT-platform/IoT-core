package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.RuleOperator;
import com.iotplatform.iotcore.repository.RuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleEngineTest {

    @Mock
    private RuleRepository ruleRepository;

    @InjectMocks
    private RuleEngine ruleEngine;

    private UUID deviceId;

    @BeforeEach
    void setUp() {
        deviceId = UUID.randomUUID();
    }

    @Test
    void check_ValueInNorm_ReturnsEmptyMap() {
        RuleEntity rule = new RuleEntity(UUID.randomUUID(), (short) 2, RuleOperator.LE, 30.0, false); // Меньше или равно 30
        when(ruleRepository.findByDeviceId(deviceId)).thenReturn(List.of(rule));

        TelemetryDto dto = new TelemetryDto(deviceId, Instant.now(), (short) 2, 25.0);
        Map<String, String> result = ruleEngine.check(dto);

        assertTrue(result.isEmpty(), "Нарушений быть не должно");
    }

    @Test
    void check_NonStrictViolation_ReturnsViolationMap() {
        RuleEntity rule = new RuleEntity(UUID.randomUUID(), (short) 2, RuleOperator.LE, 30.0, false); // Нестрогое
        when(ruleRepository.findByDeviceId(deviceId)).thenReturn(List.of(rule));

        TelemetryDto dto = new TelemetryDto(deviceId, Instant.now(), (short) 2, 35.0);
        Map<String, String> result = ruleEngine.check(dto);

        assertFalse(result.isEmpty(), "Должно вернуть нарушение (но не выбросить ошибку)");
        assertTrue(result.values().iterator().next().contains("35.0"));
    }

    @Test
    void check_StrictViolation_ThrowsException() {
        RuleEntity rule = new RuleEntity(UUID.randomUUID(), (short) 2, RuleOperator.LE, 30.0, true); // СТРОГОЕ
        when(ruleRepository.findByDeviceId(deviceId)).thenReturn(List.of(rule));

        TelemetryDto dto = new TelemetryDto(deviceId, Instant.now(), (short) 2, 35.0);

        assertThrows(ValidationException.class, () -> ruleEngine.check(dto), "Строгое правило должно выбрасывать ошибку");
    }
}