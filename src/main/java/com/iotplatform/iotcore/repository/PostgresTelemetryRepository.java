package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.entity.TelemetryEntity;

import java.time.Instant;
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
                telemetry.deviceId(), telemetry.time(), telemetry.telemetryType(), telemetry.value());

    }

    @Override
    public OptionalDouble averageValueSince(UUID deviceId, Instant since) {
        // TODO Лёша: посчитать среднее начение телеметрии где device id равен ... и time больше или равно ...
        // AVG вернёт NULL, если строк нет: в этом случае вернуть OptionalDouble.empty().
        throw new UnsupportedOperationException("TODO Лёша");
    }
}