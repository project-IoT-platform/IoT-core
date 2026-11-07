package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.check.BatteryCheckResult;
import com.iotplatform.iotcore.check.StubBatteryChecker;
import com.iotplatform.iotcore.check.StubTelemetryNormChecker;
import com.iotplatform.iotcore.check.TelemetryCheckResult;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.fake.FakeDeviceRepository;
import com.iotplatform.iotcore.fake.RecordingNotificationService;
import com.iotplatform.iotcore.model.DeviceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AlertService: вызывает функции проверки (здесь вместо функций тиммейта простые лямбды)
 * и решает, что отправить в сервис уведомлений.
 */
class AlertServiceTest {
    private static final int LOWER_BOUND = 20;

    private FakeDeviceRepository devices;
    private RecordingNotificationService notifications;
    private UUID deviceId;

    // проверка заряда «как у тиммейта»: ниже 20% -> (true, заряд, на сколько ниже, 20)
    private final com.iotplatform.iotcore.check.BatteryChecker battery = (id, level) -> level < LOWER_BOUND
            ? new BatteryCheckResult(true, level, LOWER_BOUND - level, LOWER_BOUND)
            : BatteryCheckResult.normal();

    // проверка телеметрии: норма от 18 до 25
    private final com.iotplatform.iotcore.check.TelemetryNormChecker norm = (id, type, v) -> {
        if (v < 18) return new TelemetryCheckResult(true, v, v - 18, 18.0, 25.0);
        if (v > 25) return new TelemetryCheckResult(true, v, v - 25, 18.0, 25.0);
        return TelemetryCheckResult.normal();
    };

    private AlertService alerts;

    @BeforeEach
    void setUp() {
        devices = new FakeDeviceRepository();
        notifications = new RecordingNotificationService();
        deviceId = UUID.randomUUID();
        devices.save(new DeviceEntity(deviceId, null, "n", null, 50, "m", DeviceState.ON, null));
        alerts = new AlertService(norm, battery, devices, notifications);
    }

    private TelemetryDto reading(double v) {
        return new TelemetryDto(deviceId, Instant.parse("2026-11-06T12:00:00Z"), (short) 1, v);
    }

    // ---------- телеметрия ----------

    @Test
    void telemetryInNormSendsNothing() {
        alerts.onTelemetry(reading(20));
        assertTrue(notifications.telemetryAlerts.isEmpty());
    }

    @Test
    void telemetryAboveNormIsSentWithPositiveDeviation() {
        TelemetryDto t = reading(30);
        alerts.onTelemetry(t);

        assertEquals(1, notifications.telemetryAlerts.size());
        RecordingNotificationService.TelemetryEvent e = notifications.telemetryAlerts.get(0);
        assertSame(t, e.telemetry());
        assertEquals(5.0, e.result().deviation());
        assertEquals(25.0, e.result().upperBound());
    }

    @Test
    void telemetryBelowNormIsSentWithNegativeDeviation() {
        alerts.onTelemetry(reading(15));
        assertEquals(-3.0, notifications.telemetryAlerts.get(0).result().deviation());
    }

    @Test
    void telemetryAlertIsSentEveryTime() {
        alerts.onTelemetry(reading(30));
        alerts.onTelemetry(reading(31));
        assertEquals(2, notifications.telemetryAlerts.size());
    }

    @Test
    void brokenTelemetryCheckDoesNotThrow() {
        AlertService broken = new AlertService((id, type, v) -> { throw new IllegalStateException("бум"); },
                battery, devices, notifications);
        assertDoesNotThrow(() -> broken.onTelemetry(reading(30)));
        assertTrue(notifications.telemetryAlerts.isEmpty());
    }

    // ---------- батарея: уведомление один раз ----------

    @Test
    void lowBatteryIsSentOnceAndFlagBecomesFalse() {
        alerts.onBattery(deviceId, 10);

        assertEquals(1, notifications.batteryLow.size());
        BatteryCheckResult r = notifications.batteryLow.get(0).result();
        assertEquals(10, r.batteryLevel());
        assertEquals(10, r.belowBy());
        assertEquals(LOWER_BOUND, r.lowerBound());
        assertTrue(devices.batteryNotifyDisabled.contains(deviceId));
    }

    @Test
    void repeatedLowBatteryIsNotSentAgain() {
        alerts.onBattery(deviceId, 10);
        alerts.onBattery(deviceId, 8);
        alerts.onBattery(deviceId, 5);
        assertEquals(1, notifications.batteryLow.size());
    }

    @Test
    void afterChargingAboveBoundNotificationIsAllowedAgain() {
        alerts.onBattery(deviceId, 10);          // отправили, флаг false
        alerts.onBattery(deviceId, 80);          // зарядили: флаг снова true
        assertFalse(devices.batteryNotifyDisabled.contains(deviceId));

        alerts.onBattery(deviceId, 12);          // снова разрядилась: снова отправляем
        assertEquals(2, notifications.batteryLow.size());
    }

    @Test
    void normalBatterySendsNothing() {
        alerts.onBattery(deviceId, 50);
        assertTrue(notifications.batteryLow.isEmpty());
    }

    @Test
    void unknownBatteryLevelIsIgnored() {
        alerts.onBattery(deviceId, null);
        alerts.onBattery(null, 5);
        assertTrue(notifications.batteryLow.isEmpty());
    }

    @Test
    void brokenBatteryCheckDoesNotThrow() {
        AlertService broken = new AlertService(norm, (id, level) -> { throw new IllegalStateException("бум"); },
                devices, notifications);
        assertDoesNotThrow(() -> broken.onBattery(deviceId, 5));
    }

    // ---------- заглушки тиммейта ----------

    @Test
    void stubsAlwaysSayNormal() {
        assertFalse(new StubTelemetryNormChecker().check(deviceId, (short) 1, 1e9).outOfNorm());
        assertFalse(new StubBatteryChecker().check(deviceId, 0).low());
        assertEquals(new TelemetryCheckResult(false, null, null, null, null), TelemetryCheckResult.normal());
        assertEquals(new BatteryCheckResult(false, null, null, null), BatteryCheckResult.normal());
    }
}