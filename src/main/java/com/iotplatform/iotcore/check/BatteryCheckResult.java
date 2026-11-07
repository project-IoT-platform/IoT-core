package com.iotplatform.iotcore.check;

/**
 * Результат проверки заряда (то, что возвращает функция тиммейта).
 * В норме: (false, null, null, null).
 * Ниже нормы: (true, заряд, на сколько меньше нижней границы, нижняя граница).
 */
public record BatteryCheckResult(boolean low, Integer batteryLevel, Integer belowBy, Integer lowerBound) {

    public static BatteryCheckResult normal() {
        return new BatteryCheckResult(false, null, null, null);
    }
}