package com.iotplatform.iotcore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iotplatform.iotcore.check.BatteryCheckResult;
import com.iotplatform.iotcore.check.TelemetryCheckResult;
import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.entity.UserEntity;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public record Settings(Double norm, Double deviation) {}

    private final String baseUrl;   // например http://localhost:8081
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public NotificationService(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public void notifyDeviceRegistered(DeviceDto device) {
        sendEvent("/events/device-registered", device);
    }

    /** Новый пользователь сохранён: письмо с его id. */
    public void notifyUserRegistered(UserResponse user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.id());
        body.put("username", user.username());
        body.put("email", user.email());
        sendEvent("/events/user-registered", body);
    }
    public void checkBattery(DeviceDto device) {
        Integer level = device.getBatteryLevel();
        UUID id = device.getDeviceId();
        if (level == null || id == null) return;

        if (level >= LOW_BATTERY_PERCENT) {
            lowBatteryNotified.remove(id);
            return;
        }
        if (device.getUserId() == null) return;
        if (!lowBatteryNotified.add(id)) return;
        log.info("Low battery: device {}, level {}%", id, level);

    /** Заряд ниже нормы (результат проверки заряда). */
    public void batteryLow(UUID deviceId, BatteryCheckResult r) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", deviceId);
        body.put("batteryLevel", r.batteryLevel());
        body.put("belowBy", r.belowBy());
        body.put("lowerBound", r.lowerBound());
        sendEvent("/events/battery-low", body);
    }

    /** Показание вне нормы (результат проверки телеметрии). */
    public void telemetryAlert(TelemetryDto t, TelemetryCheckResult r) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", t.deviceId());
        body.put("time", t.time());
        body.put("telemetryType", t.telemetryType());
        body.put("value", r.value() != null ? r.value() : t.value());
        body.put("deviation", r.deviation());
        body.put("lowerBound", r.lowerBound());
        body.put("upperBound", r.upperBound());
        sendEvent("/events/telemetry-alert", body);
    }

    public String deviceStatusText(UUID deviceId, UserEntity requester) {
        return statusLine(deviceFor(deviceId, requester));
    }

    public String deviceAverageText(UUID deviceId, UserEntity requester) {
        DeviceEntity device = deviceFor(deviceId, requester);
        OptionalDouble avg = telemetry.averageValueSince(deviceId, Instant.now().minus(AVERAGE_WINDOW));
        if (avg.isEmpty()) {
            return "Устройство " + device.getName() + ": за последний час показаний нет";
        }
        return String.format(Locale.ROOT, "Устройство %s: среднее за последний час: %.2f",
                device.getName(), avg.getAsDouble());
    }

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

    public Settings getDefaults() {
        HttpResponse<String> r = call(request("/settings").GET().build());
        if (r.statusCode() != 200) throw unavailable("ответил " + r.statusCode());
        return parse(r.body(), new TypeReference<Settings>() {});
    }

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

    // ---- внутренние помощники ----

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=utf-8");
    }

    private void sendEvent(String path, Object body) {
        try {
            String json = Json.MAPPER.writeValueAsString(body);
            log.info("-> сервис уведомлений: POST " + path + " " + json);
            http.sendAsync(request(path).POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                            HttpResponse.BodyHandlers.ofString())
                    .whenComplete((resp, err) -> {
                        if (err != null) {
                            // одной строкой, без стектрейса: «сервис выключен» — не ошибка в нашем коде
                            Throwable cause = err.getCause() != null ? err.getCause() : err;
                            log.error("Сервис уведомлений недоступен (" + baseUrl + "), событие "
                                    + path + " не доставлено: " + cause.getClass().getSimpleName()
                                    + (cause.getMessage() != null ? " " + cause.getMessage() : ""));
                        } else if (resp.statusCode() >= 300) {
                            log.error("Сервис уведомлений ответил " + resp.statusCode() + " на " + path + ": " + resp.body());
                        } else {
                            log.info("<- сервис уведомлений: " + path + " " + resp.statusCode());
                        }
                    });
        } catch (Exception e) {
            log.info("Не удалось отправить событие в сервис уведомлений", e);
            log.error("Failed to send notification", e);
        }
    }

    private HttpResponse<String> call(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
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