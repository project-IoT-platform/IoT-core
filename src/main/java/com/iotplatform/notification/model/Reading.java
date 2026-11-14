package com.iotplatform.notification.model;

import java.time.Instant;
import java.util.UUID;

/** Одно показание телеметрии. */
public record Reading(UUID deviceId, Instant time, Short telemetryType, Double value) {}
