package com.iotplatform.iotcore.check;

/**
 * Результат проверки телеметрии на норму (то, что возвращает функция тиммейта).
 * В норме: (false, null, null, null, null).
 * Вне нормы: (true, значение, на сколько больше нормы, нижняя граница или null, верхняя граница или null).
 * deviation < 0 значит «меньше нормы на |deviation|», deviation > 0 значит «больше нормы на deviation».
 */
public record TelemetryCheckResult(boolean outOfNorm, Double value, Double deviation,
                                   Double lowerBound, Double upperBound) {

    public static TelemetryCheckResult normal() {
        return new TelemetryCheckResult(false, null, null, null, null);
    }
}