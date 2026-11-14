package com.iotplatform.iotcore.testsupport;

import com.iotplatform.iotcore.entity.TelemetryEntity;
import com.iotplatform.iotcore.repository.TelemetryRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

/** Фейк вместо БД: запоминает всё, что сохранили. */
public class RecordingTelemetryRepository implements TelemetryRepository {
    private final List<TelemetryEntity> saved = new ArrayList<>();

    public List<TelemetryEntity> saved() {
        return saved;
    }

    @Override
    public void save(TelemetryEntity telemetry) {
        saved.add(telemetry);
    }

    @Override
    public OptionalDouble averageValueSince(UUID deviceId, Instant since) {
        return OptionalDouble.empty();
    }
}