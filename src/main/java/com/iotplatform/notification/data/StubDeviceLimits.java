package com.iotplatform.notification.data;

import com.iotplatform.notification.model.TelemetryLimit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ЗАГЛУШКА. Сюда вставляются методы тиммейта:
 *   batteryLowerBound(id устройства) -> нижняя граница батарейки;
 *   telemetryLimits(id устройства)   -> список «тип телеметрии + нижняя/верхняя граница».
 * Пока возвращает «не задано» и пустой список. Подключается в Main: new StubDeviceLimits().
 */
public class StubDeviceLimits implements DeviceLimits {
    @Override
    public Optional<Integer> batteryLowerBound(UUID deviceId) {
        // TODO: нижняя граница заряда по id устройства
        return Optional.empty();
    }

    @Override
    public List<TelemetryLimit> telemetryLimits(UUID deviceId) {
        // TODO: границы по каждому типу телеметрии устройства
        return List.of();
    }
}