package com.iotplatform.iotcore.check;

import java.util.UUID;

/**
 * ЗАГЛУШКА. Сюда вставляется функция тиммейта, которая проверяет заряд батареи.
 * Вход: id устройства, заряд. Выход: BatteryCheckResult (см. описание полей там).
 * Пока всегда отвечает «в норме», поэтому уведомлений о разряде не будет, пока функцию не вставят.
 * Подключается в Main: new AlertService(..., new StubBatteryChecker(), ...).
 */
public class StubBatteryChecker implements BatteryChecker {
    @Override
    public BatteryCheckResult check(UUID deviceId, int batteryLevel) {
        // TODO тиммейт: заменить на настоящую проверку
        return BatteryCheckResult.normal();
    }
}