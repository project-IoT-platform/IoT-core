package com.iotplatform.iotcore.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.DeviceState;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Данные устройства, которыми обмениваются с клиентом по HTTP (JSON).
 * Поля {@code deviceId}, {@code name} и {@code manufacturer} неизменяемы.
 */
public class DeviceDto {

    private final UUID deviceId;
    private final String name;
    private final String manufacturer;

    private UUID userId;
    private Integer batteryLevel;
    private DeviceState state;
    private String geolocation;
    private Instant lastDataTime;

    /**
     * Создаёт DTO устройства (используется Jackson при разборе JSON).
     *
     * @param deviceId идентификатор устройства
     * @param userId идентификатор владельца или {@code null}
     * @param name название устройства
     * @param manufacturer производитель
     * @param batteryLevel заряд батареи в процентах (0..100) или {@code null}
     * @param state состояние устройства
     * @param geolocation геолокация
     * @param lastDataTime время последних данных
     */
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

    /**
     * Возвращает идентификатор устройства.
     *
     * @return идентификатор устройства
     */
    public UUID getDeviceId() { return deviceId; }
    /**
     * Возвращает название устройства.
     *
     * @return название устройства
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
     * Возвращает время последних данных.
     *
     * @return время последних данных
     */
    public Instant getLastDataTime() { return lastDataTime; }

    /**
     * Задаёт идентификатор владельца.
     *
     * @param userId идентификатор владельца
     */
    public void setUserId(UUID userId) { this.userId = userId; }
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
    /**
     * Задаёт время последних данных.
     *
     * @param lastDataTime время последних данных
     */
    public void setLastDataTime(Instant lastDataTime) { this.lastDataTime = lastDataTime; }

    /**
     * Проверяет данные: id, название и состояние обязательны, заряд (если указан) должен быть от 0 до 100.
     *
     * @throws com.iotplatform.iotcore.exception.ValidationException с картой «поле: описание ошибки», если есть нарушения
     */
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

    /**
     * Создаёт копию DTO с другим идентификатором устройства.
     *
     * @param id новый идентификатор
     * @return новый объект с теми же остальными полями
     */
    public DeviceDto withDeviceId(UUID id) {
        return new DeviceDto(id, userId, name, manufacturer, batteryLevel, state, geolocation, lastDataTime);
    }
}