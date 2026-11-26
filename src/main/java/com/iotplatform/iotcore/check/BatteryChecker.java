package com.iotplatform.iotcore.check;

import java.util.UUID;

/** Проверяет заряд устройства по настроенному порогу. */
public interface BatteryChecker {
    /**
     * Проверяет, не ниже ли нормы заряд устройства.
     *
     * @param deviceId идентификатор устройства
     * @param batteryLevel текущий заряд в процентах
     * @return результат проверки; в норме {@link BatteryCheckResult#normal()}
     */
    BatteryCheckResult check(UUID deviceId, int batteryLevel);
}