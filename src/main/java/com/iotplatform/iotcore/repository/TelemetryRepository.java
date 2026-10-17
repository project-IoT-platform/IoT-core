package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.TelemetryEntity;

public interface TelemetryRepository {
    void save(TelemetryEntity telemetry); // сохраняет запись
}