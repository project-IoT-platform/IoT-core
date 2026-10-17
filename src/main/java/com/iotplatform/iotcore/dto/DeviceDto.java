package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.DeviceState;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record DeviceDto(
        UUID deviceId,
        UUID userId,
        String name,
        String manufacturer,
        Integer batteryLevel,
        DeviceState state,
        String geolocation,
        Instant lastDataTime) {

    public void validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (deviceId == null) errors.put("deviceId", "обязательное поле");
        if (name == null || name.isBlank()) errors.put("name", "обязательное поле");
        if (state == null) errors.put("state", "обязательное поле (ON или OFF)");
        if (batteryLevel != null && (batteryLevel < 0 || batteryLevel > 100)) {
            errors.put("batteryLevel", "должно быть от 0 до 100");
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    public DeviceDto withDeviceId(UUID id) {
        return new DeviceDto(id, userId, name, manufacturer, batteryLevel, state, geolocation, lastDataTime);
    }
}