package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.TelemetryEntity;

import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

public interface TelemetryRepository {
    void save(TelemetryEntity telemetry); // сохраняет запись
    OptionalDouble averageValueSince(UUID deviceId, Instant since);

    // ---- НОВОЕ: чтение телеметрии для интерфейса (плитки, график, среднее) ----

    /** Одна точка графика. */
    record Point(Instant time, double value) {}

    /** Последнее показание одного типа (для плитки в карточке устройства). */
    record Latest(short telemetryType, double value, Instant time) {}

    /** Среднее по типу; average = null, если показаний за период нет. */
    record Average(Double average, long count) {}

    /** Последнее показание каждого типа у устройства (по одному на тип, по возрастанию типа). */
    default List<Latest> latestByType(UUID deviceId) {
        throw new UnsupportedOperationException("latestByType");
    }

    /** Последние limit точек типа (с момента since, если задан) в порядке возрастания времени. */
    default List<Point> series(UUID deviceId, short telemetryType, Instant since, int limit) {
        throw new UnsupportedOperationException("series");
    }

    /** Среднее значение указанного типа начиная с since (recorded_at >= since). */
    default Average averageByType(UUID deviceId, short telemetryType, Instant since) {
        throw new UnsupportedOperationException("averageByType");
    }
}