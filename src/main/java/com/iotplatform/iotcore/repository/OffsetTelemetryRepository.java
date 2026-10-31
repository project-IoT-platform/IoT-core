package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.entity.TelemetryEntity;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Работает вместо PostgresTelemetryRepository (его файл не тронут).
 * Единственное отличие: время записи передаётся в БД как OffsetDateTime (UTC),
 * потому что драйвер PostgreSQL не умеет сохранять java.time.Instant, и из-за этого
 * POST /api/telemetry возвращал 500 до отправки уведомлений.
 */
public class OffsetTelemetryRepository implements TelemetryRepository {
    private final DatabaseUtils databaseUtils;

    public OffsetTelemetryRepository(DatabaseUtils databaseUtils) {
        this.databaseUtils = databaseUtils;
    }

    @Override
    public void save(TelemetryEntity t) {
        databaseUtils.update(
                "INSERT INTO telemetry (id, device_id, recorded_at, telemetry_type, value) "
                        + "VALUES (?, ?, ?, ?, ?)",
                // ИЗМЕНЕНО: Instant -> OffsetDateTime
                t.id(), t.deviceId(), t.time().atOffset(ZoneOffset.UTC), t.telemetryType(), t.value());
    }

    @Override
    public OptionalDouble averageValueSince(UUID deviceId, Instant since) {
        // среднее значение за период; для пустой выборки AVG даёт NULL, а не 0
        Double average = databaseUtils.query(
                "SELECT AVG(value) FROM telemetry WHERE device_id = ? AND recorded_at >= ?",
                rs -> rs.getObject(1, Double.class),
                deviceId, since.atOffset(ZoneOffset.UTC)).getFirst();
        return average == null ? OptionalDouble.empty() : OptionalDouble.of(average);
    }
}