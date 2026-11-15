package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.check.BatteryCheckResult;
import com.iotplatform.iotcore.check.BatteryChecker;
import com.iotplatform.iotcore.check.TelemetryCheckResult;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.repository.DeviceRepository;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Проверки на норму после того, как данные прошли валидацию и сохранены.
 * Если показание или заряд вне нормы, результат проверки уходит в сервис уведомлений.
 * Ошибка внутри проверки не ломает запрос: данные уже сохранены, поэтому ошибку только пишем в лог.
 */
public class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final BatteryChecker batteryChecker;
    private final DeviceRepository devices;
    private final NotificationService notifications;

    public AlertService(BatteryChecker batteryChecker,
                        DeviceRepository devices, NotificationService notifications) {
        this.batteryChecker = batteryChecker;
        this.devices = devices;
        this.notifications = notifications;
    }

    public void onTelemetry(TelemetryDto t, Map<String, String> ruleViolations) {
        if (ruleViolations == null || ruleViolations.isEmpty()) {
            log.debug("Telemetry within rules: device {}", t.deviceId());
            return;
        }
        try {
            log.info("Telemetry violates rules: device {}, type {}, {} -> sending notification",
                    t.deviceId(), t.telemetryType(), ruleViolations);
            notifications.telemetryAlert(t, new TelemetryCheckResult(true, t.value(), null, null, null));
        } catch (RuntimeException e) {
            log.error("Rule violation notification failed for device {}", t.deviceId(), e);
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
                    log.info("Battery of device {} is back to normal ({}%): low battery notification enabled again",
                            deviceId, batteryLevel);
                }
                return;
            }
            if (devices.disableBatteryNotification(deviceId)) {
                log.info("Low battery on device {}: {} -> sending notification", deviceId, r);
                notifications.batteryLow(deviceId, r);
            } else {
                log.info("Low battery on device {} ({}%): notification already sent, not sending again",
                        deviceId, batteryLevel);
            }
        } catch (RuntimeException e) {
            log.error("Battery check failed for device {}", deviceId, e);
        }
    }
}