package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.entity.TelemetryEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

// мб запись телеметрии и обновление lastDataTime на устройстве делать в одной транзакции?
/**
 * Реализация {@link TelemetryRepository} на PostgreSQL (таблица {@code telemetry}).
 */
public class PostgresTelemetryRepository implements TelemetryRepository {
    private final DatabaseUtils databaseUtils;

    @Override
    public List<TelemetryEntity> latestByDevice(UUID deviceId) {
        return databaseUtils.query("""
                SELECT DISTINCT ON (telemetry_type) id, recorded_at, telemetry_type, value
                FROM telemetry
                WHERE device_id = ?
                ORDER BY telemetry_type, recorded_at DESC, id DESC
                """, row -> reading(row, deviceId), deviceId);
    }

    @Override
    public List<TelemetryEntity> recentByType(UUID deviceId, short type, int limit) {
        return databaseUtils.query("""
                SELECT id, recorded_at, telemetry_type, value
                FROM telemetry
                WHERE device_id = ? AND telemetry_type = ?
                ORDER BY recorded_at DESC, id DESC
                LIMIT ?
                """, row -> reading(row, deviceId), deviceId, type, limit).reversed();
    }

    @Override
    public Average averageByTypeSince(UUID deviceId, short type, Instant since) {
        return databaseUtils.query("""
                SELECT AVG(value), COUNT(*) FROM telemetry
                WHERE device_id = ? AND telemetry_type = ? AND recorded_at >= ?
                """, row -> new Average(row.getObject(1, Double.class), row.getLong(2)),
                deviceId, type, since).getFirst();
    }

    private static TelemetryEntity reading(ResultSet row, UUID deviceId) throws SQLException {
        return new TelemetryEntity(row.getObject("id", UUID.class), deviceId,
                row.getObject("recorded_at", OffsetDateTime.class).toInstant(),
                row.getShort("telemetry_type"), row.getDouble("value"));
    }

    /**
     * Создаёт репозиторий.
     *
     * @param databaseUtils доступ к БД
     */
    public PostgresTelemetryRepository(DatabaseUtils databaseUtils) {
        this.databaseUtils = databaseUtils;
    }

    /**
     * Вставляет запись телеметрии.
     *
     * @param telemetry запись
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public void save(TelemetryEntity telemetry) {
        databaseUtils.update(
                "INSERT INTO telemetry (id, device_id, recorded_at, telemetry_type, value) "
                        + "VALUES (?, ?, ?, ?, ?)",
                // ИСПРАВЛЕНО: Instant -> OffsetDateTime (драйвер PostgreSQL не принимает Instant, был HTTP 500)
                telemetry.id(), telemetry.deviceId(), telemetry.time().atOffset(ZoneOffset.UTC),
                telemetry.telemetryType(), telemetry.value());

    }

    /**
     * Считает {@code AVG(value)} по показаниям устройства с {@code recorded_at >= since}.
     *
     * @param deviceId идентификатор устройства
     * @param since начало периода
     * @return среднее значение или пустой результат, если показаний нет
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public OptionalDouble averageValueSince(UUID deviceId, Instant since) {
        Double average = databaseUtils.query(
                "SELECT AVG(value) FROM telemetry WHERE device_id = ? AND recorded_at >= ?",
                rs -> rs.getObject(1, Double.class), // для SQL NULL вернёт null, а не 0
                deviceId, since.atOffset(ZoneOffset.UTC)).getFirst();

        if (average == null) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(average);
    }
}