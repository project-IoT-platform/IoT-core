package com.iotplatform.iotcore.check;

import java.util.UUID;

/**
 * Заглушка проверки телеметрии на норму: всегда отвечает «в норме».
 * Сейчас {@code AlertService} этот интерфейс не использует, класс оставлен как заготовка для будущей проверки.
 */
public class StubTelemetryNormChecker implements TelemetryNormChecker {
    /**
     * Не проверяет показание.
     *
     * @param deviceId идентификатор устройства, не используется
     * @param telemetryType тип телеметрии, не используется
     * @param value значение показания, не используется
     * @return всегда {@link TelemetryCheckResult#normal()}
     */
    @Override
    public TelemetryCheckResult check(UUID deviceId, short telemetryType, double value) {
        // TODO тиммейт: заменить на настоящую проверку
        return TelemetryCheckResult.normal();
    }
}