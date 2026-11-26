package com.iotplatform.iotcore.check;

/**
 * Результат проверки показания телеметрии на норму.
 * В норме: {@code (false, null, null, null, null)}. Вне нормы:
 * {@code (true, значение, отклонение, нижняя граница или null, верхняя граница или null)}.
 *
 * @param outOfNorm {@code true}, если показание вышло за норму
 * @param value проверенное значение; {@code null}, если в норме
 * @param deviation отклонение от нормы: меньше нуля значит «меньше нормы на |deviation|», больше нуля значит «больше нормы на deviation»
 * @param lowerBound нижняя граница нормы или {@code null}
 * @param upperBound верхняя граница нормы или {@code null}
 */
public record TelemetryCheckResult(boolean outOfNorm, Double value, Double deviation,
                                   Double lowerBound, Double upperBound) {

    /**
     * Создаёт результат «показание в норме».
     *
     * @return результат с {@code outOfNorm = false} и пустыми остальными полями
     */
    public static TelemetryCheckResult normal() {
        return new TelemetryCheckResult(false, null, null, null, null);
    }
}