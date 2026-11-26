package com.iotplatform.iotcore.check;

/**
 * Результат проверки заряда батареи.
 * В норме: {@code (false, null, null, null)}. Ниже нормы: {@code (true, заряд, на сколько ниже границы, нижняя граница)}.
 *
 * @param low {@code true}, если заряд ниже допустимой границы
 * @param batteryLevel текущий заряд в процентах; {@code null}, если заряд в норме
 * @param belowBy на сколько процентов заряд ниже границы; {@code null}, если заряд в норме
 * @param lowerBound нижняя допустимая граница в процентах; {@code null}, если заряд в норме
 */
public record BatteryCheckResult(boolean low, Integer batteryLevel, Integer belowBy, Integer lowerBound) {

    /**
     * Создаёт результат «заряд в норме».
     *
     * @return результат с {@code low = false} и пустыми остальными полями
     */
    public static BatteryCheckResult normal() {
        return new BatteryCheckResult(false, null, null, null);
    }
}