package com.iotplatform.notification.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotplatform.notification.exception.NotFoundException;
import com.iotplatform.notification.exception.ValidationException;
import com.iotplatform.notification.model.Device;
import com.iotplatform.notification.model.Reading;
import com.iotplatform.notification.model.Settings;
import com.iotplatform.notification.service.NotificationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP-вход сервиса уведомлений (встроенный сервер из JDK, без библиотек). Принимает запросы от IoT Core:
 *   POST /events/device-registered   тело: устройство (JSON)                                   -> 202
 *   POST /events/user-registered     тело: {userId, username, email}                           -> 202
 *   POST /events/battery-low         тело: {deviceId, batteryLevel, belowBy, lowerBound}       -> 202
 *   POST /events/telemetry-alert     тело: {deviceId, time, telemetryType, value, deviation,
 *                                           lowerBound, upperBound}                            -> 202
 *   POST /devices/{id}/status        -> 200 {"deviceId":..,"state":"ON"} или 404
 *   GET  /settings                   -> 200 {"norm":..,"deviation":..}
 *   PUT  /settings                   тело {"norm":..,"deviation":..} -> 200 или 400
 */
public class ApiServer {
    private static final System.Logger LOG = System.getLogger(ApiServer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NotificationService service;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(8); // можно поменять

    /** port = 0: выбрать свободный порт (для тестов). */
    public ApiServer(int port, NotificationService service) throws IOException {
        this.service = service;
        this.server = HttpServer.create(new InetSocketAddress(port), 0); // 8080  по умолчанию
        this.server.createContext("/", this::handle);
        this.server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange ex) throws IOException {
        long start = System.nanoTime();
        String request = ex.getRequestMethod() + " " + ex.getRequestURI();
        try {
            String path = ex.getRequestURI().getPath();
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            byte[] raw = ex.getRequestBody().readAllBytes();
            route(ex, ex.getRequestMethod(), path, raw);
        } catch (JsonProcessingException e) {
            LOG.log(System.Logger.Level.WARNING, request + ": некорректный JSON: " + e.getOriginalMessage());
            send(ex, 400, Map.of("error", "Некорректный JSON"));
        } catch (ValidationException e) {
            LOG.log(System.Logger.Level.WARNING, request + ": " + e.getErrors());
            send(ex, 400, e.getErrors());
        } catch (NotFoundException e) {
            send(ex, 404, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | DateTimeException e) {
            LOG.log(System.Logger.Level.WARNING, request + ": " + e.getMessage());
            send(ex, 400, Map.of("error", String.valueOf(e.getMessage())));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Внутренняя ошибка при " + request, e);
            send(ex, 500, Map.of("error", "Внутренняя ошибка сервера"));
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            int status = ex.getResponseCode();
            System.Logger.Level level = status >= 500 ? System.Logger.Level.ERROR
                    : status >= 400 ? System.Logger.Level.WARNING : System.Logger.Level.INFO;
            LOG.log(level, request + " -> " + status + " (" + ms + " мс)");
            ex.close();
        }
    }

    private void route(HttpExchange ex, String method, String path, byte[] raw) throws IOException {
        if (method.equals("POST") && path.equals("/events/device-registered")) {
            service.notifyRegistered(device(raw));
            send(ex, 202, null);
        } else if (method.equals("POST") && path.equals("/events/user-registered")) {
            JsonNode n = MAPPER.readTree(raw);
            UUID userId = uuid(n, "userId");
            if (userId == null) throw new IllegalArgumentException("userId обязателен");
            service.notifyUserRegistered(userId, text(n, "username"));
            send(ex, 202, null);
        } else if (method.equals("POST") && path.equals("/events/battery-low")) {
            JsonNode n = MAPPER.readTree(raw);
            UUID deviceId = uuid(n, "deviceId");
            Double level = number(n, "batteryLevel");
            if (deviceId == null || level == null) throw new IllegalArgumentException("deviceId и batteryLevel обязательны");
            service.batteryLow(deviceId, level.intValue(), integer(n, "belowBy"), integer(n, "lowerBound"));
            send(ex, 202, null);
        } else if (method.equals("POST") && path.equals("/events/telemetry-alert")) {
            JsonNode n = MAPPER.readTree(raw);
            service.telemetryAlert(reading(n), number(n, "deviation"), number(n, "lowerBound"), number(n, "upperBound"));
            send(ex, 202, null);
        } else if (method.equals("POST") && path.matches("/devices/[^/]+/status")) {
            UUID id = UUID.fromString(path.split("/")[2]);
            boolean on = service.sendStatus(id);
            send(ex, 200, Map.of("deviceId", id.toString(), "state", on ? "ON" : "OFF"));
        } else if (method.equals("GET") && path.equals("/settings")) {
            send(ex, 200, settingsJson(service.getDefaults()));
        } else if (method.equals("PUT") && path.equals("/settings")) {
            send(ex, 200, settingsJson(service.updateDefaults(settings(raw))));
        } else {
            send(ex, 404, Map.of("error", "Адрес не найден"));
        }
    }

    // ---- разбор JSON вручную: так проще и не нужны лишние модули Jackson ----

    private static Device device(byte[] raw) throws IOException {
        JsonNode n = MAPPER.readTree(raw);
        UUID id = uuid(n, "deviceId");
        if (id == null) throw new IllegalArgumentException("deviceId обязателен");
        String last = text(n, "lastDataTime");
        return new Device(id, uuid(n, "userId"), text(n, "name"), text(n, "manufacturer"),
                n.hasNonNull("batteryLevel") ? Integer.valueOf(n.get("batteryLevel").asInt()) : null,
                "ON".equalsIgnoreCase(text(n, "state")), text(n, "geolocation"),
                last == null ? null : Instant.parse(last));
    }

    private static Reading reading(JsonNode n) {
        UUID id = uuid(n, "deviceId");
        if (id == null) throw new IllegalArgumentException("deviceId обязателен");
        String time = text(n, "time");
        return new Reading(id, time == null ? Instant.now() : Instant.parse(time),
                n.hasNonNull("telemetryType") ? Short.valueOf(n.get("telemetryType").shortValue()) : null,
                number(n, "value"));
    }

    private static Settings settings(byte[] raw) throws IOException {
        JsonNode n = MAPPER.readTree(raw);
        return new Settings(number(n, "norm"), number(n, "deviation"));
    }

    private static Map<String, Double> settingsJson(Settings s) {
        return Map.of("norm", s.norm(), "deviation", s.deviation());
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private static UUID uuid(JsonNode n, String field) {
        String t = text(n, field);
        return t == null ? null : UUID.fromString(t);
    }

    private static Double number(JsonNode n, String field) {
        if (!n.hasNonNull(field)) return null;
        if (!n.get(field).isNumber()) throw new ValidationException(Map.of(field, "должно быть числом"));
        return n.get(field).asDouble();
    }

    private static Integer integer(JsonNode n, String field) {
        Double d = number(n, field);
        return d == null ? null : d.intValue();
    }

    private static void send(HttpExchange ex, int status, Object body) throws IOException {
        if (body == null) {
            ex.sendResponseHeaders(status, -1);
            return;
        }
        byte[] out = MAPPER.writeValueAsBytes(body);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, out.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }
}