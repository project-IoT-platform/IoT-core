package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.fake.FakeDeviceRepository;
import com.iotplatform.iotcore.fake.FakeUserRepository;
import com.iotplatform.iotcore.model.DeviceState;
import com.iotplatform.iotcore.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeviceServiceTest {
    private FakeDeviceRepository devices;
    private FakeUserRepository users;
    private DeviceService service;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        devices = new FakeDeviceRepository();
        users = new FakeUserRepository();
        service = new DeviceService(devices, users);
        ownerId = UUID.randomUUID();
        users.save(new UserEntity(ownerId, "vera", "v@x.ru", null, null, new byte[0], UserRole.ADMIN));
    }

    private DeviceDto dto(UUID id, UUID userId) {
        return new DeviceDto(id, userId, "Термометр", "Acme", 80, DeviceState.ON, "55.7,37.6", null);
    }

    @Test
    void registerSavesNewDevice() {
        UUID id = UUID.randomUUID();
        service.register(dto(id, ownerId));

        DeviceEntity saved = devices.rows.get(id);
        assertNotNull(saved);
        assertEquals("Термометр", saved.getName());
        assertEquals(ownerId, saved.getUserId());
        assertEquals(80, saved.getBatteryLevel());
        assertNull(saved.getLastDataTime());
    }

    @Test
    void registerWithoutOwnerIsAllowed() {
        UUID id = UUID.randomUUID();
        service.register(dto(id, null));
        assertTrue(devices.existsById(id));
    }

    @Test
    void registerWithUnknownOwnerFails() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.register(dto(UUID.randomUUID(), UUID.randomUUID())));
        assertTrue(e.getErrors().containsKey("userId"));
        assertTrue(devices.rows.isEmpty());
    }

    @Test
    void registerInvalidDtoSavesNothing() {
        DeviceDto bad = new DeviceDto(null, null, null, null, 500, null, null, null);
        assertThrows(ValidationException.class, () -> service.register(bad));
        assertEquals(0, devices.saveCalls);
    }

    @Test
    void reRegisterKeepsLastDataTime() {
        UUID id = UUID.randomUUID();
        Instant t = Instant.parse("2026-11-01T10:00:00Z");
        devices.save(new DeviceEntity(id, null, "old", t, 10, "Acme", DeviceState.OFF, null));

        service.register(dto(id, ownerId));

        assertEquals(t, devices.rows.get(id).getLastDataTime());
        assertEquals("Термометр", devices.rows.get(id).getName());
    }

    @Test
    void findByIdReturnsDto() {
        UUID id = UUID.randomUUID();
        Instant t = Instant.parse("2026-11-01T10:00:00Z");
        devices.save(new DeviceEntity(id, ownerId, "n", t, 42, "m", DeviceState.OFF, "geo"));

        DeviceDto found = service.findById(id);

        assertEquals(id, found.getDeviceId());
        assertEquals(ownerId, found.getUserId());
        assertEquals(42, found.getBatteryLevel());
        assertEquals(DeviceState.OFF, found.getState());
        assertEquals(t, found.getLastDataTime());
    }

    @Test
    void findByIdUnknownThrowsNotFound() {
        assertThrows(DeviceNotFoundException.class, () -> service.findById(UUID.randomUUID()));
    }

    @Test
    void updateUsesIdFromPathAndKeepsLastDataTime() {
        UUID id = UUID.randomUUID();
        Instant t = Instant.parse("2026-11-01T10:00:00Z");
        devices.save(new DeviceEntity(id, null, "old", t, 10, "Acme", DeviceState.OFF, null));

        // в теле другой id (или вообще без id): должен победить id из адреса
        DeviceDto result = service.update(id, dto(UUID.randomUUID(), ownerId));

        assertEquals(id, result.getDeviceId());
        assertEquals(t, result.getLastDataTime());
        assertEquals(1, devices.rows.size());
        assertEquals("Термометр", devices.rows.get(id).getName());
    }

    @Test
    void updateWorksWhenBodyHasNoId() {
        UUID id = UUID.randomUUID();
        devices.save(new DeviceEntity(id, null, "old", null, 10, "Acme", DeviceState.OFF, null));
        assertEquals(id, service.update(id, dto(null, null)).getDeviceId());
    }

    @Test
    void updateUnknownDeviceThrowsNotFound() {
        assertThrows(DeviceNotFoundException.class, () -> service.update(UUID.randomUUID(), dto(null, null)));
        assertEquals(0, devices.saveCalls);
    }

    @Test
    void deleteRemovesDevice() {
        UUID id = UUID.randomUUID();
        devices.save(new DeviceEntity(id, null, "n", null, null, "m", DeviceState.ON, null));
        service.delete(id);
        assertFalse(devices.existsById(id));
    }

    @Test
    void deleteUnknownThrowsNotFound() {
        assertThrows(DeviceNotFoundException.class, () -> service.delete(UUID.randomUUID()));
    }
}