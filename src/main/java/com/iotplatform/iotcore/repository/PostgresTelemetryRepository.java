package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.Database;
import com.iotplatform.iotcore.entity.TelemetryEntity;

// ===== ДЛЯ ЛЁШИ =====
// мб апись телеметрии и обновление lastDataTime на устройстве делать в одной транзакции?
public class PostgresTelemetryRepository implements TelemetryRepository {
    private final Database database;

    public PostgresTelemetryRepository(Database database) {
        this.database = database;
    }

    @Override
    public void save(TelemetryEntity telemetry) {
        // TODO Лёша:
        throw new UnsupportedOperationException("TODO Лёша");
    }
}
