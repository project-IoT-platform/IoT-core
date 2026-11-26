package com.iotplatform.iotcore.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись телеметрии, как она хранится в БД.
 *
 * @param id идентификатор записи
 * @param deviceId идентификатор устройства
 * @param time момент измерения
 * @param telemetryType тип телеметрии
 * @param value значение показания
 */
public record TelemetryEntity(
        UUID id,
        UUID deviceId,
        Instant time,
        Short telemetryType,
        Double value) {
}
