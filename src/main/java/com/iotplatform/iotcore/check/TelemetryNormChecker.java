package com.iotplatform.iotcore.check;

import java.util.UUID;

/**
 * Проверка показания телеметрии на норму. Заглушка: {@link StubTelemetryNormChecker}.
 */
public interface TelemetryNormChecker {
    /**
     * Проверяет показание на норму.
     *
     * @param deviceId идентификатор устройства
     * @param telemetryType тип телеметрии
     * @param value значение показания
     * @return результат проверки; в норме {@link TelemetryCheckResult#normal()}
     */
    TelemetryCheckResult check(UUID deviceId, short telemetryType, double value);
}