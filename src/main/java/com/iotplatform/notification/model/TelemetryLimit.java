package com.iotplatform.notification.model;

/** Границы нормы для одного типа телеметрии устройства. null = этой границы нет. */
public record TelemetryLimit(short telemetryType, Double lowerBound, Double upperBound) {}