package com.iotplatform.iotcore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iotplatform.iotcore.check.BatteryCheckResult;
import com.iotplatform.iotcore.check.TelemetryCheckResult;
import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.http.Json;
import com.iotplatform.iotcore.model.UserRole;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.DeviceState;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяем NotificationService против маленького HTTP-сервера из JDK,
 * который притворяется сервисом уведомлений. Сеть наружу не нужна.
 */
class NotificationServiceTest {

    record Received(String method, String path, String body) {}
    record Answer(int status, String body) {}

    private HttpServer server;
    private final BlockingQueue<Received> received = new LinkedBlockingQueue<>();
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();   // ключ: "METHOD /path"
    private NotificationService service;

    @BeforeEach
    void startFakeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            received.add(new Received(exchange.getRequestMethod(), path, body));
            Answer a = answers.getOrDefault(exchange.getRequestMethod() + " " + path, new Answer(200, "{}"));
            byte[] bytes = a.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(a.status(), bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        // слэш в конце должен отрезаться конструктором
        service = new NotificationService("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void stopFakeServer() {
        server.stop(0);
    }

    private Received next() throws InterruptedException {
        Received r = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(r, "сервис уведомлений не получил запрос");
        return r;
    }

    // ---------- события ----------

    @Test
    void deviceRegisteredEventIsPostedAsJson() throws Exception {
        UUID id = UUID.randomUUID();
        service.notifyDeviceRegistered(new DeviceDto(id, null, "Термометр", "Acme", 5, DeviceState.ON, null, null));

        Received r = next();
        assertEquals("POST", r.method());
        assertEquals("/events/device-registered", r.path());
        assertTrue(r.body().contains(id.toString()));
        assertTrue(r.body().contains("\"batteryLevel\":5"));
    }

    @Test
    void userRegisteredEventHasIdAndEmail() throws Exception {
        UUID id = UUID.randomUUID();
        service.notifyUserRegistered(new UserResponse(id, "vera", "v@x.ru", null, null, UserRole.GUEST));
        Received r = next();
        assertEquals("/events/user-registered", r.path());
        Map<String, Object> body = Json.MAPPER.readValue(r.body(), new TypeReference<Map<String, Object>>() {});
        assertEquals(id.toString(), body.get("userId"));
        assertEquals("v@x.ru", body.get("email"));
        assertEquals("vera", body.get("username"));
    }

    @Test
    void batteryLowEventCarriesCheckResult() throws Exception {
        UUID id = UUID.randomUUID();
        service.batteryLow(id, new BatteryCheckResult(true, 12, 8, 20));
        Received r = next();
        assertEquals("/events/battery-low", r.path());
        Map<String, Object> body = Json.MAPPER.readValue(r.body(), new TypeReference<Map<String, Object>>() {});
        assertEquals(id.toString(), body.get("deviceId"));
        assertEquals(12, body.get("batteryLevel"));
        assertEquals(8, body.get("belowBy"));
        assertEquals(20, body.get("lowerBound"));
    }

    @Test
    void telemetryAlertEventCarriesCheckResultAndIsoTime() throws Exception {
        UUID id = UUID.randomUUID();
        service.telemetryAlert(new TelemetryDto(id, Instant.parse("2026-11-06T12:00:00Z"), (short) 1, 15.0),
                new TelemetryCheckResult(true, 15.0, -3.0, 18.0, null));
        Received r = next();
        assertEquals("/events/telemetry-alert", r.path());
        Map<String, Object> body = Json.MAPPER.readValue(r.body(), new TypeReference<Map<String, Object>>() {});
        assertEquals("2026-11-06T12:00:00Z", body.get("time"));
        assertEquals(1, body.get("telemetryType"));
        assertEquals(15.0, body.get("value"));
        assertEquals(-3.0, body.get("deviation"));
        assertEquals(18.0, body.get("lowerBound"));
        assertNull(body.get("upperBound"));
        assertTrue(body.containsKey("upperBound"));
    }

    @Test
    void eventDoesNotThrowWhenServiceIsDown() throws Exception {
        NotificationService down = new NotificationService("http://127.0.0.1:" + freePort());
        assertDoesNotThrow(() -> down.notifyDeviceRegistered(
                new DeviceDto(UUID.randomUUID(), null, "n", "m", 5, DeviceState.ON, null, null)));
    }

    @Test
    void eventDoesNotThrowWhenServiceAnswersError() {
        answers.put("POST /events/device-registered", new Answer(500, "oops"));
        assertDoesNotThrow(() -> service.notifyDeviceRegistered(
                new DeviceDto(UUID.randomUUID(), null, "n", "m", 5, DeviceState.ON, null, null)));
    }

    // ---------- статус устройства ----------

    @Test
    void sendDeviceStatusReturnsState() throws Exception {
        UUID id = UUID.randomUUID();
        answers.put("POST /devices/" + id + "/status", new Answer(200, "{\"state\":\"OFF\"}"));

        assertEquals(DeviceState.OFF, service.sendDeviceStatus(id));
        assertEquals("POST", next().method());
    }

    @Test
    void sendDeviceStatus404MeansDeviceNotFound() {
        UUID id = UUID.randomUUID();
        answers.put("POST /devices/" + id + "/status", new Answer(404, "{}"));
        assertThrows(DeviceNotFoundException.class, () -> service.sendDeviceStatus(id));
    }

    @Test
    void sendDeviceStatusOtherErrorMeansUnavailable() {
        UUID id = UUID.randomUUID();
        answers.put("POST /devices/" + id + "/status", new Answer(500, "{}"));
        assertThrows(UnsupportedOperationException.class, () -> service.sendDeviceStatus(id));
    }

    @Test
    void sendDeviceStatusWithoutStateMeansUnavailable() {
        UUID id = UUID.randomUUID();
        answers.put("POST /devices/" + id + "/status", new Answer(200, "{}"));
        assertThrows(UnsupportedOperationException.class, () -> service.sendDeviceStatus(id));
    }

    @Test
    void sendDeviceStatusWithUnknownStateMeansUnavailable() {
        UUID id = UUID.randomUUID();
        answers.put("POST /devices/" + id + "/status", new Answer(200, "{\"state\":\"BROKEN\"}"));
        assertThrows(UnsupportedOperationException.class, () -> service.sendDeviceStatus(id));
    }

    @Test
    void sendDeviceStatusWithGarbageMeansUnavailable() {
        UUID id = UUID.randomUUID();
        answers.put("POST /devices/" + id + "/status", new Answer(200, "not json"));
        assertThrows(UnsupportedOperationException.class, () -> service.sendDeviceStatus(id));
    }

    @Test
    void requestWhenServiceIsDownMeansUnavailable() throws Exception {
        NotificationService down = new NotificationService("http://127.0.0.1:" + freePort());
        assertThrows(UnsupportedOperationException.class, () -> down.sendDeviceStatus(UUID.randomUUID()));
    }

    // ---------- настройки ----------

    @Test
    void getDefaultsParsesSettings() {
        answers.put("GET /settings", new Answer(200, "{\"norm\":20.5,\"deviation\":3.0}"));
        assertEquals(new NotificationService.Settings(20.5, 3.0), service.getDefaults());
    }

    @Test
    void getDefaultsErrorMeansUnavailable() {
        answers.put("GET /settings", new Answer(503, ""));
        assertThrows(UnsupportedOperationException.class, () -> service.getDefaults());
    }

    @Test
    void updateDefaultsSendsJsonAndReturnsAnswer() throws Exception {
        answers.put("PUT /settings", new Answer(200, "{\"norm\":25.0,\"deviation\":2.0}"));

        NotificationService.Settings result = service.updateDefaults(new NotificationService.Settings(25.0, 2.0));

        assertEquals(new NotificationService.Settings(25.0, 2.0), result);
        Received r = next();
        assertEquals("PUT", r.method());
        assertTrue(r.body().contains("\"norm\":25.0"), r.body());
    }

    @Test
    void updateDefaults400BecomesValidationException() {
        answers.put("PUT /settings", new Answer(400, "{\"deviation\":\"должно быть больше 0\"}"));
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.updateDefaults(new NotificationService.Settings(25.0, -1.0)));
        assertEquals("должно быть больше 0", e.getErrors().get("deviation"));
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}