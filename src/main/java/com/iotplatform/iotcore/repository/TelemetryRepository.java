package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.TelemetryEntity;

import java.time.Instant;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Хранилище телеметрии.
 */
public interface TelemetryRepository {
    /**
     * Сохраняет запись телеметрии.
     *
     * @param telemetry запись
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    void save(TelemetryEntity telemetry); // сохраняет запись
    /**
     * Считает среднее значение показаний устройства начиная с момента {@code since} (включительно).
     *
     * @param deviceId идентификатор устройства
     * @param since начало периода
     * @return среднее значение или пустой результат, если показаний за период нет
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    OptionalDouble averageValueSince(UUID deviceId, Instant since);
}