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
 * Формирует события о нарушениях правил и разряде в транзакции записи данных.
 */
public class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final BatteryChecker batteryChecker;
    private final DeviceRepository devices;
    private final NotificationService notifications;

    /**
     * Создаёт сервис.
     *
     * @param batteryChecker проверка заряда батареи
     * @param devices хранилище устройств (хранит флаг «уведомление о заряде уже отправлено»)
     * @param notifications клиент сервиса уведомлений
     */
    public AlertService(BatteryChecker batteryChecker,
                        DeviceRepository devices, NotificationService notifications) {
        this.batteryChecker = batteryChecker;
        this.devices = devices;
        this.notifications = notifications;
    }

    public void onTelemetry(TelemetryDto telemetry, Map<String, String> ruleViolations) {
        if (ruleViolations == null || ruleViolations.isEmpty()) return;

        log.info("Telemetry violates rules: device {}, type {}, {}",
                telemetry.deviceId(), telemetry.telemetryType(), ruleViolations);
        notifications.telemetryAlert(telemetry,
                new TelemetryCheckResult(true, telemetry.value(), null, null, null));
    }

    public void onBattery(UUID deviceId, Integer batteryLevel) {
        if (deviceId == null || batteryLevel == null) return;

        BatteryCheckResult result = batteryChecker.check(deviceId, batteryLevel);
        if (!result.low()) {
            devices.enableBatteryNotification(deviceId);
        } else if (devices.disableBatteryNotification(deviceId)) {
            notifications.batteryLow(deviceId, result);
        }
    }
}