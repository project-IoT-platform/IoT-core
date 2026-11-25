package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.db.RowMapper;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.entity.TelemetryEntity;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalDouble;
import java.util.UUID;

// мб запись телеметрии и обновление lastDataTime на устройстве делать в одной транзакции?
/**
 * Реализация {@link TelemetryRepository} на PostgreSQL (таблица {@code telemetry}).
 */
public class PostgresTelemetryRepository implements TelemetryRepository {
    private final DatabaseUtils databaseUtils;

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
    public record Average(Double average, long count) {}

    public Average summarizeSince(UUID deviceId, Instant since, Short type) {
        String select = """
                SELECT AVG(t.value) AS average, COUNT(t.id) AS count
                FROM device d LEFT JOIN telemetry t ON t.device_id = d.id AND t.recorded_at >= ?
                """;
        String device = " WHERE d.id = ? GROUP BY d.id";
        RowMapper<Average> mapper = rs ->
                new Average(rs.getObject("average", Double.class), rs.getLong("count"));
        var rows = type == null
                ? databaseUtils.query(select + device, mapper, since, deviceId)
                : databaseUtils.query(select + " AND t.telemetry_type = ?" + device, mapper, since, type, deviceId);
        if (rows.isEmpty()) throw new DeviceNotFoundException(deviceId);
        return rows.getFirst();
    }
}
