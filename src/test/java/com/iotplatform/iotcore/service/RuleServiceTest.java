package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.RuleDto;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.RuleNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.RuleOperator;
import com.iotplatform.iotcore.testsupport.InMemoryDeviceRepository;
import com.iotplatform.iotcore.testsupport.InMemoryRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleServiceTest {
    private final UUID deviceId = UUID.randomUUID();
    private InMemoryDeviceRepository devices;
    private InMemoryRuleRepository rules;
    private RuleService service;

    @BeforeEach
    void setUp() {
        devices = new InMemoryDeviceRepository();
        devices.addDevice(deviceId);
        rules = new InMemoryRuleRepository();
        service = new RuleService(rules, devices);
    }

    private RuleEntity createRule() {
        return service.create(new RuleDto((short) 2, RuleOperator.LE, 100.0, null));
    }

    @Test
    void ruleWithoutStrictFlagIsStrict() {
        RuleEntity rule = service.create(new RuleDto((short) 2, RuleOperator.LE, 100.0, null));

        assertTrue(rule.strict());
    }

    @Test
    void explicitStrictFalseIsKept() {
        RuleEntity rule = service.create(new RuleDto((short) 3, RuleOperator.LE, 30.0, false));

        assertFalse(rule.strict());
    }

    @Test
    void createdRuleKeepsAllFieldsAndGetsAnId() {
        RuleEntity rule = service.create(new RuleDto((short) 3, RuleOperator.GT, 7.5, true));

        assertNotNull(rule.id());
        assertEquals(Short.valueOf((short) 3), rule.telemetryType());
        assertEquals(RuleOperator.GT, rule.operator());
        assertEquals(Double.valueOf(7.5), rule.value());
    }

    @Test
    void createdRuleCanBeFoundById() {
        RuleEntity rule = createRule();

        assertEquals(rule, service.findById(rule.id()));
    }

    @Test
    void invalidDtoIsRejectedAndNothingIsSaved() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create(new RuleDto(null, null, null, null)));

        assertEquals(Set.of("telemetryType", "operator", "value"), e.getErrors().keySet());
    }

    @Test
    void findingMissingRuleThrows() {
        assertThrows(RuleNotFoundException.class, () -> service.findById(UUID.randomUUID()));
    }

    @Test
    void deletedRuleIsGone() {
        RuleEntity rule = createRule();

        service.delete(rule.id());

        assertThrows(RuleNotFoundException.class, () -> service.findById(rule.id()));
    }

    @Test
    void deletingMissingRuleThrows() {
        assertThrows(RuleNotFoundException.class, () -> service.delete(UUID.randomUUID()));
    }

    @Test
    void attachedRuleShowsUpInDeviceRules() {
        RuleEntity rule = createRule();

        service.attach(deviceId, rule.id());

        List<RuleEntity> deviceRules = service.findByDevice(deviceId);
        assertEquals(1, deviceRules.size());
        assertEquals(rule.id(), deviceRules.get(0).id());
    }

    @Test
    void attachingToUnknownDeviceThrowsAndCreatesNoLink() {
        RuleEntity rule = createRule();
        UUID unknownDevice = UUID.randomUUID();

        assertThrows(DeviceNotFoundException.class, () -> service.attach(unknownDevice, rule.id()));

        assertFalse(rules.isAttached(rule.id(), unknownDevice));
    }

    @Test
    void attachingUnknownRuleThrowsAndCreatesNoLink() {
        UUID unknownRule = UUID.randomUUID();

        assertThrows(RuleNotFoundException.class, () -> service.attach(deviceId, unknownRule));

        assertFalse(rules.isAttached(unknownRule, deviceId));
    }

    @Test
    void detachingMissingLinkThrowsRuleNotFound() {
        RuleEntity rule = createRule();

        assertThrows(RuleNotFoundException.class, () -> service.detach(deviceId, rule.id()));
    }

    @Test
    void detachedRuleDisappearsFromDeviceRules() {
        RuleEntity rule = createRule();
        service.attach(deviceId, rule.id());

        service.detach(deviceId, rule.id());

        assertTrue(service.findByDevice(deviceId).isEmpty());
    }

    @Test
    void detachingTwiceThrowsOnTheSecondCall() {
        RuleEntity rule = createRule();
        service.attach(deviceId, rule.id());
        service.detach(deviceId, rule.id());

        assertThrows(RuleNotFoundException.class, () -> service.detach(deviceId, rule.id()));
    }

    @Test
    void listingRulesOfUnknownDeviceThrows() {
        assertThrows(DeviceNotFoundException.class, () -> service.findByDevice(UUID.randomUUID()));
    }

    @Test
    void deviceWithoutRulesHasEmptyList() {
        assertTrue(service.findByDevice(deviceId).isEmpty());
    }
}