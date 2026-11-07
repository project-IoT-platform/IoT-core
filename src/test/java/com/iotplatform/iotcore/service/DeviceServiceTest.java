package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.DeviceState;
import com.iotplatform.iotcore.model.UserRole;
import com.iotplatform.iotcore.testsupport.InMemoryDeviceRepository;
import com.iotplatform.iotcore.testsupport.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceServiceTest {
    private InMemoryDeviceRepository devices;
    private InMemoryUserRepository users;
    private DeviceService service;

    @BeforeEach
    void setUp() {
        devices = new InMemoryDeviceRepository();
        users = new InMemoryUserRepository();
        service = new DeviceService(devices, users);
    }

    private UUID existingUser() {
        UUID id = UUID.randomUUID();
        users.save(new UserEntity(id, "user", "user@example.com", null, null, new byte[0], UserRole.GUEST));
        return id;
    }

    private static DeviceDto dto(UUID deviceId, UUID userId) {
        return new DeviceDto(deviceId, userId, "Sensor", "ACME", 80, DeviceState.ON, null, null);
    }

    @Test
    void registeringDeviceWithoutOwnerIsAllowed() {
        UUID id = UUID.randomUUID();

        service.register(dto(id, null));

        assertTrue(devices.existsById(id));
    }

    @Test
    void registeringDeviceWithExistingOwnerWorks() {
        UUID id = UUID.randomUUID();
        UUID userId = existingUser();

        service.register(dto(id, userId));

        assertEquals(userId, devices.findById(id).orElseThrow().getUserId());
    }

    @Test
    void unknownOwnerIsRejectedWithUserIdError() {
        UUID id = UUID.randomUUID();

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.register(dto(id, UUID.randomUUID())));

        assertEquals(Set.of("userId"), e.getErrors().keySet());
        assertFalse(devices.existsById(id));
    }

    @Test
    void invalidDtoIsRejectedAndNothingIsSaved() {
        UUID id = UUID.randomUUID();
        DeviceDto blankName = new DeviceDto(id, null, "  ", "ACME", 80, DeviceState.ON, null, null);

        assertThrows(ValidationException.class, () -> service.register(blankName));

        assertFalse(devices.existsById(id));
    }

    @Test
    void newDeviceHasNoLastDataTime() {
        UUID id = UUID.randomUUID();

        service.register(dto(id, null));

        assertNull(devices.findById(id).orElseThrow().getLastDataTime());
    }

    @Test
    void registeringAgainKeepsLastDataTime() {
        UUID id = UUID.randomUUID();
        service.register(dto(id, null));
        Instant real = Instant.parse("2026-11-07T10:00:00Z");
        devices.updateLastDataTime(id, real);

        // клиент присылает своё lastDataTime, но оно не должно перетереть реальное
        DeviceDto again = new DeviceDto(id, null, "Sensor", "ACME", 80, DeviceState.ON, null,
                Instant.parse("2000-01-01T00:00:00Z"));
        service.register(again);

        assertEquals(real, devices.findById(id).orElseThrow().getLastDataTime());
    }

    @Test
    void registeringAgainUpdatesOtherFields() {
        UUID id = UUID.randomUUID();
        service.register(dto(id, null));

        service.register(new DeviceDto(id, null, "Renamed", "ACME", 55, DeviceState.OFF, null, null));

        DeviceEntity stored = devices.findById(id).orElseThrow();
        assertEquals("Renamed", stored.getName());
        assertEquals(DeviceState.OFF, stored.getState());
    }

    @Test
    void findingMissingDeviceThrows() {
        assertThrows(DeviceNotFoundException.class, () -> service.findById(UUID.randomUUID()));
    }

    @Test
    void findByIdReturnsDtoWithTheSameData() {
        UUID id = UUID.randomUUID();
        service.register(dto(id, null));

        DeviceDto found = service.findById(id);

        assertEquals(id, found.getDeviceId());
        assertEquals("Sensor", found.getName());
        assertEquals(DeviceState.ON, found.getState());
    }

    @Test
    void updatingUnknownDeviceThrows() {
        UUID id = UUID.randomUUID();

        assertThrows(DeviceNotFoundException.class, () -> service.update(id, dto(id, null)));
    }

    @Test
    void updateKeepsLastDataTime() {
        UUID id = UUID.randomUUID();
        service.register(dto(id, null));
        Instant real = Instant.parse("2026-11-07T10:00:00Z");
        devices.updateLastDataTime(id, real);

        DeviceDto updated = service.update(id, new DeviceDto(id, null, "New name", "ACME", 70,
                DeviceState.ON, null, null));

        assertEquals(real, updated.getLastDataTime());
        assertEquals("New name", updated.getName());
    }

    @Test
    void updateUsesTheIdFromThePathNotFromTheBody() {
        UUID id = UUID.randomUUID();
        service.register(dto(id, null));

        DeviceDto updated = service.update(id, dto(UUID.randomUUID(), null));

        assertEquals(id, updated.getDeviceId());
    }

    @Test
    void updateWithUnknownOwnerIsRejected() {
        UUID id = UUID.randomUUID();
        service.register(dto(id, null));

        assertThrows(ValidationException.class, () -> service.update(id, dto(id, UUID.randomUUID())));
    }

    @Test
    void deletedDeviceIsGone() {
        UUID id = UUID.randomUUID();
        service.register(dto(id, null));

        service.delete(id);

        assertFalse(devices.existsById(id));
    }

    @Test
    void deletingMissingDeviceThrows() {
        assertThrows(DeviceNotFoundException.class, () -> service.delete(UUID.randomUUID()));
    }
}