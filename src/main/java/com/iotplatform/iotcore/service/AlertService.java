package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.check.BatteryCheckResult;
import com.iotplatform.iotcore.check.BatteryChecker;
import com.iotplatform.iotcore.check.TelemetryCheckResult;
import com.iotplatform.iotcore.check.TelemetryNormChecker;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.repository.DeviceRepository;

import java.util.UUID;

/**
 * Проверки на норму после того, как данные прошли валидацию и сохранены.
 * Если показание или заряд вне нормы, результат проверки уходит в сервис уведомлений.
 * Ошибка внутри проверки не ломает запрос: данные уже сохранены, поэтому ошибку только пишем в лог.
 */
public class AlertService {
    private static final System.Logger LOG = System.getLogger(AlertService.class.getName());

    private final TelemetryNormChecker telemetryChecker;
    private final BatteryChecker batteryChecker;
    private final DeviceRepository devices;
    private final NotificationService notifications;

    public AlertService(TelemetryNormChecker telemetryChecker, BatteryChecker batteryChecker,
                        DeviceRepository devices, NotificationService notifications) {
        this.telemetryChecker = telemetryChecker;
        this.batteryChecker = batteryChecker;
        this.devices = devices;
        this.notifications = notifications;
    }

    /** Показание вне нормы: отправляем в сервис уведомлений каждый раз. */
    public void onTelemetry(TelemetryDto t) {
        try {
            TelemetryCheckResult r = telemetryChecker.check(t.deviceId(), t.telemetryType(), t.value());
            if (r == null || !r.outOfNorm()) {
                LOG.log(System.Logger.Level.DEBUG, "Телеметрия в норме: устройство " + t.deviceId());
                return;
            }
            LOG.log(System.Logger.Level.INFO, "Телеметрия вне нормы: устройство " + t.deviceId()
                    + ", тип " + t.telemetryType() + ", " + r + " -> отправляем уведомление");
            notifications.telemetryAlert(t, r);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Ошибка проверки телеметрии устройства " + t.deviceId(), e);
        }
    }

    /**
     * Заряд ниже нормы: уведомление отправляем один раз. Флаг battery_notify_allowed становится false.
     * Когда заряд снова в норме (выше нижней границы), флаг возвращается в true, и в следующий раз опять сообщим.
     */
    public void onBattery(UUID deviceId, Integer batteryLevel) {
        if (deviceId == null || batteryLevel == null) return;
        try {
            BatteryCheckResult r = batteryChecker.check(deviceId, batteryLevel);
            if (r == null || !r.low()) {
                if (devices.enableBatteryNotification(deviceId)) {
                    LOG.log(System.Logger.Level.INFO, "Заряд устройства " + deviceId + " снова в норме ("
                            + batteryLevel + "%): уведомление о разряде снова разрешено");
                }
                return;
            }
            if (devices.disableBatteryNotification(deviceId)) {
                LOG.log(System.Logger.Level.INFO, "Низкий заряд устройства " + deviceId + ": " + r
                        + " -> отправляем уведомление");
                notifications.batteryLow(deviceId, r);
            } else {
                LOG.log(System.Logger.Level.INFO, "Низкий заряд устройства " + deviceId + " (" + batteryLevel
                        + "%): уведомление уже отправляли, повторно не шлём");
            }
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Ошибка проверки заряда устройства " + deviceId, e);
        }
    }
}