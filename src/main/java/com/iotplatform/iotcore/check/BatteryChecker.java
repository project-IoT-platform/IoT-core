package com.iotplatform.iotcore.check;

import java.util.UUID;

/** Проверка заряда батареи: id устройства и заряд. */
public interface BatteryChecker {
    BatteryCheckResult check(UUID deviceId, int batteryLevel);
}