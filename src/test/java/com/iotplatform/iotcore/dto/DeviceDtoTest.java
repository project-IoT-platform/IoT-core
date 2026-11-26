package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.DeviceState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeviceDtoTest {
    private final UUID id = UUID.randomUUID();

    private DeviceDto device(UUID deviceId, String name, DeviceState state, Integer battery) {
        return new DeviceDto(deviceId, null, name, "ACME", battery, state, null, null);
    }

    private Set<String> errors(DeviceDto dto) {
        return assertThrows(ValidationException.class, dto::validate).getErrors().keySet();
    }

    @Test
    void completeDeviceIsValid() {
        assertDoesNotThrow(() -> device(id, "Sensor", DeviceState.ON, 80).validate());
    }

    @Test
    void emptyDeviceReportsIdNameAndState() {
        assertEquals(Set.of("deviceId", "name", "state"), errors(device(null, null, null, null)));
    }

    @Test
    void blankNameIsRejected() {
        assertEquals(Set.of("name"), errors(device(id, "   ", DeviceState.ON, 80)));
    }

    @Test
    void missingStateIsRejected() {
        assertEquals(Set.of("state"), errors(device(id, "Sensor", null, 80)));
    }

    @Test
    void batteryOutOfRangeIsRejected() {
        assertEquals(Set.of("batteryLevel"), errors(device(id, "Sensor", DeviceState.ON, -1)));
        assertEquals(Set.of("batteryLevel"), errors(device(id, "Sensor", DeviceState.ON, 101)));
    }

    @Test
    void batteryBoundariesAndMissingBatteryAreAccepted() {
        assertDoesNotThrow(() -> device(id, "Sensor", DeviceState.ON, 0).validate());
        assertDoesNotThrow(() -> device(id, "Sensor", DeviceState.ON, 100).validate());
        assertDoesNotThrow(() -> device(id, "Sensor", DeviceState.ON, null).validate());
    }

    @Test
    void withDeviceIdReplacesOnlyTheId() {
        UUID owner = UUID.randomUUID();
        Instant time = Instant.parse("2026-11-07T10:00:00Z");
        DeviceDto original = new DeviceDto(id, owner, "Sensor", "ACME", 80, DeviceState.OFF, "50.1,8.6", time);
        UUID newId = UUID.randomUUID();

        DeviceDto copy = original.withDeviceId(newId);

        assertEquals(newId, copy.getDeviceId());
        assertEquals(owner, copy.getUserId());
        assertEquals("Sensor", copy.getName());
        assertEquals("ACME", copy.getManufacturer());
        assertEquals(Integer.valueOf(80), copy.getBatteryLevel());
        assertEquals(DeviceState.OFF, copy.getState());
        assertEquals("50.1,8.6", copy.getGeolocation());
        assertEquals(time, copy.getLastDataTime());
    }
}