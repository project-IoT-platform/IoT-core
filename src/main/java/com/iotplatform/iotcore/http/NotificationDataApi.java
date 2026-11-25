package com.iotplatform.iotcore.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.iotplatform.iotcore.dto.DeviceLimitsDto;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.RuleOperator;
import com.iotplatform.iotcore.repository.PostgresDeviceLimitsRepository;
import com.iotplatform.iotcore.repository.PostgresTelemetryRepository;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class NotificationDataApi {
    private static final String PREFIX = "/internal/notifications";
    private static final String DEVICES = PREFIX + "/devices/";
    private static final ObjectReader JSON = strictJson();

    private static ObjectReader strictJson() {
        var mapper = Json.MAPPER.copy();
        for (LogicalType type : List.of(LogicalType.Integer, LogicalType.Float)) {
            mapper.coercionConfigFor(type).setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        }
        return mapper.reader()
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private final byte[] credential;
    private final PostgresTelemetryRepository telemetry;
    private final PostgresDeviceLimitsRepository limits;

    NotificationDataApi(String token, PostgresTelemetryRepository telemetry,
                        PostgresDeviceLimitsRepository limits) {
        this.credential = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
        this.telemetry = telemetry;
        this.limits = limits;
    }

    static boolean matches(String uri) {
        return uri.equals(PREFIX) || uri.startsWith(PREFIX + "/") || uri.startsWith(PREFIX + "?");
    }

    ApiRouter.Response handle(String method, String uri, byte[] body, String authorization) {
        if (credential == null) return error(404, "Адрес не найден");
        if (!authorized(authorization)) return error(401, "Требуется сервисная аутентификация");
        try {
            return dispatch(method, new QueryStringDecoder(uri), body);
        } catch (ValidationException e) {
            return ApiRouter.json(400, e.getErrors());
        } catch (DeviceNotFoundException e) {
            return error(404, e.getMessage());
        } catch (IOException | IllegalArgumentException | DateTimeParseException e) {
            return error(400, "Некорректный адрес, параметры или тело запроса");
        }
    }

    private boolean authorized(String header) {
        return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
                && MessageDigest.isEqual(credential, header.substring(7).getBytes(StandardCharsets.UTF_8));
    }

    private ApiRouter.Response dispatch(String method, QueryStringDecoder uri, byte[] body) throws IOException {
        String path = uri.path();
        if (!path.startsWith(DEVICES)) return error(404, "Адрес не найден");
        String[] parts = path.substring(DEVICES.length()).split("/", -1);
        if (parts.length == 2 && parts[1].equals("limits")) {
            UUID id = UUID.fromString(parts[0]);
            return switch (method) {
                case "GET" -> ApiRouter.json(200, limits.find(id));
                case "PUT" -> ApiRouter.json(200, limits.replace(id, read(body, DeviceLimitsDto.class)));
                default -> error(405, "Метод не поддерживается");
            };
        }
        if (parts.length == 3 && parts[1].equals("telemetry") && parts[2].equals("average")) {
            if (!method.equals("GET")) return error(405, "Метод не поддерживается");
            UUID id = UUID.fromString(parts[0]);
            String since = parameter(uri, "since");
            if (since == null) throw new ValidationException(Map.of("since", "обязательное поле"));
            Instant start = Instant.parse(since);
            String type = parameter(uri, "type");
            Short telemetryType = type == null ? null : Short.valueOf(type);
            return ApiRouter.json(200, telemetry.summarizeSince(id, start, telemetryType));
        }
        if (parts.length == 4 && parts[1].equals("norms")) {
            if (!method.equals("PUT")) return error(405, "Метод не поддерживается");
            UUID id = UUID.fromString(parts[0]);
            short type = Short.parseShort(parts[2]);
            RuleOperator operator = RuleOperator.valueOf(parts[3]);
            Double value = read(body, Norm.class).value();
            if (value == null) throw new ValidationException(Map.of("value", "обязательное поле"));
            return ApiRouter.json(200, limits.setNorm(id, type, operator, value));
        }
        return error(404, "Адрес не найден");
    }

    private static String parameter(QueryStringDecoder uri, String key) {
        List<String> values = uri.parameters().get(key);
        if (values == null) return null;
        if (values.size() != 1) throw new ValidationException(Map.of(key, "укажите параметр один раз"));
        return values.getFirst();
    }

    private static <T> T read(byte[] body, Class<T> type) throws IOException {
        T value = body == null || body.length == 0 ? null : JSON.forType(type).readValue(body);
        if (value == null) throw new ValidationException(Map.of("body", "пустое тело запроса"));
        return value;
    }

    private static ApiRouter.Response error(int status, String message) {
        return ApiRouter.json(status, Map.of("error", message));
    }

    private record Norm(Double value) {}
}
