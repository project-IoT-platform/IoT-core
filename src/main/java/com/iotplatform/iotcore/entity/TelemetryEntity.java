package com.iotplatform.iotcore.entity;

import java.time.Instant;
import java.util.UUID;

public record TelemetryEntity(
        UUID id,              // PK: event_id
        UUID deviceId,        // FK на устройство
        Instant time,
        Short telemetryType,
        Double value) {
}
