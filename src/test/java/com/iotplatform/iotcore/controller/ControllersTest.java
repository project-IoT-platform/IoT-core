package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.check.BatteryCheckResult;
import com.iotplatform.iotcore.check.TelemetryCheckResult;
import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.fake.FakeDeviceRepository;
import com.iotplatform.iotcore.fake.FakeRuleRepository;
import com.iotplatform.iotcore.fake.FakeTelemetryRepository;
import com.iotplatform.iotcore.fake.FakeUserRepository;
import com.iotplatform.iotcore.fake.RecordingNotificationService;
import com.iotplatform.iotcore.model.DeviceState;
import com.iotplatform.iotcore.model.RuleOperator;
import com.iotplatform.iotcore.model.UserRole;
import com.iotplatform.iotcore.service.AlertService;
import com.iotplatform.iotcore.service.DeviceService;
import com.iotplatform.iotcore.service.NotificationService;
import com.iotplatform.iotcore.service.RuleEngine;
import com.iotplatform.iotcore.service.TelemetryService;
import com.iotplatform.iotcore.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Контроллеры: передают вызов в сервис и, где нужно, шлют событие в сервис уведомлений. */
class ControllersTest {
    private FakeDeviceRepository devices;
    private FakeUserRepository users;
    private FakeTelemetryRepository telemetry;
    private FakeRuleRepository rules;
    private RecordingNotificationService notifications;

    private DeviceController deviceController;
    private UserController userController;
    private TelemetryController telemetryController;
    private NotificationController notificationController;

    @BeforeEach
    void setUp() {
        devices = new FakeDeviceRepository();
        users = new FakeUserRepository();
        telemetry = new FakeTelemetryRepository();
        rules = new FakeRuleRepository();
        notifications = new RecordingNotificationService();
        // вместо функций тиммейта: заряд < 20 низкий; телеметрия > 25 вне нормы
        AlertService alerts = new AlertService(
                (id, type, v) -> v > 25 ? new TelemetryCheckResult(true, v, v - 25, null, 25.0) : TelemetryCheckResult.normal(),
                (id, level) -> level < 20 ? new BatteryCheckResult(true, level, 20 - level, 20) : BatteryCheckResult.normal(),
                devices, notifications);

        deviceController = new DeviceController(new DeviceService(devices, users), notifications, alerts);
        userController = new UserController(new UserService(users), notifications);
        telemetryController = new TelemetryController(new TelemetryService(devices, telemetry, new RuleEngine(rules)), alerts);
        notificationController = new NotificationController(notifications);
    }

    private static DeviceDto device(UUID id) {
        return new DeviceDto(id, null, "n", "m", 15, DeviceState.ON, null, null);
    }

    // ---------- DeviceController ----------

    @Test
    void registerSavesAndSendsRegistrationEvent() {
        DeviceDto dto = device(UUID.randomUUID());   // заряд 15% < 20%
        deviceController.register(dto);

        assertTrue(devices.existsById(dto.getDeviceId()));
        assertEquals(1, notifications.registered.size());
        assertSame(dto, notifications.registered.get(0));
        assertEquals(1, notifications.batteryLow.size());   // при регистрации заряд тоже проверяется
    }

    @Test
    void registerWithNormalBatterySendsOnlyRegistration() {
        UUID id = UUID.randomUUID();
        deviceController.register(new DeviceDto(id, null, "n", "m", 90, DeviceState.ON, null, null));
        assertEquals(1, notifications.registered.size());
        assertTrue(notifications.batteryLow.isEmpty());
    }

    @Test
    void failedRegisterSendsNoEvents() {
        assertThrows(ValidationException.class, () -> deviceController.register(device(null)));
        assertTrue(notifications.registered.isEmpty());
        assertTrue(notifications.batteryLow.isEmpty());
    }

    @Test
    void updateChecksBatteryOfUpdatedDeviceOnlyOnce() {
        UUID id = UUID.randomUUID();
        devices.save(new DeviceEntity(id, null, "old", null, 90, "m", DeviceState.ON, null));

        DeviceDto updated = deviceController.update(id, device(null));   // 15%
        deviceController.update(id, device(null));                       // всё ещё 15%: повторно не шлём

        assertEquals(id, updated.getDeviceId());
        assertEquals(1, notifications.batteryLow.size());
        assertEquals(id, notifications.batteryLow.get(0).deviceId());
        assertTrue(notifications.registered.isEmpty());
    }

    @Test
    void getAndDeleteDevice() {
        UUID id = UUID.randomUUID();
        deviceController.register(device(id));
        assertEquals(id, deviceController.getById(id).getDeviceId());

        deviceController.delete(id);
        assertThrows(DeviceNotFoundException.class, () -> deviceController.getById(id));
    }

    // ---------- TelemetryController ----------

    @Test
    void telemetryOutOfNormIsSavedAndSentToNotifications() {
        UUID id = UUID.randomUUID();
        devices.save(new DeviceEntity(id, null, "n", null, null, "m", DeviceState.ON, null));
        TelemetryDto dto = new TelemetryDto(id, Instant.now(), (short) 1, 30.0);

        telemetryController.receive(dto);

        assertEquals(1, telemetry.rows.size());
        assertEquals(1, notifications.telemetryAlerts.size());
        assertSame(dto, notifications.telemetryAlerts.get(0).telemetry());
        assertEquals(5.0, notifications.telemetryAlerts.get(0).result().deviation());
    }

    @Test
    void telemetryInNormIsSavedButNotSent() {
        UUID id = UUID.randomUUID();
        devices.save(new DeviceEntity(id, null, "n", null, null, "m", DeviceState.ON, null));
        telemetryController.receive(new TelemetryDto(id, Instant.now(), (short) 1, 20.0));
        assertEquals(1, telemetry.rows.size());
        assertTrue(notifications.telemetryAlerts.isEmpty());
    }

    @Test
    void softRuleViolationStillGoesToNormCheck() {
        UUID id = UUID.randomUUID();
        devices.save(new DeviceEntity(id, null, "n", null, null, "m", DeviceState.ON, null));
        rules.add(id, 1, RuleOperator.LE, 28.0, false);

        telemetryController.receive(new TelemetryDto(id, Instant.now(), (short) 1, 30.0));

        assertEquals(1, telemetry.rows.size());
        assertEquals(1, notifications.telemetryAlerts.size());
    }

    @Test
    void strictRuleViolationIsNotSavedAndNotSent() {
        UUID id = UUID.randomUUID();
        devices.save(new DeviceEntity(id, null, "n", null, null, "m", DeviceState.ON, null));
        rules.add(id, 1, RuleOperator.LE, 28.0, true);

        assertThrows(ValidationException.class,
                () -> telemetryController.receive(new TelemetryDto(id, Instant.now(), (short) 1, 30.0)));
        assertTrue(telemetry.rows.isEmpty());
        assertTrue(notifications.telemetryAlerts.isEmpty());
    }

    @Test
    void telemetryForUnknownDeviceIsNotSentToNotifications() {
        TelemetryDto dto = new TelemetryDto(UUID.randomUUID(), Instant.now(), (short) 1, 30.0);
        assertThrows(DeviceNotFoundException.class, () -> telemetryController.receive(dto));
        assertTrue(notifications.telemetryAlerts.isEmpty());
    }

    // ---------- UserController ----------

    @Test
    void userCrudGoesThroughService() {
        UserResponse created = userController.create(
                new UserDto(null, "vera", "v@x.ru", null, null, null, "password123"));
        assertEquals(created, userController.getById(created.id()));
        assertEquals(java.util.List.of(created), notifications.usersRegistered);   // письмо с id пользователя

        UserResponse updated = userController.update(created.id(),
                new UserDto(null, null, null, null, null, UserRole.ADMIN, null));
        assertEquals(UserRole.ADMIN, updated.role());

        userController.delete(created.id());
        assertFalse(users.existsById(created.id()));
    }

    @Test
    void failedUserCreateSendsNoEmail() {
        assertThrows(ValidationException.class,
                () -> userController.create(new UserDto(null, null, "bad", null, null, null, "1")));
        assertTrue(notifications.usersRegistered.isEmpty());
    }

    // ---------- NotificationController ----------

    @Test
    void sendDeviceStatusReturnsIdAndState() {
        UUID id = UUID.randomUUID();
        notifications.stateToReturn = DeviceState.OFF;

        Map<String, Object> result = notificationController.sendDeviceStatus(id);

        assertEquals(id, result.get("deviceId"));
        assertEquals(DeviceState.OFF, result.get("state"));
        assertEquals(1, notifications.statusRequests.size());
    }

    @Test
    void settingsArePassedThrough() {
        assertEquals(notifications.settings, notificationController.getSettings());

        NotificationService.Settings s = new NotificationService.Settings(30.0, 1.0);
        assertEquals(s, notificationController.updateSettings(s));
        assertEquals(s, notifications.updatedSettings.get(0));
    }
}