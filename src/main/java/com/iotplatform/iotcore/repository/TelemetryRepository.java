package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.TelemetryEntity;

import java.time.Instant;
import java.util.OptionalDouble;
import java.util.UUID;

public interface TelemetryRepository {
    void save(TelemetryEntity telemetry); // сохраняет запись
    OptionalDouble averageValueSince(UUID deviceId, Instant since);
}