package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.entity.TelemetryEntity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

// мб запись телеметрии и обновление lastDataTime на устройстве делать в одной транзакции?
public class PostgresTelemetryRepository implements TelemetryRepository {
    private final DatabaseUtils databaseUtils;

    public PostgresTelemetryRepository(DatabaseUtils databaseUtils) {
        this.databaseUtils = databaseUtils;
    }

    @Override
    public void save(TelemetryEntity telemetry) {
        databaseUtils.update(
                "INSERT INTO telemetry (id, device_id, recorded_at, telemetry_type, value) "
                        + "VALUES (?, ?, ?, ?, ?)",
                // ИСПРАВЛЕНО: Instant -> OffsetDateTime (драйвер PostgreSQL не принимает Instant, был HTTP 500)
                telemetry.id(), telemetry.deviceId(), telemetry.time().atOffset(ZoneOffset.UTC),
                telemetry.telemetryType(), telemetry.value());

    }

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

    // ---- НОВОЕ ----

    @Override
    public List<Latest> latestByType(UUID deviceId) {
        // DISTINCT ON берёт первую строку каждой группы типа; из-за ORDER BY ... recorded_at DESC это самая свежая
        return databaseUtils.query(
                "SELECT DISTINCT ON (telemetry_type) telemetry_type, value, recorded_at "
                        + "FROM telemetry WHERE device_id = ? "
                        + "ORDER BY telemetry_type, recorded_at DESC",
                rs -> new Latest(
                        rs.getShort("telemetry_type"),
                        rs.getDouble("value"),
                        rs.getObject("recorded_at", OffsetDateTime.class).toInstant()),
                deviceId);
    }

    @Override
    public List<Point> series(UUID deviceId, short telemetryType, Instant since, int limit) {
        // внутри берём самые свежие limit строк (DESC), снаружи разворачиваем по возрастанию для графика
        List<Object> params = new ArrayList<>();
        params.add(deviceId);
        params.add(telemetryType);
        String sinceSql = "";
        if (since != null) {
            sinceSql = "AND recorded_at >= ? ";
            params.add(since.atOffset(ZoneOffset.UTC));
        }
        params.add(limit);
        return databaseUtils.query(
                "SELECT recorded_at, value FROM ("
                        + "SELECT recorded_at, value FROM telemetry "
                        + "WHERE device_id = ? AND telemetry_type = ? " + sinceSql
                        + "ORDER BY recorded_at DESC LIMIT ?) t ORDER BY recorded_at ASC",
                rs -> new Point(
                        rs.getObject("recorded_at", OffsetDateTime.class).toInstant(),
                        rs.getDouble("value")),
                params.toArray());
    }

    @Override
    public Average averageByType(UUID deviceId, short telemetryType, Instant since) {
        return databaseUtils.query(
                "SELECT AVG(value), COUNT(*) FROM telemetry "
                        + "WHERE device_id = ? AND telemetry_type = ? AND recorded_at >= ?",
                rs -> new Average(rs.getObject(1, Double.class), rs.getLong(2)),   // AVG по пустому набору = NULL
                deviceId, telemetryType, since.atOffset(ZoneOffset.UTC)).getFirst();
    }
}