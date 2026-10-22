package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.Database;
import com.iotplatform.iotcore.entity.TelemetryEntity;

import java.time.Instant;
import java.util.OptionalDouble;
import java.util.UUID;

// ===== ДЛЯ ЛЁШИ =====
// мб запись телеметрии и обновление lastDataTime на устройстве делать в одной транзакции?
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

    @Override
    public OptionalDouble averageValueSince(UUID deviceId, Instant since) {
        // TODO Лёша: посчитать среднее начение телеметрии где device id равен ... и time больше или равно ...
        // AVG вернёт NULL, если строк нет: в этом случае вернуть OptionalDouble.empty().
        throw new UnsupportedOperationException("TODO Лёша");
    }
}