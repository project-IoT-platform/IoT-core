package com.iotplatform.iotcore.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iotplatform.iotcore.controller.DeviceController;
import com.iotplatform.iotcore.controller.NotificationController;
import com.iotplatform.iotcore.controller.RuleController;
import com.iotplatform.iotcore.controller.TelemetryController;
import com.iotplatform.iotcore.controller.UserController;
import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.dto.RuleDto;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ForbiddenException;
import com.iotplatform.iotcore.exception.RuleNotFoundException;
import com.iotplatform.iotcore.exception.UserNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.repository.TelemetryRepository;
import com.iotplatform.iotcore.service.NotificationService;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ApiRouter {
    private static final System.Logger LOG = System.getLogger(ApiRouter.class.getName());

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    record Response(int status, byte[] body) {}

    private final DeviceController deviceController;
    private final UserController userController;
    private final TelemetryController telemetryController;
    private final NotificationController notificationController;
    private final RuleController ruleController;

    // клиент сервиса уведомлений для проксирования лимитов (адрес тот же, что в Main)
    private final NotificationService limitsClient = new NotificationService(
            System.getenv().getOrDefault("NOTIFICATION_URL", "http://localhost:8081"));

    ApiRouter(DeviceController deviceController,
              UserController userController,
              TelemetryController telemetryController,
              NotificationController notificationController,
              RuleController ruleController) {
        this.deviceController = deviceController;
        this.userController = userController;
        this.telemetryController = telemetryController;
        this.notificationController = notificationController;
        this.ruleController = ruleController;
    }

    Response handle(String method, String uri, byte[] body) {
        long start = System.nanoTime();
        Response response = route(method, uri, body);
        long ms = (System.nanoTime() - start) / 1_000_000;
        String line = method + " " + uri + " -> " + response.status() + " (" + ms + " мс)";
        if (response.status() >= 500) {
            LOG.log(System.Logger.Level.ERROR, line + " " + text(response.body()));
        } else if (response.status() >= 400) {
            LOG.log(System.Logger.Level.WARNING, line + " " + text(response.body())
                    + (body != null && body.length > 0 ? " | тело запроса: " + hidePassword(text(body)) : ""));
        } else {
            LOG.log(System.Logger.Level.INFO, line);
        }
        return response;
    }

    private static String text(byte[] bytes) {
        if (bytes == null) return "";
        String s = new String(bytes, StandardCharsets.UTF_8);
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }

    static String hidePassword(String json) {
        return json.replaceAll("(\"password\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"", "$1\"***\"");
    }

    private Response route(String method, String uri, byte[] body) {
        try {
            return dispatch(method, uri, body);
        } catch (JsonProcessingException e) {
            return json(400, Map.of("error", "Некорректный JSON: " + e.getOriginalMessage()));
        } catch (ValidationException e) {
            return json(400, e.getErrors());
        } catch (DeviceNotFoundException | UserNotFoundException | RuleNotFoundException e) {
            return json(404, Map.of("error", String.valueOf(e.getMessage())));
        } catch (ForbiddenException e) {   // НОВОЕ: список пользователей не для админа
            return json(403, Map.of("error", String.valueOf(e.getMessage())));
        } catch (UnsupportedOperationException e) {
            return json(501, Map.of("error", "Ещё не реализовано: " + e.getMessage()));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Внутренняя ошибка при " + method + " " + uri, e);
            return json(500, Map.of("error", "Внутренняя ошибка сервера"));
        }
    }

    private Response dispatch(String method, String uri, byte[] body) throws IOException {
        String path;
        try {
            path = new URI(uri).getPath();
        } catch (URISyntaxException e) {
            return json(400, Map.of("error", "Некорректный адрес"));
        }
        if (path == null) return notFound();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        String[] parts = path.split("/");
        if (parts.length >= 3 && parts[0].isEmpty() && parts[1].equals("api") && parts[2].equals("notifications")) {
            return notifications(method, parts, body);
        }
        // POST /api/login
        if (parts.length == 3 && parts[0].isEmpty() && parts[1].equals("api") && parts[2].equals("login")) {
            return login(method, body);
        }
        // POST /api/users/{id}/confirm
        if (parts.length == 5 && parts[0].isEmpty() && parts[1].equals("api")
                && parts[2].equals("users") && parts[4].equals("confirm")) {
            return confirmUser(method, parts[3]);
        }
        // НОВОЕ: GET/PUT /api/users/{id}/notification-preferences (проксируется в сервис уведомлений)
        if (parts.length == 5 && parts[0].isEmpty() && parts[1].equals("api")
                && parts[2].equals("users") && parts[4].equals("notification-preferences")) {
            return notificationPrefs(method, parts[3], body);
        }
        // НОВОЕ: GET /api/devices/{id}/telemetry[/latest|/average]
        if ((parts.length == 5 || parts.length == 6) && parts[0].isEmpty() && parts[1].equals("api")
                && parts[2].equals("devices") && parts[4].equals("telemetry")) {
            return deviceTelemetry(method, parts, uri);
        }
        // GET/PUT /api/devices/{id}/limits (проксируется в сервис уведомлений)
        if (parts.length == 5 && parts[0].isEmpty() && parts[1].equals("api")
                && parts[2].equals("devices") && parts[4].equals("limits")) {
            return deviceLimits(method, parts[3], body);
        }

        // /api/devices/{id}/rules и /api/devices/{id}/rules/{ruleId}
        // ВАЖНО: до общей проверки parts.length, иначе 5-сегментный путь уйдёт в notFound()
        if (parts.length >= 5 && parts[0].isEmpty() && parts[1].equals("api")
                && parts[2].equals("devices") && parts[4].equals("rules")) {
            return deviceRules(method, parts);
        }
        // /api/rules и /api/rules/{id}
        if (parts.length >= 3 && parts[0].isEmpty() && parts[1].equals("api") && parts[2].equals("rules")) {
            return rules(method, parts, body);
        }

        if (parts.length < 3 || parts.length > 4 || !parts[0].isEmpty() || !parts[1].equals("api")) {
            return notFound();
        }
        String resource = parts[2];
        if (!resource.equals("devices") && !resource.equals("users") && !resource.equals("telemetry")) {
            return notFound();
        }

        // GET /api/devices?userId={uuid}&limit=10&offset=0&search={часть названия}
        if (method.equals("GET") && parts.length == 3 && resource.equals("devices")) {
            return devicesByUser(uri);
        }
        // НОВОЕ: GET /api/users?requesterId={uuid}&limit=10&offset=0&search={часть почты} (только админ)
        if (method.equals("GET") && parts.length == 3 && resource.equals("users")) {
            return usersPage(uri);
        }

        UUID id = null;
        if (parts.length == 4) {
            try {
                id = UUID.fromString(parts[3]);
            } catch (IllegalArgumentException e) {
                return json(400, Map.of("id", "некорректный UUID в адресе"));
            }
        }

        if (resource.equals("devices")) return devices(method, id, body);
        if (resource.equals("users")) return users(method, id, body);
        return telemetry(method, id, body);
    }

    private Response login(String method, byte[] body) throws IOException {
        if (!method.equals("POST")) return methodNotAllowed();
        UserDto dto = read(body, UserDto.class);
        return userController.login(dto.getEmail(), dto.getPassword())
                .map(user -> json(200, user))
                .orElseGet(() -> json(401, Map.of("error", "Неверный email или пароль")));
    }

    private Response confirmUser(String method, String rawId) {
        if (!method.equals("POST")) return methodNotAllowed();
        UUID id;
        try {
            id = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            return json(400, Map.of("id", "некорректный UUID в адресе"));
        }
        return json(200, userController.confirm(id));   // нет такого пользователя -> 404
    }

    private static String first(Map<String, List<String>> params, String name) {
        List<String> values = params.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private Response devicesByUser(String uri) {
        Map<String, List<String>> params = new QueryStringDecoder(uri).parameters();

        String rawUser = first(params, "userId");
        if (rawUser == null || rawUser.isBlank()) {
            return json(400, Map.of("error", "userId обязателен"));
        }
        UUID userId;
        try {
            userId = UUID.fromString(rawUser.trim());
        } catch (IllegalArgumentException e) {
            return json(400, Map.of("error", "некорректный UUID"));
        }

        int limit = DEFAULT_LIMIT;
        int offset = 0;
        try {
            String rawLimit = first(params, "limit");
            String rawOffset = first(params, "offset");
            if (rawLimit != null && !rawLimit.isBlank()) limit = Integer.parseInt(rawLimit.trim());
            if (rawOffset != null && !rawOffset.isBlank()) offset = Integer.parseInt(rawOffset.trim());
        } catch (NumberFormatException e) {
            return json(400, Map.of("error", "limit и offset должны быть целыми числами"));
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            return json(400, Map.of("error", "limit должен быть от 1 до " + MAX_LIMIT));
        }
        if (offset < 0) {
            return json(400, Map.of("error", "offset не может быть отрицательным"));
        }

        // ИЗМЕНЕНО: search теперь часть названия устройства (раньше был UUID). С search отдаём все совпадения без пагинации
        String search = first(params, "search");
        if (search != null) {
            search = search.trim();
            if (search.isEmpty()) search = null;
            else if (search.length() > 100) return json(400, Map.of("error", "search не длиннее 100 символов"));
        }
        return json(200, deviceController.getByUserId(userId, search, limit, offset));
    }

    // ---- НОВОЕ: список пользователей (только админ) ----

    private Response usersPage(String uri) {
        Map<String, List<String>> params = new QueryStringDecoder(uri).parameters();
        String rawRequester = first(params, "requesterId");
        if (rawRequester == null || rawRequester.isBlank()) {
            return json(400, Map.of("error", "requesterId обязателен"));
        }
        UUID requesterId;
        try {
            requesterId = UUID.fromString(rawRequester.trim());
        } catch (IllegalArgumentException e) {
            return json(400, Map.of("error", "некорректный UUID"));
        }
        int limit = intParam(params, "limit", DEFAULT_LIMIT);
        int offset = intParam(params, "offset", 0);
        if (limit < 1 || limit > MAX_LIMIT) {
            return json(400, Map.of("error", "limit должен быть от 1 до " + MAX_LIMIT));
        }
        if (offset < 0) {
            return json(400, Map.of("error", "offset не может быть отрицательным"));
        }
        String search = first(params, "search");
        if (search != null) {
            search = search.trim();
            if (search.isEmpty()) search = null;
            else if (search.length() > 100) return json(400, Map.of("error", "search не длиннее 100 символов"));
        }
        return json(200, userController.list(requesterId, search, limit, offset));
    }

    private static int intParam(Map<String, List<String>> params, String name, int fallback) {
        String raw = first(params, name);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(Map.of(name, "должно быть целым числом"));
        }
    }

    // ---- НОВОЕ: настройки частоты уведомлений ----

    private Response notificationPrefs(String method, String rawId, byte[] body) throws IOException {
        UUID id = uuid(rawId);
        if (method.equals("GET")) {
            return json(200, limitsClient.getDeliveryPrefs(id));
        }
        if (method.equals("PUT")) {
            return json(200, limitsClient.updateDeliveryPrefs(id,
                    read(body, NotificationService.DeliveryPrefs.class)));
        }
        return methodNotAllowed();
    }

    // ---- НОВОЕ: телеметрия устройства для плиток и графика ----
    //   GET /api/devices/{id}/telemetry/latest                          -> [{telemetryType, value, time}]
    //   GET /api/devices/{id}/telemetry?type=1&limit=60[&since=ISO]     -> [{time, value}] по возрастанию времени
    //   GET /api/devices/{id}/telemetry/average?type=1&since=ISO        -> {telemetryType, since, average, count}

    private static final int DEFAULT_POINTS = 60;
    private static final int MAX_POINTS = 1000;

    private Response deviceTelemetry(String method, String[] parts, String uri) {
        if (!method.equals("GET")) return methodNotAllowed();
        UUID deviceId = uuid(parts[3]);
        Map<String, List<String>> params = new QueryStringDecoder(uri).parameters();

        if (parts.length == 6 && parts[5].equals("latest")) {
            return json(200, telemetryController.latest(deviceId));
        }
        if (parts.length == 6 && parts[5].equals("average")) {
            short type = telemetryType(params);
            Instant since = instantParam(params, "since", true);
            TelemetryRepository.Average avg = telemetryController.average(deviceId, type, since);
            Map<String, Object> out = new LinkedHashMap<>();   // LinkedHashMap: Map.of не принимает null в average
            out.put("telemetryType", type);
            out.put("since", since);
            out.put("average", avg.average());
            out.put("count", avg.count());
            return json(200, out);
        }
        if (parts.length == 5) {
            short type = telemetryType(params);
            Instant since = instantParam(params, "since", false);
            int limit = intParam(params, "limit", DEFAULT_POINTS);
            if (limit < 1 || limit > MAX_POINTS) {
                return json(400, Map.of("error", "limit должен быть от 1 до " + MAX_POINTS));
            }
            return json(200, telemetryController.series(deviceId, type, since, limit));
        }
        return notFound();
    }

    private static short telemetryType(Map<String, List<String>> params) {
        String raw = first(params, "type");
        if (raw == null || raw.isBlank()) throw new ValidationException(Map.of("type", "обязательное поле"));
        try {
            return Short.parseShort(raw.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(Map.of("type", "должно быть целым числом"));
        }
    }

    private static Instant instantParam(Map<String, List<String>> params, String name, boolean required) {
        String raw = first(params, name);
        if (raw == null || raw.isBlank()) {
            if (required) throw new ValidationException(Map.of(name, "обязательное поле"));
            return null;
        }
        try {
            return Instant.parse(raw.trim());   // ISO-8601 в UTC, например 2026-11-21T10:00:00.000Z
        } catch (DateTimeParseException e) {
            throw new ValidationException(Map.of(name, "ожидается время в формате ISO-8601, например 2026-11-21T10:00:00Z"));
        }
    }

    private Response deviceLimits(String method, String rawId, byte[] body) throws IOException {
        UUID id = uuid(rawId);
        if (method.equals("GET")) {
            return json(200, limitsClient.getDeviceLimits(id));
        }
        if (method.equals("PUT")) {
            return json(200, limitsClient.updateDeviceLimits(id,
                    read(body, NotificationService.DeviceLimits.class)));
        }
        return methodNotAllowed();
    }

    private Response devices(String method, UUID id, byte[] body) throws IOException {
        if (method.equals("GET") && id != null) {
            return json(200, deviceController.getById(id));
        }
        if (method.equals("POST") && id == null) {
            deviceController.register(read(body, DeviceDto.class));
            return empty(202);
        }
        if (method.equals("PUT") && id != null) {
            return json(200, deviceController.update(id, read(body, DeviceDto.class)));
        }
        if (method.equals("DELETE") && id != null) {
            deviceController.delete(id);
            return empty(204);
        }
        return methodNotAllowed();
    }

    private Response users(String method, UUID id, byte[] body) throws IOException {
        if (method.equals("GET") && id != null) {
            return json(200, userController.getById(id));
        }
        if (method.equals("POST") && id == null) {
            return json(201, userController.create(read(body, UserDto.class)));
        }
        if (method.equals("PUT") && id != null) {
            return json(200, userController.update(id, read(body, UserDto.class)));
        }
        if (method.equals("DELETE") && id != null) {
            userController.delete(id);
            return empty(204);
        }
        return methodNotAllowed();
    }

    private Response telemetry(String method, UUID id, byte[] body) throws IOException {
        if (method.equals("POST") && id == null) {
            telemetryController.receive(read(body, TelemetryDto.class));
            return empty(202);
        }
        return methodNotAllowed();
    }

    private Response deviceRules(String method, String[] parts) {
        UUID deviceId = uuid(parts[3]);
        if (parts.length == 5) {
            if (method.equals("GET")) return json(200, ruleController.getByDevice(deviceId));
            return methodNotAllowed();
        }
        if (parts.length == 6) {
            UUID ruleId = uuid(parts[5]);
            if (method.equals("POST")) {
                ruleController.attach(deviceId, ruleId);
                return empty(204);
            }
            if (method.equals("DELETE")) {
                ruleController.detach(deviceId, ruleId);
                return empty(204);
            }
            return methodNotAllowed();
        }
        return notFound();
    }

    private Response rules(String method, String[] parts, byte[] body) throws IOException {
        if (parts.length == 3) {
            if (method.equals("POST")) return json(201, ruleController.create(read(body, RuleDto.class)));
            return methodNotAllowed();
        }
        if (parts.length == 4) {
            UUID id = uuid(parts[3]);
            if (method.equals("GET")) return json(200, ruleController.getById(id));
            if (method.equals("DELETE")) {
                ruleController.delete(id);
                return empty(204);
            }
            return methodNotAllowed();
        }
        return notFound();
    }

    private UUID uuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(Map.of("id", "некорректный UUID в адресе"));
        }
    }

    private Response notifications(String method, String[] parts, byte[] body) throws IOException {
        if (parts.length == 4 && parts[3].equals("settings")) {
            if (method.equals("GET")) return json(200, notificationController.getSettings());
            if (method.equals("PUT")) {
                return json(200, notificationController.updateSettings(
                        read(body, NotificationService.Settings.class)));
            }
            return methodNotAllowed();
        }
        if (parts.length == 6 && parts[3].equals("devices") && parts[5].equals("status")) {
            UUID id;
            try {
                id = UUID.fromString(parts[4]);
            } catch (IllegalArgumentException e) {
                return json(400, Map.of("id", "некорректный UUID в адресе"));
            }
            if (method.equals("GET")) return json(200, notificationController.sendDeviceStatus(id));
            return methodNotAllowed();
        }
        return notFound();
    }

    private <T> T read(byte[] body, Class<T> type) throws IOException {
        if (body == null || body.length == 0) {
            throw new ValidationException(Map.of("body", "пустое тело запроса"));
        }
        T value = Json.MAPPER.readValue(body, type);
        if (value == null) {
            throw new ValidationException(Map.of("body", "пустое тело запроса"));
        }
        return value;
    }

    private Response json(int status, Object value) {
        try {
            return new Response(status, Json.MAPPER.writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
            LOG.log(System.Logger.Level.ERROR, "Не удалось собрать JSON-ответ", e);
            return new Response(500, "{\"error\":\"Внутренняя ошибка сервера\"}".getBytes(StandardCharsets.UTF_8));
        }
    }

    private Response empty(int status) { return new Response(status, null); }
    private Response notFound() { return json(404, Map.of("error", "Адрес не найден")); }
    private Response methodNotAllowed() { return json(405, Map.of("error", "Метод не поддерживается для этого адреса")); }
}