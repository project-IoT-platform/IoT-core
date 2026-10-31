package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.model.DeviceState;

import java.util.UUID;

/**
 * Контракт IoT Core с NotificationService. Раньше здесь была реализация с Telegram.
 * Сейчас реализация: iotcore.notification.NotificationServiceAdapter (вызов в том же процессе).
 * Когда тиммейты выберут транспорт (HTTP/очередь), заменяется только реализация интерфейса.
 */
public interface NotificationService {

    record Settings(Double norm, Double deviation) {}

    void notifyDeviceRegistered(DeviceDto device);

    void checkBattery(DeviceDto device);

    void checkTelemetry(TelemetryDto telemetry);

    /** Отправляет владельцу статус устройства и возвращает его состояние. */
    DeviceState sendDeviceStatus(UUID deviceId);

    Settings getDefaults();

    Settings updateDefaults(Settings settings);
}