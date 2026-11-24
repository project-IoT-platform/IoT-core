package com.iotplatform.iotcore.check;

import java.util.UUID;

/**
 * Заглушка проверки заряда: всегда отвечает «в норме», поэтому уведомления о разряде
 * не отправляются, пока вместо неё не подключена настоящая реализация.
 * Подключается в {@code IotCoreMain} при создании {@code AlertService}.
 */
public class StubBatteryChecker implements BatteryChecker {
    /**
     * Не проверяет заряд.
     *
     * @param deviceId идентификатор устройства, не используется
     * @param batteryLevel текущий заряд, не используется
     * @return всегда {@link BatteryCheckResult#normal()}
     */
    @Override
    public BatteryCheckResult check(UUID deviceId, int batteryLevel) {
        // TODO тиммейт: заменить на настоящую проверку
        return BatteryCheckResult.normal();
    }
}