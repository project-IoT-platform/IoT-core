package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.entity.TelemetryEntity;

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
}
