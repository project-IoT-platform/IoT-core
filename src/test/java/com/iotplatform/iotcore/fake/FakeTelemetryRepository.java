package com.iotplatform.iotcore.fake;

import com.iotplatform.iotcore.entity.TelemetryEntity;
import com.iotplatform.iotcore.repository.TelemetryRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

/** Репозиторий телеметрии в памяти. */
public class FakeTelemetryRepository implements TelemetryRepository {
    public final List<TelemetryEntity> rows = new ArrayList<>();

    @Override
    public void save(TelemetryEntity telemetry) {
        rows.add(telemetry);
    }

    @Override
    public OptionalDouble averageValueSince(UUID deviceId, Instant since) {
        return rows.stream()
                .filter(t -> t.deviceId().equals(deviceId) && !t.time().isBefore(since))
                .mapToDouble(TelemetryEntity::value)
                .average();
    }
}