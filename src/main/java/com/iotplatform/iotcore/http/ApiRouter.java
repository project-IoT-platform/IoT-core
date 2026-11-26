package com.iotplatform.iotcore.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iotplatform.iotcore.controller.DeviceController;
import com.iotplatform.iotcore.controller.TelemetryController;
import com.iotplatform.iotcore.controller.UserController;
import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.UserNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.service.NotificationService;
import com.iotplatform.iotcore.controller.NotificationController;

import io.netty.handler.codec.http.QueryStringDecoder;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import com.iotplatform.iotcore.db.DatabaseUtils;

import com.iotplatform.iotcore.controller.RuleController;
import com.iotplatform.iotcore.dto.RuleDto;
import com.iotplatform.iotcore.exception.RuleNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApiRouter {
    private static final Logger log = LoggerFactory.getLogger(ApiRouter.class);

    record Response(int status, byte[] body) {}
    private record PasswordForm(String email, String token, String currentPassword, String password) {}
    private record ManufacturerForm(UUID userId, String manufacturer) {}
    private record LoginRequest(String email, String password) {}

    private final DeviceController deviceController;
    private final UserController userController;
    private final TelemetryController telemetryController;
    private final NotificationController notificationController;
    private final RuleController ruleController;


    ApiRouter(DeviceController deviceController,
              UserController userController,
              TelemetryController telemetryController,
              NotificationController notificationController,
              RuleController ruleController) {
        this.ruleController = ruleController;
        this.deviceController = deviceController;
        this.userController = userController;
        this.telemetryController = telemetryController;
        this.notificationController = notificationController;
    }

    private InternalNotificationApi internal;
    private DatabaseUtils database;

    void setDatabase(DatabaseUtils database) { this.database = database; }

    private <T> T transaction(Supplier<T> work) {
        return database == null ? work.get() : database.transaction(work);
    }
    void setInternal(InternalNotificationApi internal) { this.internal = internal; }
    Response handle(String method, String uri, byte[] body, String authorization) {
        if (internal != null && (uri.equals("/internal/notifications") || uri.startsWith("/internal/notifications/")))
            return internal.handle(method, uri, body, authorization);
        return handle(method, uri, body);
    }

    Response handle(String method, String uri, byte[] body) {
        long start = System.nanoTime();
        Response response = route(method, uri, body);
        long ms = (System.nanoTime() - start) / 1_000_000;
        String line = method + " " + uri + " -> " + response.status() + " (" + ms + " мс)";
        if (response.status() >= 500) {
            log.error(line + " " + text(response.body()));
        } else if (response.status() >= 400) {
            log.warn(line + " " + text(response.body()));
        } else {
            log.info(line);
        }
        return response;
    }

    private static String text(byte[] bytes) {
        if (bytes == null) return "";
        String s = new String(bytes, StandardCharsets.UTF_8);
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }

    private Response route(String method, String uri, byte[] body) {
        try {
            return dispatch(method, uri, body);
        } catch (JsonProcessingException e) {
            log.debug("400 {} {}: invalid JSON", method, uri);
            return json(400, Map.of("error", "Некорректный JSON: " + e.getOriginalMessage()));
        } catch (ValidationException e) {
            log.debug("400 {} {}: {}", method, uri, e.getErrors());
            return json(400, e.getErrors());
        } catch (DeviceNotFoundException | UserNotFoundException | RuleNotFoundException e) {
            log.debug("404 {} {}: {}", method, uri, e.getMessage());
            return json(404, Map.of("error", String.valueOf(e.getMessage())));
        } catch (UnsupportedOperationException e) {
            return json(501, Map.of("error", "Ещё не реализовано: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Internal error while handling {} {}", method, uri, e);
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

        if (path.equals("/api/login")) {
            return method.equals("POST") ? login(body) : methodNotAllowed();
        }

        if (path.equals("/api/password/request") || path.equals("/api/password/reset")) {
            if (!method.equals("POST")) return methodNotAllowed();
            if (database == null) return notFound();
            var recovery = new com.iotplatform.iotcore.service.PasswordRecovery(database, userController::sendPasswordReset);
            var form = read(body, PasswordForm.class);
            if (path.endsWith("/request")) {
                recovery.request(form.email());
                return empty(202);
            }
            return recovery.reset(form.token(), form.password()) ? empty(204)
                    : json(400, Map.of("error", "Ссылка недействительна или просрочена"));
        }

        String[] parts = path.split("/");
        if (parts.length == 5 && parts[1].equals("api") && parts[2].equals("users")
                && parts[4].equals("password")) {
            if (!method.equals("PUT")) return methodNotAllowed();
            if (database == null) return notFound();
            var form = read(body, PasswordForm.class);
            var recovery = new com.iotplatform.iotcore.service.PasswordRecovery(database, userController::sendPasswordReset);
            return recovery.change(uuid(parts[3]), form.currentPassword(), form.password()) ? empty(204)
                    : json(400, Map.of("error", "Текущий пароль неверен"));
        }
        if (parts.length == 5 && parts[1].equals("api") && parts[2].equals("devices")
                && parts[4].equals("manufacturer")) {
            if (!method.equals("PUT")) return methodNotAllowed();
            if (database == null) return notFound();
            var edit = read(body, ManufacturerForm.class);
            if (edit.userId() == null || edit.manufacturer() == null || edit.manufacturer().strip().length() > 120)
                return json(400, Map.of("error", "Укажите производителя длиной до 120 символов"));
            UUID id = uuid(parts[3]);
            String name = edit.manufacturer().strip();
            int changed = database.update("UPDATE device SET manufacturer = ? WHERE id = ? AND user_id = ?",
                    name.isEmpty() ? null : name, id, edit.userId());
            return changed == 0 ? notFound() : json(200, deviceController.getById(id));
        }
        if (parts.length == 5 && parts[0].isEmpty() && parts[1].equals("api")
                && parts[2].equals("users") && parts[4].equals("notification-preferences")) {
            if (!method.equals("GET") && !method.equals("PUT")) return methodNotAllowed();
            UUID userId = uuid(parts[3]);
            return internal == null ? notFound() : internal.handleUserPreferences(method, userId, body);
        }
        if (parts.length == 5 && parts[0].isEmpty() && parts[1].equals("api")
                && parts[2].equals("devices") && parts[4].equals("limits")) {
            if (!method.equals("GET") && !method.equals("PUT")) return methodNotAllowed();
            UUID deviceId = uuid(parts[3]);
            return internal == null ? notFound() : internal.handleDeviceLimits(method, deviceId, body);
        }
        if ((parts.length == 5 || parts.length == 6) && parts[0].isEmpty()
                && parts[1].equals("api") && parts[2].equals("devices") && parts[4].equals("telemetry")) {
            String operation = parts.length == 6 ? parts[5] : "";
            return deviceTelemetry(method, parts[3], operation, uri);
        }
        if (parts.length >= 3 && parts[0].isEmpty() && parts[1].equals("api") && parts[2].equals("notifications")) {
            return notifications(method, parts, body);
        }

        if (parts.length >= 5 && parts[0].isEmpty() && parts[1].equals("api")
                && parts[2].equals("devices") && parts[4].equals("rules")) {
            return deviceRules(method, parts);
        }
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

        UUID id = null;
        if (parts.length == 4) {
            try {
                id = UUID.fromString(parts[3]);
            } catch (IllegalArgumentException e) {
                return json(400, Map.of("id", "некорректный UUID в адресе"));
            }
        }

        if (resource.equals("devices")) {
            if (method.equals("GET") && id == null) return deviceList(uri);
            return devices(method, id, body);
        }
        if (resource.equals("users")) return users(method, id, body);
        return telemetry(method, id, body);
    }

    private Response deviceTelemetry(String method, String id, String operation, String uri) {
        if (!operation.isEmpty() && !operation.equals("latest") && !operation.equals("average")) return notFound();
        if (!method.equals("GET")) return methodNotAllowed();

        UUID deviceId = uuid(id);
        if (operation.equals("latest")) return json(200, telemetryController.latest(deviceId));

        var params = new QueryStringDecoder(uri).parameters();
        short type;
        try {
            type = Short.parseShort(params.getOrDefault("type", List.of("")).getFirst());
        } catch (NumberFormatException e) {
            return json(400, Map.of("type", "Укажите целочисленный тип телеметрии"));
        }
        if (operation.equals("average")) {
            Instant since;
            try {
                since = Instant.parse(params.getOrDefault("since", List.of("")).getFirst());
            } catch (DateTimeParseException e) {
                return json(400, Map.of("since", "Укажите время в формате ISO-8601"));
            }
            return json(200, telemetryController.average(deviceId, type, since));
        }
        int limit;
        try {
            limit = Integer.parseInt(params.getOrDefault("limit", List.of("60")).getFirst());
        } catch (NumberFormatException e) {
            return json(400, Map.of("limit", "Укажите целое число от 1 до 1000"));
        }
        if (limit < 1 || limit > 1000) return json(400, Map.of("limit", "Укажите целое число от 1 до 1000"));
        return json(200, telemetryController.recent(deviceId, type, limit));
    }

    private Response login(byte[] body) throws IOException {
        LoginRequest credentials;
        try {
            credentials = read(body, LoginRequest.class);
        } catch (JsonProcessingException e) {
            return json(400, Map.of("error", "Некорректный JSON"));
        }
        if (credentials.email() == null || credentials.email().isBlank()
                || credentials.password() == null || credentials.password().isEmpty()) {
            return json(400, Map.of("error", "Введите email и пароль"));
        }
        return userController.authenticate(credentials.email(), credentials.password())
                .map(id -> json(200, Map.of("id", id)))
                .orElseGet(() -> json(401, Map.of("error", "Неверный email или пароль")));
    }

    private Response deviceList(String uri) {
        QueryStringDecoder query = new QueryStringDecoder(uri);
        UUID owner;
        Integer limit = null;
        int offset;
        String search;
        try {
            owner = uuid(query.parameters().getOrDefault("userId", java.util.List.of("")).getFirst());
            search = query.parameters().getOrDefault("search", java.util.List.of("")).getFirst().strip();
            var limits = query.parameters().get("limit");
            if (limits != null) {
                limit = Integer.parseInt(limits.getFirst());
                if (limit < 1 || limit > 100) throw new IllegalArgumentException();
            } else if (search.isEmpty()) {
                limit = 11;
            }
            offset = Integer.parseInt(query.parameters().getOrDefault("offset", java.util.List.of("0")).getFirst());
            if (offset < 0) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | ValidationException e) {
            return json(400, Map.of("error", "Проверьте userId, поиск и параметры страницы"));
        }
        return json(200, deviceController.list(owner, search, limit, offset));
    }

    private Response devices(String method, UUID id, byte[] body) throws IOException {
        if (method.equals("GET") && id != null) {
            return json(200, deviceController.getById(id));
        }
        if (method.equals("POST") && id == null) {
            DeviceDto dto = read(body, DeviceDto.class);
            transaction(() -> {
                deviceController.register(dto);
                return null;
            });
            return empty(202);
        }
        if (method.equals("PUT") && id != null) {
            DeviceDto dto = read(body, DeviceDto.class);
            return json(200, transaction(() -> deviceController.update(id, dto)));
        }
        if (method.equals("DELETE") && id != null) {
            deviceController.delete(id);
            return empty(204);
        }
        return methodNotAllowed();
    }

    private Response users(String method, UUID id, byte[] body) throws IOException {
        if (method.equals("GET") && id != null) {
            return json(200, userController.getById(id));   // в users(...): userController.getById(id)
        }
        if (method.equals("POST") && id == null) {
            UserDto dto = read(body, UserDto.class);
            return json(201, transaction(() -> userController.create(dto)));
        }
        if (method.equals("PUT") && id != null) {
            UserDto dto = read(body, UserDto.class);
            if (dto.getPassword() != null)
                return json(400, Map.of("error", "Для смены пароля используйте форму безопасности"));
            return json(200, transaction(() -> userController.update(id, dto)));
        }
        if (method.equals("DELETE") && id != null) {
            userController.delete(id);
            return empty(204);
        }
        return methodNotAllowed();
    }

    private Response telemetry(String method, UUID id, byte[] body) throws IOException {
        if (method.equals("POST") && id == null) {
            TelemetryDto dto = read(body, TelemetryDto.class);
            transaction(() -> {
                telemetryController.receive(dto);
                return null;
            });
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
            log.error("Failed to build JSON response", e);
            return new Response(500, "{\"error\":\"Внутренняя ошибка сервера\"}".getBytes(StandardCharsets.UTF_8));
        }
    }

    private Response empty(int status) { return new Response(status, null); }
    private Response notFound() { return json(404, Map.of("error", "Адрес не найден")); }
    private Response methodNotAllowed() { return json(405, Map.of("error", "Метод не поддерживается для этого адреса")); }
}