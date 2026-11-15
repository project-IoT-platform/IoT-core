package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.check.BatteryCheckResult;
import com.iotplatform.iotcore.check.StubBatteryChecker;
import com.iotplatform.iotcore.check.TelemetryCheckResult;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.fake.FakeDeviceRepository;
import com.iotplatform.iotcore.fake.RecordingNotificationService;
import com.iotplatform.iotcore.model.DeviceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
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

    private AlertService alerts;

    @BeforeEach
    void setUp() {
        devices = new FakeDeviceRepository();
        notifications = new RecordingNotificationService();
        deviceId = UUID.randomUUID();
        devices.save(new DeviceEntity(deviceId, null, "n", null, 50, "m", DeviceState.ON, null));
        alerts = new AlertService(battery, devices, notifications);
    }

    private TelemetryDto reading(double v) {
        return new TelemetryDto(deviceId, Instant.parse("2026-11-06T12:00:00Z"), (short) 1, v);
    }

    // ---------- телеметрия ----------

    private static final Map<String, String> VIOLATION = Map.of("rule_1", "значение 30.0 нарушает правило: должно быть <= 25.0");

    @Test
    void telemetryWithoutViolationsSendsNothing() {
        alerts.onTelemetry(reading(20), Map.of());
        alerts.onTelemetry(reading(20), null);
        assertTrue(notifications.telemetryAlerts.isEmpty());
    }

    @Test
    void telemetryWithViolationIsSent() {
        TelemetryDto t = reading(30);
        alerts.onTelemetry(t, VIOLATION);

        assertEquals(1, notifications.telemetryAlerts.size());
        RecordingNotificationService.TelemetryEvent e = notifications.telemetryAlerts.get(0);
        assertSame(t, e.telemetry());
        assertTrue(e.result().outOfNorm());
        assertEquals(30.0, e.result().value());
    }

    @Test
    void telemetryAlertIsSentEveryTime() {
        alerts.onTelemetry(reading(30), VIOLATION);
        alerts.onTelemetry(reading(31), VIOLATION);
        assertEquals(2, notifications.telemetryAlerts.size());
    }

    @Test
    void brokenTelemetryNotificationDoesNotThrow() {
        RecordingNotificationService failing = new RecordingNotificationService() {
            @Override
            public void telemetryAlert(TelemetryDto t, TelemetryCheckResult r) {
                throw new IllegalStateException("бум");
            }
        };
        AlertService broken = new AlertService(battery, devices, failing);
        assertDoesNotThrow(() -> broken.onTelemetry(reading(30), VIOLATION));
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
        AlertService broken = new AlertService((id, level) -> { throw new IllegalStateException("бум"); },
                devices, notifications);
        assertDoesNotThrow(() -> broken.onBattery(deviceId, 5));
    }

    // ---------- заглушки тиммейта ----------

    @Test
    void stubsAlwaysSayNormal() {
        assertFalse(new StubBatteryChecker().check(deviceId, 0).low());
        assertEquals(new TelemetryCheckResult(false, null, null, null, null), TelemetryCheckResult.normal());
        assertEquals(new BatteryCheckResult(false, null, null, null), BatteryCheckResult.normal());
    }
}