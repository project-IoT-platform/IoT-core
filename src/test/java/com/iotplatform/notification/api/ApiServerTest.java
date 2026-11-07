package com.iotplatform.notification.api;

import com.iotplatform.notification.fake.Fakes;
import com.iotplatform.notification.model.Device;
import com.iotplatform.notification.model.Notification;
import com.iotplatform.notification.model.NotificationType;
import com.iotplatform.notification.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Сервер на случайном порту, сервис на фейках: проверяем приём событий от IoT Core. */
class ApiServerTest {
    private final HttpClient http = HttpClient.newHttpClient();
    private Fakes.World w;
    private ApiServer server;
    private User owner;
    private Device device;

    @BeforeEach
    void setUp() throws Exception {
        w = new Fakes.World();
        owner = w.platform.addUser(false);
        device = w.platform.addDevice(owner.id());
        server = new ApiServer(0, w.service);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void batteryLowEvent() throws Exception {
        HttpResponse<String> r = post("/events/battery-low",
                "{\"deviceId\":\"" + device.id() + "\",\"batteryLevel\":12,\"belowBy\":8,\"lowerBound\":20}");

        assertEquals(202, r.statusCode());
        Notification n = w.channel.sent.get(0);
        assertEquals(NotificationType.LOW_BATTERY, n.type());
        assertEquals("8%", n.details().get("Ниже границы на"));
    }

    @Test
    void telemetryAlertEvent() throws Exception {
        HttpResponse<String> r = post("/events/telemetry-alert",
                "{\"deviceId\":\"" + device.id() + "\",\"time\":\"2026-11-07T09:00:00Z\",\"telemetryType\":1,"
                        + "\"value\":30.0,\"deviation\":5.0,\"lowerBound\":null,\"upperBound\":25.0}");

        assertEquals(202, r.statusCode());
        Notification n = w.channel.sent.get(0);
        assertEquals("Телеметрия больше нормы на 5", n.details().get("Отклонение"));
        assertEquals("25", n.details().get("Верхняя граница нормы"));
        assertFalse(n.details().containsKey("Нижняя граница нормы"));
    }

    @Test
    void userRegisteredEvent() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(202, post("/events/user-registered",
                "{\"userId\":\"" + id + "\",\"username\":\"vera\",\"email\":\"v@x.ru\"}").statusCode());
        assertEquals(NotificationType.USER_REGISTERED, w.channel.sent.get(0).type());
        assertEquals(id, w.channel.sent.get(0).userId());
    }

    @Test
    void deviceRegisteredEventStillWorks() throws Exception {
        assertEquals(202, post("/events/device-registered",
                "{\"deviceId\":\"" + device.id() + "\",\"userId\":\"" + owner.id() + "\",\"name\":\"n\",\"state\":\"ON\"}")
                .statusCode());
        assertEquals(NotificationType.DEVICE_REGISTERED, w.channel.sent.get(0).type());
    }

    @Test
    void missingFieldsGive400() throws Exception {
        assertEquals(400, post("/events/battery-low", "{\"batteryLevel\":12}").statusCode());
        assertEquals(400, post("/events/user-registered", "{}").statusCode());
        assertEquals(400, post("/events/telemetry-alert", "{\"value\":1}").statusCode());
        assertEquals(400, post("/events/battery-low",
                "{\"deviceId\":\"" + device.id() + "\",\"batteryLevel\":\"много\"}").statusCode());
        assertTrue(w.channel.sent.isEmpty());
    }

    @Test
    void brokenJsonGives400() throws Exception {
        assertEquals(400, post("/events/battery-low", "{oops").statusCode());
    }

    @Test
    void oldEventPathsAreGone() throws Exception {
        // проверки теперь делает IoT Core, старые адреса не нужны
        assertEquals(404, post("/events/battery", "{}").statusCode());
        assertEquals(404, post("/events/telemetry", "{}").statusCode());
    }
}