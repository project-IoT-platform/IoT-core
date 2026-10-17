package com.iotplatform.iotcore.entity;

import com.iotplatform.iotcore.model.DeviceState;
import java.time.Instant;
import java.util.UUID;

public class DeviceEntity {

    private final UUID id;
    private final String name;
    private final String manufacturer;

    private UUID userId;
    private Instant lastDataTime;
    private Integer batteryLevel;
    private DeviceState state;
    private String geolocation;

    public DeviceEntity() {

        this.id = null;
        this.name = null;
        this.manufacturer = null;
    }

    public DeviceEntity(UUID id, UUID userId, String name, Instant lastDataTime,
                        Integer batteryLevel, String manufacturer, DeviceState state, String geolocation) {
        this.id = id;
        this.name = name;
        this.manufacturer = manufacturer;

        this.userId = userId;
        this.lastDataTime = lastDataTime;
        this.batteryLevel = batteryLevel;
        this.state = state;
        this.geolocation = geolocation;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getManufacturer() { return manufacturer; }

    public UUID getUserId() { return userId; }
    public Instant getLastDataTime() { return lastDataTime; }
    public Integer getBatteryLevel() { return batteryLevel; }
    public DeviceState getState() { return state; }
    public String getGeolocation() { return geolocation; }

    public void setUserId(UUID userId) { this.userId = userId; }
    public void setLastDataTime(Instant lastDataTime) { this.lastDataTime = lastDataTime; }
    public void setBatteryLevel(Integer batteryLevel) { this.batteryLevel = batteryLevel; }
    public void setState(DeviceState state) { this.state = state; }
    public void setGeolocation(String geolocation) { this.geolocation = geolocation; }
}