package com.iotplatform.iotcore.entity;

import com.iotplatform.iotcore.model.DeviceState;

import java.time.Instant;
import java.util.UUID;

/**
 * Устройство в том виде, в котором оно хранится в БД. Поля {@code id}, {@code name} и {@code manufacturer} неизменяемы.
 */
public class DeviceEntity {

    private final UUID id;
    private final String name;
    private final String manufacturer;

    private UUID userId;
    private Instant lastDataTime;
    private Integer batteryLevel;
    private DeviceState state;
    private String geolocation;

    /**
     * Создаёт пустую сущность: {@code id}, {@code name} и {@code manufacturer} равны {@code null}.
     */
    public DeviceEntity() {

        this.id = null;
        this.name = null;
        this.manufacturer = null;
    }

    /**
     * Создаёт сущность со всеми полями.
     *
     * @param id идентификатор устройства
     * @param userId идентификатор владельца или {@code null}
     * @param name название
     * @param lastDataTime время последних данных
     * @param batteryLevel заряд батареи в процентах или {@code null}
     * @param manufacturer производитель
     * @param state состояние устройства
     * @param geolocation геолокация
     */
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

    /**
     * Возвращает идентификатор устройства.
     *
     * @return идентификатор устройства
     */
    public UUID getId() { return id; }
    /**
     * Возвращает название.
     *
     * @return название
     */
    public String getName() { return name; }
    /**
     * Возвращает производитель.
     *
     * @return производитель
     */
    public String getManufacturer() { return manufacturer; }

    /**
     * Возвращает идентификатор владельца или {@code null}.
     *
     * @return идентификатор владельца или {@code null}
     */
    public UUID getUserId() { return userId; }
    /**
     * Возвращает время последних данных.
     *
     * @return время последних данных
     */
    public Instant getLastDataTime() { return lastDataTime; }
    /**
     * Возвращает заряд батареи в процентах или {@code null}.
     *
     * @return заряд батареи в процентах или {@code null}
     */
    public Integer getBatteryLevel() { return batteryLevel; }
    /**
     * Возвращает состояние устройства.
     *
     * @return состояние устройства
     */
    public DeviceState getState() { return state; }
    /**
     * Возвращает геолокацию.
     *
     * @return геолокация
     */
    public String getGeolocation() { return geolocation; }

    /**
     * Задаёт идентификатор владельца.
     *
     * @param userId идентификатор владельца
     */
    public void setUserId(UUID userId) { this.userId = userId; }
    /**
     * Задаёт время последних данных.
     *
     * @param lastDataTime время последних данных
     */
    public void setLastDataTime(Instant lastDataTime) { this.lastDataTime = lastDataTime; }
    /**
     * Задаёт заряд батареи в процентах.
     *
     * @param batteryLevel заряд батареи в процентах
     */
    public void setBatteryLevel(Integer batteryLevel) { this.batteryLevel = batteryLevel; }
    /**
     * Задаёт состояние устройства.
     *
     * @param state состояние устройства
     */
    public void setState(DeviceState state) { this.state = state; }
    /**
     * Задаёт геолокацию.
     *
     * @param geolocation геолокация
     */
    public void setGeolocation(String geolocation) { this.geolocation = geolocation; }
}