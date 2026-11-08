package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.DeviceState;
import com.iotplatform.iotcore.model.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DtoValidationTest {

    private static DeviceDto device(UUID id, String name, Integer battery, DeviceState state) {
        return new DeviceDto(id, null, name, "Acme", battery, state, null, null);
    }

    // ---------- DeviceDto ----------

    @Test
    void validDeviceHasNoErrors() {
        assertDoesNotThrow(() -> device(UUID.randomUUID(), "датчик", 50, DeviceState.ON).validate());
    }

    @Test
    void deviceBatteryBoundariesAreAllowed() {
        assertDoesNotThrow(() -> device(UUID.randomUUID(), "d", 0, DeviceState.OFF).validate());
        assertDoesNotThrow(() -> device(UUID.randomUUID(), "d", 100, DeviceState.OFF).validate());
        assertDoesNotThrow(() -> device(UUID.randomUUID(), "d", null, DeviceState.OFF).validate());
    }

    @Test
    void deviceCollectsAllErrorsAtOnce() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> device(null, "  ", 101, null).validate());
        assertEquals(4, e.getErrors().size());
        assertTrue(e.getErrors().containsKey("deviceId"));
        assertTrue(e.getErrors().containsKey("name"));
        assertTrue(e.getErrors().containsKey("state"));
        assertTrue(e.getErrors().containsKey("batteryLevel"));
    }

    @Test
    void deviceNegativeBatteryIsRejected() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> device(UUID.randomUUID(), "d", -1, DeviceState.ON).validate());
        assertEquals(1, e.getErrors().size());
        assertTrue(e.getErrors().containsKey("batteryLevel"));
    }

    @Test
    void withDeviceIdCopiesEverythingExceptId() {
        UUID user = UUID.randomUUID();
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        DeviceDto original = new DeviceDto(UUID.randomUUID(), user, "n", "m", 10, DeviceState.ON, "geo", t);
        UUID newId = UUID.randomUUID();

        DeviceDto copy = original.withDeviceId(newId);

        assertEquals(newId, copy.getDeviceId());
        assertEquals(user, copy.getUserId());
        assertEquals("n", copy.getName());
        assertEquals("m", copy.getManufacturer());
        assertEquals(10, copy.getBatteryLevel());
        assertEquals(DeviceState.ON, copy.getState());
        assertEquals("geo", copy.getGeolocation());
        assertEquals(t, copy.getLastDataTime());
        assertNotSame(original, copy);
    }

    // ---------- TelemetryDto ----------

    @Test
    void validTelemetryHasNoErrors() {
        assertDoesNotThrow(() -> new TelemetryDto(UUID.randomUUID(), Instant.now(), (short) 1, 3.5).validate());
    }

    @Test
    void telemetryRequiresAllFields() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> new TelemetryDto(null, null, null, null).validate());
        assertEquals(4, e.getErrors().size());
    }

    // ---------- UserDto ----------

    private static UserDto user(String email, String password, String username, LocalDate birth) {
        return new UserDto(null, username, email, null, birth, UserRole.GUEST, password);
    }

    @Test
    void creatingUserNeedsOnlyEmailAndPassword() {
        assertDoesNotThrow(() -> user("a@b.ru", "12345678", null, null).validate(true));
    }

    @Test
    void creatingUserWithoutEmailAndPasswordFails() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> user(null, null, null, null).validate(true));
        assertTrue(e.getErrors().containsKey("email"));
        assertTrue(e.getErrors().containsKey("password"));
    }

    @Test
    void emailWithoutAtIsRejected() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> user("not-an-email", "12345678", null, null).validate(true));
        assertTrue(e.getErrors().containsKey("email"));
    }

    @Test
    void shortPasswordIsRejected() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> user("a@b.ru", "1234567", null, null).validate(true));
        assertEquals("минимум 8 символов", e.getErrors().get("password"));
    }

    @Test
    void updateDoesNotRequireEmailOrPassword() {
        assertDoesNotThrow(() -> user(null, null, null, null).validate(false));
    }

    @Test
    void updateStillChecksFieldsThatWereSent() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> user(null, "short", " ", LocalDate.now().plusDays(1)).validate(false));
        assertTrue(e.getErrors().containsKey("password"));
        assertTrue(e.getErrors().containsKey("username"));
        assertTrue(e.getErrors().containsKey("dateOfBirth"));
    }

    @Test
    void userToStringDoesNotLeakPassword() {
        String s = user("a@b.ru", "secret-password", "vera", null).toString();
        assertFalse(s.contains("secret-password"));
    }
}