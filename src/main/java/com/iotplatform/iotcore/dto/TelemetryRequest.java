package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.exception.ValidationException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record TelemetryRequest(UUID deviceId, Instant time, Short telemetryType, Double value) {

    public void validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (deviceId == null) errors.put("deviceId", "обязательное поле");
        if (time == null) errors.put("time", "обязательное поле");
        if (telemetryType == null) errors.put("telemetryType", "обязательное поле");
        if (value == null) errors.put("value", "обязательное поле");
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }
}
