package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.exception.ValidationException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Показание телеметрии от устройства.
 *
 * @param deviceId идентификатор устройства
 * @param time момент измерения
 * @param telemetryType тип телеметрии
 * @param value значение показания
 */
public record TelemetryDto(UUID deviceId, Instant time, Short telemetryType, Double value) {

    /**
     * Проверяет, что все четыре поля заполнены.
     *
     * @throws com.iotplatform.iotcore.exception.ValidationException с перечнем незаполненных полей
     */
    public void validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (deviceId == null) errors.put("deviceId", "обязательное поле");
        if (time == null) errors.put("time", "обязательное поле");
        if (telemetryType == null) errors.put("telemetryType", "обязательное поле");
        if (value == null) errors.put("value", "обязательное поле");
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }
}
