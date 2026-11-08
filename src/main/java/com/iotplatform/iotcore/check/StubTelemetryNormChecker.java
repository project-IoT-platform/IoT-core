package com.iotplatform.iotcore.check;

import java.util.UUID;

/**
 * ЗАГЛУШКА. Сюда вставляется функция тиммейта, которая проверяет телеметрию на норму.
 * Вход: id устройства, тип телеметрии, значение.
 * Выход: TelemetryCheckResult (см. описание полей там).
 * Пока всегда отвечает «в норме», поэтому уведомлений об отклонении не будет, пока функцию не вставят.
 * Подключается в Main: new AlertService(new StubTelemetryNormChecker(), ...).
 */
public class StubTelemetryNormChecker implements TelemetryNormChecker {
    @Override
    public TelemetryCheckResult check(UUID deviceId, short telemetryType, double value) {
        // TODO тиммейт: заменить на настоящую проверку
        return TelemetryCheckResult.normal();
    }
}