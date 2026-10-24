package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.entity.TelemetryEntity;

import java.time.Instant;
import java.time.ZoneOffset;
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
                telemetry.id(), telemetry.deviceId(), telemetry.time(), telemetry.telemetryType(), telemetry.value());

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
}