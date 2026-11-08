package com.iotplatform.iotcore.check;

import java.util.UUID;

/** Проверка показания на норму: id устройства, тип телеметрии, значение. */
public interface TelemetryNormChecker {
    TelemetryCheckResult check(UUID deviceId, short telemetryType, double value);
}