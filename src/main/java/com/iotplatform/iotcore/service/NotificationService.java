package com.iotplatform.iotcore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iotplatform.iotcore.check.BatteryCheckResult;
import com.iotplatform.iotcore.check.TelemetryCheckResult;
import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.dto.UserResponse;

import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.http.Json;
import com.iotplatform.iotcore.model.DeviceState;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Формирует события для outbox и выполняет синхронные HTTP-запросы статуса и настроек. */
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /**
     * Общие значения нормы и допустимого отклонения.
     *
     * @param norm норма
     * @param deviation допустимое отклонение
     */
    public record Settings(Double norm, Double deviation) {}

    private final BiConsumer<String, Object> events;
    private final String baseUrl;   // например http://localhost:8081
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public NotificationService(String baseUrl, BiConsumer<String, Object> events) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.events = Objects.requireNonNull(events);
    }

    /**
     * Сообщает сервису уведомлений, что устройство зарегистрировано.
     *
     * @param device данные устройства
     */
    public void notifyDeviceRegistered(DeviceDto device) {
        events.accept("device-registered", device);
    }

    /**
     * Сообщает сервису уведомлений о новом пользователе, чтобы тот отправил письмо с его id
     *.
     *
     * @param user сохранённый пользователь
     */
    public void notifyUserRegistered(UserResponse user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.id());
        body.put("username", user.username());
        body.put("email", user.email());
        events.accept("user-registered", body);
    }

    /**
     * Сообщает о заряде ниже нормы.
     *
     * @param deviceId идентификатор устройства
     * @param r результат проверки заряда
     */
    public void batteryLow(UUID deviceId, BatteryCheckResult r) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", deviceId);
        body.put("batteryLevel", r.batteryLevel());
        body.put("belowBy", r.belowBy());
        body.put("lowerBound", r.lowerBound());
        events.accept("battery-low", body);
    }

    /**
     * Сообщает о показании вне нормы.
     * Если в результате проверки нет значения, берётся значение из показания.
     *
     * @param t показание
     * @param r результат проверки на норму
     */
    public void telemetryAlert(TelemetryDto t, TelemetryCheckResult r) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", t.deviceId());
        body.put("time", t.time());
        body.put("telemetryType", t.telemetryType());
        body.put("value", r.value() != null ? r.value() : t.value());
        body.put("deviation", r.deviation());
        body.put("lowerBound", r.lowerBound());
        body.put("upperBound", r.upperBound());
        events.accept("telemetry-alert", body);
    }

    /**
     * Просит сервис уведомлений отправить владельцу статус устройства.
     *
     * @param deviceId идентификатор устройства
     * @return состояние устройства, которое сообщил сервис уведомлений
     * @throws DeviceNotFoundException если сервис уведомлений ответил 404
     * @throws UnsupportedOperationException если сервис недоступен, ответил не 200 или прислал некорректное состояние
     */
    public DeviceState sendDeviceStatus(UUID deviceId) {
        HttpResponse<String> r = call(request("/devices/" + deviceId + "/status")
                .POST(HttpRequest.BodyPublishers.noBody()).build());
        if (r.statusCode() == 404) throw new DeviceNotFoundException(deviceId);
        if (r.statusCode() != 200) throw unavailable("ответил " + r.statusCode());
        Map<String, String> answer = parse(r.body(), new TypeReference<Map<String, String>>() {});
        // ИСПРАВЛЕНО: без поля state или с неизвестным значением раньше был NPE/IllegalArgumentException -> 500
        if (answer == null || answer.get("state") == null) throw unavailable("не прислал state");
        try {
            return DeviceState.valueOf(answer.get("state"));
        } catch (IllegalArgumentException e) {
            throw unavailable("прислал неизвестный state: " + answer.get("state"));
        }
    }

    /**
     * Запрашивает общие значения нормы и отклонения.
     *
     * @return текущие настройки
     * @throws UnsupportedOperationException если сервис недоступен, ответил не 200 или прислал непонятный ответ
     */
    public Settings getDefaults() {
        HttpResponse<String> r = call(request("/settings").GET().build());
        if (r.statusCode() != 200) throw unavailable("ответил " + r.statusCode());
        return parse(r.body(), new TypeReference<Settings>() {});
    }

    /**
     * Отправляет новые общие значения нормы и отклонения.
     *
     * @param settings новые значения
     * @return настройки после изменения
     * @throws ValidationException если сервис уведомлений ответил 400 (ошибки берутся из его ответа)
     * @throws IllegalArgumentException если настройки не удалось превратить в JSON
     * @throws UnsupportedOperationException если сервис недоступен, ответил иначе чем 200 или 400 или прислал непонятный ответ
     */
    public Settings updateDefaults(Settings settings) {
        String json;
        try {
            json = Json.MAPPER.writeValueAsString(settings);
        } catch (IOException e) {
            throw new IllegalArgumentException("не удалось собрать JSON", e);
        }
        HttpResponse<String> r = call(request("/settings").PUT(HttpRequest.BodyPublishers.ofString(json)).build());
        if (r.statusCode() == 400) {   // сервис уведомлений не принял значения: отдаём ошибки как раньше
            throw new ValidationException(parse(r.body(), new TypeReference<Map<String, String>>() {}));
        }
        if (r.statusCode() != 200) throw unavailable("ответил " + r.statusCode());
        return parse(r.body(), new TypeReference<Settings>() {});
    }

    public void sendPasswordReset(String email, String token) {
        String secret = System.getenv("NOTIFICATION_DATA_TOKEN");
        if (secret == null || secret.isBlank()) throw unavailable("не задан NOTIFICATION_DATA_TOKEN");
        String frontend = System.getenv().getOrDefault("IOT_FRONTEND_URL", "http://127.0.0.1:3001/");
        URI frontendUri = URI.create(frontend);
        if (frontendUri.getHost() == null || frontendUri.getFragment() != null
                || !("http".equals(frontendUri.getScheme()) || "https".equals(frontendUri.getScheme()))) {
            throw new IllegalStateException("Некорректный IOT_FRONTEND_URL");
        }
        try {
            String json = Json.MAPPER.writeValueAsString(Map.of("email", email, "link", frontend + "#reset=" + token));
            HttpResponse<String> result = call(request("/internal/password-reset-mail")
                    .header("Authorization", "Bearer " + secret)
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build());
            if (result.statusCode() != 204) throw unavailable("ответил " + result.statusCode());
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось собрать письмо восстановления", e);
        }
    }

    // ---- внутренние помощники ----

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=utf-8");
    }

    private HttpResponse<String> call(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.error("HTTP request to Notification Service failed: " + request.uri(), e);
            throw unavailable("недоступен");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("запрос прерван");
        }
    }

    private static <T> T parse(String body, TypeReference<T> type) {
        try {
            return Json.MAPPER.readValue(body, type);
        } catch (IOException e) {
            throw unavailable("вернул непонятный ответ");
        }
    }

    // ApiRouter превращает UnsupportedOperationException в код 501
    private static UnsupportedOperationException unavailable(String why) {
        return new UnsupportedOperationException("сервис уведомлений " + why);
    }
}