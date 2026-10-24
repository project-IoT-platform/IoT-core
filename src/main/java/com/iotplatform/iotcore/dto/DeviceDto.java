package com.iotplatform.iotcore.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.DeviceState;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class DeviceDto {

    private final UUID deviceId;
    private final String name;
    private final String manufacturer;

    private UUID userId;
    private Integer batteryLevel;
    private DeviceState state;
    private String geolocation;
    private Instant lastDataTime;

    @JsonCreator
    public DeviceDto(@JsonProperty("deviceId") UUID deviceId,
                     @JsonProperty("userId") UUID userId,
                     @JsonProperty("name") String name,
                     @JsonProperty("manufacturer") String manufacturer,
                     @JsonProperty("batteryLevel") Integer batteryLevel,
                     @JsonProperty("state") DeviceState state,
                     @JsonProperty("geolocation") String geolocation,
                     @JsonProperty("lastDataTime") Instant lastDataTime) {
        this.deviceId = deviceId;
        this.userId = userId;
        this.name = name;
        this.manufacturer = manufacturer;
        this.batteryLevel = batteryLevel;
        this.state = state;
        this.geolocation = geolocation;
        this.lastDataTime = lastDataTime;
    }

    public UUID getDeviceId() { return deviceId; }
    public String getName() { return name; }
    public String getManufacturer() { return manufacturer; }

    public UUID getUserId() { return userId; }
    public Integer getBatteryLevel() { return batteryLevel; }
    public DeviceState getState() { return state; }
    public String getGeolocation() { return geolocation; }
    public Instant getLastDataTime() { return lastDataTime; }

    public void setUserId(UUID userId) { this.userId = userId; }
    public void setBatteryLevel(Integer batteryLevel) { this.batteryLevel = batteryLevel; }
    public void setState(DeviceState state) { this.state = state; }
    public void setGeolocation(String geolocation) { this.geolocation = geolocation; }
    public void setLastDataTime(Instant lastDataTime) { this.lastDataTime = lastDataTime; }

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