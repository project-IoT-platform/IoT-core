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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ApiRouter {
    private static final System.Logger LOG = System.getLogger(ApiRouter.class.getName());

    record Response(int status, byte[] body) {}

    private final DeviceController deviceController;
    private final UserController userController;
    private final TelemetryController telemetryController;
    private final NotificationController notificationController;

    ApiRouter(DeviceController deviceController,
              UserController userController,
              TelemetryController telemetryController,
              NotificationController notificationController) {
        this.deviceController = deviceController;
        this.userController = userController;
        this.telemetryController = telemetryController;
        this.notificationController = notificationController;
    }

    /** Обрабатывает запрос и пишет в лог одну строку: метод, адрес, код ответа, время; при ошибке ещё и её текст. */
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

    /** Пароль из тела запроса в лог не попадает. */
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
        } catch (DeviceNotFoundException | UserNotFoundException e) {
            return json(404, Map.of("error", String.valueOf(e.getMessage())));
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
        // ДОБАВЛЕНО: POST /api/login
        if (parts.length == 3 && parts[0].isEmpty() && parts[1].equals("api") && parts[2].equals("login")) {
            return login(method, body);
        }
        // ДОБАВЛЕНО: POST /api/users/{id}/confirm
        if (parts.length == 5 && parts[0].isEmpty() && parts[1].equals("api")
                && parts[2].equals("users") && parts[4].equals("confirm")) {
            return confirmUser(method, parts[3]);
        }
        if (parts.length < 3 || parts.length > 4 || !parts[0].isEmpty() || !parts[1].equals("api")) {
            return notFound();
        }
        String resource = parts[2];
        if (!resource.equals("devices") && !resource.equals("users") && !resource.equals("telemetry")) {
            return notFound();
        }

        // GET /api/devices?userId={uuid}: список устройств пользователя (без {id} в пути)
        if (method.equals("GET") && parts.length == 3 && resource.equals("devices")) {
            return devicesByUser(uri);
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

    // ДОБАВЛЕНО: тело {email, password}; пользователь не найден или пароль неверный -> 401
    private Response login(String method, byte[] body) throws IOException {
        if (!method.equals("POST")) return methodNotAllowed();
        UserDto dto = read(body, UserDto.class);
        return userController.login(dto.getEmail(), dto.getPassword())
                .map(user -> json(200, user))
                .orElseGet(() -> json(401, Map.of("error", "Неверный email или пароль")));
    }

    // ДОБАВЛЕНО: подтверждение регистрации, после него пользователь появляется в БД
    private Response confirmUser(String method, String rawId) {
        if (!method.equals("POST")) return methodNotAllowed();
        UUID id;
        try {
            id = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            return json(400, Map.of("id", "некорректный UUID в адресе"));
        }
        return json(200, userController.confirm(id));   // нет такой регистрации или срок истёк -> 404
    }

    private Response devicesByUser(String uri) {
        List<String> values = new QueryStringDecoder(uri).parameters().get("userId");
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            return json(400, Map.of("error", "userId обязателен"));
        }
        UUID userId;
        try {
            userId = UUID.fromString(values.get(0).trim());
        } catch (IllegalArgumentException e) {
            return json(400, Map.of("error", "некорректный UUID"));
        }
        return json(200, deviceController.getByUserId(userId));
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
            return json(200, userController.getById(id));   // в users(...): userController.getById(id)
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