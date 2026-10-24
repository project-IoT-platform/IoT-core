package com.iotplatform.iotcore.entity;

import java.time.Instant;
import java.util.UUID;

public record TelemetryEntity(
        UUID id,
        UUID deviceId,
        Instant time,
        Short telemetryType,
        Double value) {
}
