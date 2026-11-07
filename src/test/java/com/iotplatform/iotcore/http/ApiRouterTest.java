package com.iotplatform.iotcore.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iotplatform.iotcore.controller.DeviceController;
import com.iotplatform.iotcore.controller.NotificationController;
import com.iotplatform.iotcore.controller.TelemetryController;
import com.iotplatform.iotcore.controller.UserController;
import com.iotplatform.iotcore.check.StubBatteryChecker;
import com.iotplatform.iotcore.check.StubTelemetryNormChecker;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.exception.DatabaseException;
import com.iotplatform.iotcore.fake.FakeDeviceRepository;
import com.iotplatform.iotcore.fake.FakeRuleRepository;
import com.iotplatform.iotcore.fake.FakeTelemetryRepository;
import com.iotplatform.iotcore.fake.FakeUserRepository;
import com.iotplatform.iotcore.fake.RecordingNotificationService;
import com.iotplatform.iotcore.model.DeviceState;
import com.iotplatform.iotcore.model.RuleOperator;
import com.iotplatform.iotcore.service.AlertService;
import com.iotplatform.iotcore.service.DeviceService;
import com.iotplatform.iotcore.service.RuleEngine;
import com.iotplatform.iotcore.service.TelemetryService;
import com.iotplatform.iotcore.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Маршрутизация и коды ответов: от «сырых» method/uri/body до статуса и JSON. */
class ApiRouterTest {
    private FakeDeviceRepository devices;
    private FakeUserRepository users;
    private FakeTelemetryRepository telemetry;
    private FakeRuleRepository rules;
    private RecordingNotificationService notifications;
    private ApiRouter router;

    @BeforeEach
    void setUp() {
        devices = new FakeDeviceRepository();
        users = new FakeUserRepository();
        telemetry = new FakeTelemetryRepository();
        rules = new FakeRuleRepository();
        notifications = new RecordingNotificationService();
        router = routerWith(devices);
    }

    private ApiRouter routerWith(FakeDeviceRepository deviceRepo) {
        AlertService alerts = new AlertService(new StubTelemetryNormChecker(), new StubBatteryChecker(),
                deviceRepo, notifications);
        return new ApiRouter(
                new DeviceController(new DeviceService(deviceRepo, users), notifications, alerts),
                new UserController(new UserService(users), notifications),
                new TelemetryController(new TelemetryService(deviceRepo, telemetry, new RuleEngine(rules)), alerts),
                new NotificationController(notifications));
    }

    private ApiRouter.Response call(String method, String uri, String body) {
        return router.handle(method, uri, body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> json(ApiRouter.Response r) throws Exception {
        assertNotNull(r.body(), "ожидали JSON в ответе");
        return Json.MAPPER.readValue(r.body(), new TypeReference<Map<String, Object>>() {});
    }

    private UUID existingDevice() {
        UUID id = UUID.randomUUID();
        devices.save(new DeviceEntity(id, null, "Термометр", null, 50, "Acme", DeviceState.ON, null));
        return id;
    }

    // ---------- адреса ----------

    @Test
    void unknownPathsGive404() {
        assertEquals(404, call("GET", "/", null).status());
        assertEquals(404, call("GET", "/api", null).status());
        assertEquals(404, call("GET", "/api/cats", null).status());
        assertEquals(404, call("GET", "/other/devices", null).status());
        assertEquals(404, call("GET", "/api/devices/" + UUID.randomUUID() + "/extra", null).status());
        assertEquals(404, call("GET", "/api/notifications", null).status());
    }

    @Test
    void badUuidInPathGives400() throws Exception {
        ApiRouter.Response r = call("GET", "/api/devices/not-a-uuid", null);
        assertEquals(400, r.status());
        assertTrue(json(r).containsKey("id"));
    }

    @Test
    void malformedUriGives400() {
        assertEquals(400, call("GET", "/api/devices/a b", null).status());
    }

    @Test
    void trailingSlashAndQueryAreIgnored() {
        UUID id = existingDevice();
        assertEquals(200, call("GET", "/api/devices/" + id + "/", null).status());
        assertEquals(200, call("GET", "/api/devices/" + id + "?x=1", null).status());
    }

    @Test
    void wrongMethodGives405() {
        assertEquals(405, call("GET", "/api/devices", null).status());
        assertEquals(405, call("PATCH", "/api/devices/" + UUID.randomUUID(), null).status());
        assertEquals(405, call("POST", "/api/users/" + UUID.randomUUID(), "{}").status());
        assertEquals(405, call("GET", "/api/telemetry", null).status());
        assertEquals(405, call("POST", "/api/telemetry/" + UUID.randomUUID(), "{}").status());
        assertEquals(405, call("DELETE", "/api/notifications/settings", null).status());
        assertEquals(405, call("POST", "/api/notifications/devices/" + UUID.randomUUID() + "/status", null).status());
    }

    // ---------- устройства ----------

    @Test
    void registerDeviceGives202() {
        UUID id = UUID.randomUUID();
        ApiRouter.Response r = call("POST", "/api/devices",
                "{\"deviceId\":\"" + id + "\",\"name\":\"Датчик\",\"state\":\"ON\",\"batteryLevel\":10}");
        assertEquals(202, r.status());
        assertNull(r.body());
        assertTrue(devices.existsById(id));
        assertEquals(1, notifications.registered.size());
    }

    @Test
    void getDeviceReturnsJson() throws Exception {
        UUID id = existingDevice();
        ApiRouter.Response r = call("GET", "/api/devices/" + id, null);
        assertEquals(200, r.status());
        Map<String, Object> body = json(r);
        assertEquals(id.toString(), body.get("deviceId"));
        assertEquals("Термометр", body.get("name"));
        assertEquals("ON", body.get("state"));
    }

    @Test
    void lastDataTimeIsIsoStringNotNumber() throws Exception {
        UUID id = UUID.randomUUID();
        devices.save(new DeviceEntity(id, null, "n", Instant.parse("2026-11-06T12:00:00Z"),
                null, "m", DeviceState.ON, null));
        assertEquals("2026-11-06T12:00:00Z", json(call("GET", "/api/devices/" + id, null)).get("lastDataTime"));
    }

    @Test
    void getMissingDeviceGives404() throws Exception {
        ApiRouter.Response r = call("GET", "/api/devices/" + UUID.randomUUID(), null);
        assertEquals(404, r.status());
        assertTrue(json(r).get("error").toString().contains("Устройство не найдено"));
    }

    @Test
    void updateDeviceGives200() throws Exception {
        UUID id = existingDevice();
        ApiRouter.Response r = call("PUT", "/api/devices/" + id,
                "{\"name\":\"Новое имя\",\"state\":\"OFF\"}");
        assertEquals(200, r.status());
        assertEquals("Новое имя", json(r).get("name"));
        assertEquals(DeviceState.OFF, devices.rows.get(id).getState());
    }

    @Test
    void deleteDeviceGives204() {
        UUID id = existingDevice();
        ApiRouter.Response r = call("DELETE", "/api/devices/" + id, null);
        assertEquals(204, r.status());
        assertNull(r.body());
        assertEquals(404, call("DELETE", "/api/devices/" + id, null).status());
    }

    @Test
    void invalidDeviceGives400WithFieldErrors() throws Exception {
        ApiRouter.Response r = call("POST", "/api/devices", "{\"batteryLevel\":150}");
        assertEquals(400, r.status());
        Map<String, Object> errors = json(r);
        assertTrue(errors.containsKey("deviceId"));
        assertTrue(errors.containsKey("name"));
        assertTrue(errors.containsKey("state"));
        assertTrue(errors.containsKey("batteryLevel"));
    }

    @Test
    void brokenJsonGives400() throws Exception {
        ApiRouter.Response r = call("POST", "/api/devices", "{not json");
        assertEquals(400, r.status());
        assertTrue(json(r).get("error").toString().startsWith("Некорректный JSON"));
    }

    @Test
    void unknownEnumValueGives400() {
        assertEquals(400, call("POST", "/api/devices",
                "{\"deviceId\":\"" + UUID.randomUUID() + "\",\"name\":\"n\",\"state\":\"BROKEN\"}").status());
    }

    @Test
    void emptyOrNullBodyGives400() throws Exception {
        ApiRouter.Response empty = call("POST", "/api/devices", "");
        assertEquals(400, empty.status());
        assertTrue(json(empty).containsKey("body"));

        assertEquals(400, call("POST", "/api/devices", "null").status());
        assertEquals(400, router.handle("POST", "/api/devices", null).status());
    }

    @Test
    void unknownJsonFieldsAreIgnored() {
        assertEquals(202, call("POST", "/api/devices",
                "{\"deviceId\":\"" + UUID.randomUUID() + "\",\"name\":\"n\",\"state\":\"ON\",\"color\":\"red\"}").status());
    }

    // ---------- пользователи ----------

    @Test
    void createUserGives201WithoutPasswordInResponse() throws Exception {
        ApiRouter.Response r = call("POST", "/api/users",
                "{\"email\":\"V@X.ru\",\"password\":\"password123\",\"dateOfBirth\":\"2005-03-01\"}");
        assertEquals(201, r.status());
        Map<String, Object> body = json(r);
        assertEquals("v@x.ru", body.get("email"));
        assertEquals("GUEST", body.get("role"));
        assertEquals("2005-03-01", body.get("dateOfBirth"));
        assertFalse(body.containsKey("password"));
        assertFalse(body.containsKey("passwordHash"));
    }

    @Test
    void userFlow() throws Exception {
        String id = json(call("POST", "/api/users", "{\"email\":\"v@x.ru\",\"password\":\"password123\"}"))
                .get("id").toString();

        assertEquals(200, call("GET", "/api/users/" + id, null).status());

        ApiRouter.Response upd = call("PUT", "/api/users/" + id, "{\"username\":\"Вера\"}");
        assertEquals(200, upd.status());
        assertEquals("Вера", json(upd).get("username"));

        assertEquals(400, call("PUT", "/api/users/" + id, "{\"email\":\"other@x.ru\"}").status());

        assertEquals(204, call("DELETE", "/api/users/" + id, null).status());
        assertEquals(404, call("GET", "/api/users/" + id, null).status());
    }

    @Test
    void duplicateEmailGives400() {
        call("POST", "/api/users", "{\"email\":\"v@x.ru\",\"password\":\"password123\"}");
        assertEquals(400, call("POST", "/api/users", "{\"email\":\"v@x.ru\",\"password\":\"password123\"}").status());
    }

    // ---------- телеметрия ----------

    @Test
    void telemetryGives202() {
        UUID id = existingDevice();
        ApiRouter.Response r = call("POST", "/api/telemetry",
                "{\"deviceId\":\"" + id + "\",\"time\":\"2026-11-06T12:00:00Z\",\"telemetryType\":1,\"value\":22.5}");
        assertEquals(202, r.status());
        assertEquals(1, telemetry.rows.size());
        assertEquals(Instant.parse("2026-11-06T12:00:00Z"), devices.rows.get(id).getLastDataTime());
    }

    @Test
    void strictRuleGives400AndIsNotSaved() throws Exception {
        UUID id = existingDevice();
        RuleEntity rule = rules.add(id, 2, RuleOperator.LE, 100.0, true);

        ApiRouter.Response r = call("POST", "/api/telemetry",
                "{\"deviceId\":\"" + id + "\",\"time\":\"2026-11-06T12:00:00Z\",\"telemetryType\":2,\"value\":120}");

        assertEquals(400, r.status());
        assertEquals("значение 120.0 нарушает правило: должно быть ≤ 100.0", json(r).get("rule_" + rule.id()));
        assertTrue(telemetry.rows.isEmpty());
    }

    @Test
    void softRuleGives202AndIsSaved() {
        UUID id = existingDevice();
        rules.add(id, 3, RuleOperator.LE, 30.0, false);
        assertEquals(202, call("POST", "/api/telemetry",
                "{\"deviceId\":\"" + id + "\",\"time\":\"2026-11-06T12:00:00Z\",\"telemetryType\":3,\"value\":35}")
                .status());
        assertEquals(1, telemetry.rows.size());
    }

    @Test
    void createUserSendsRegistrationEvent() {
        call("POST", "/api/users", "{\"email\":\"v@x.ru\",\"password\":\"password123\"}");
        assertEquals(1, notifications.usersRegistered.size());
        assertEquals("v@x.ru", notifications.usersRegistered.get(0).email());
    }

    @Test
    void passwordIsHiddenInLogs() {
        assertEquals("{\"email\":\"a@b\",\"password\":\"***\"}",
                ApiRouter.hidePassword("{\"email\":\"a@b\",\"password\":\"se\\\"cret\"}"));
        assertEquals("{\"password\" : \"***\"}", ApiRouter.hidePassword("{\"password\" : \"x\"}"));
    }

    @Test
    void telemetryForUnknownDeviceGives404() {
        assertEquals(404, call("POST", "/api/telemetry",
                "{\"deviceId\":\"" + UUID.randomUUID() + "\",\"time\":\"2026-11-06T12:00:00Z\",\"telemetryType\":1,\"value\":1}")
                .status());
    }

    // ---------- уведомления ----------

    @Test
    void getAndPutSettings() throws Exception {
        ApiRouter.Response get = call("GET", "/api/notifications/settings", null);
        assertEquals(200, get.status());
        assertEquals(20.0, json(get).get("norm"));

        ApiRouter.Response put = call("PUT", "/api/notifications/settings", "{\"norm\":30.0,\"deviation\":2.5}");
        assertEquals(200, put.status());
        assertEquals(30.0, json(put).get("norm"));
        assertEquals(2.5, notifications.settings.deviation());
    }

    @Test
    void deviceStatus() throws Exception {
        UUID id = UUID.randomUUID();
        notifications.stateToReturn = DeviceState.OFF;
        ApiRouter.Response r = call("GET", "/api/notifications/devices/" + id + "/status", null);
        assertEquals(200, r.status());
        assertEquals("OFF", json(r).get("state"));
        assertEquals(id.toString(), json(r).get("deviceId"));
    }

    @Test
    void deviceStatusWithBadUuidGives400() {
        assertEquals(400, call("GET", "/api/notifications/devices/xyz/status", null).status());
    }

    @Test
    void notificationServiceDownGives501() {
        notifications.failWith = new UnsupportedOperationException("сервис уведомлений недоступен");
        assertEquals(501, call("GET", "/api/notifications/settings", null).status());
    }

    // ---------- непредвиденные ошибки ----------

    @Test
    void databaseErrorGives500WithoutDetails() throws Exception {
        FakeDeviceRepository broken = new FakeDeviceRepository() {
            @Override
            public Optional<DeviceEntity> findById(UUID id) {
                throw new DatabaseException("connection refused", null);
            }
        };
        ApiRouter.Response r = routerWith(broken).handle("GET", "/api/devices/" + UUID.randomUUID(), new byte[0]);
        assertEquals(500, r.status());
        assertFalse(json(r).get("error").toString().contains("connection refused"));
    }
}