package com.iotplatform.notification.data;

import com.iotplatform.notification.model.TelemetryLimit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Границы нормы устройства для команды /settings. Методы пишет тиммейт (см. StubDeviceLimits). */
public interface DeviceLimits {
    /** Нижняя граница заряда батареи устройства, %; пусто, если не задана. */
    Optional<Integer> batteryLowerBound(UUID deviceId);

    /** Один элемент = один тип телеметрии со своими границами. */
    List<TelemetryLimit> telemetryLimits(UUID deviceId);
}