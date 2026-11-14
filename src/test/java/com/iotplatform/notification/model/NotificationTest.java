package com.iotplatform.notification.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationsTest {
    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    private final Device device = new Device(UUID.randomUUID(), UUID.randomUUID(), "Термометр", "Acme", 12,
            true, null, null);

    @Test
    void deviationTextSaysLessOrMore() {
        assertEquals("Телеметрия меньше нормы на 3", Notifications.deviationText(-3.0));
        assertEquals("Телеметрия больше нормы на 5.5", Notifications.deviationText(5.5));
        assertEquals("Телеметрия вне нормы", Notifications.deviationText(null));
        assertEquals("Телеметрия вне нормы", Notifications.deviationText(0.0));
    }

    @Test
    void numbersAreFormattedWithoutNoise() {
        assertEquals("5", Notifications.format(5.0));
        assertEquals("2.5", Notifications.format(2.50));
        assertEquals("0.3", Notifications.format(0.1 + 0.2));   // а не 0.30000000000000004
        assertEquals("-3.13", Notifications.format(-3.125));
        assertEquals("—", Notifications.format(null));
    }

    @Test
    void telemetryAboveNorm() {
        Reading r = new Reading(device.id(), NOW, (short) 1, 30.0);
        Notification n = Notifications.telemetryAlert(device, r, 5.0, 18.0, 25.0, NOW);

        assertEquals(NotificationType.TELEMETRY_ALERT, n.type());
        assertEquals(device.userId(), n.userId());
        assertEquals("Телеметрия больше нормы на 5.", n.description());
        assertEquals("30", n.details().get("Показание"));
        assertEquals("18", n.details().get("Нижняя граница нормы"));
        assertEquals("25", n.details().get("Верхняя граница нормы"));
        assertTrue(n.telegramText().contains("Телеметрия больше нормы на 5"));
    }

    @Test
    void telemetryBelowNormWithOnlyLowerBound() {
        Reading r = new Reading(device.id(), NOW, (short) 1, 15.0);
        Notification n = Notifications.telemetryAlert(device, r, -3.0, 18.0, null, NOW);

        assertEquals("Телеметрия меньше нормы на 3", n.details().get("Отклонение"));
        assertTrue(n.details().containsKey("Нижняя граница нормы"));
        assertFalse(n.details().containsKey("Верхняя граница нормы"));
    }

    @Test
    void telemetryUsesReadingTime() {
        Instant t = Instant.parse("2026-11-07T09:00:00Z");
        assertEquals(t, Notifications.telemetryAlert(device, new Reading(device.id(), t, (short) 1, 1.0),
                1.0, null, null, NOW).time());
    }

    @Test
    void lowBatteryHasBoundAndDifference() {
        Notification n = Notifications.lowBattery(device, 12, 8, 20, NOW);
        assertEquals(NotificationType.LOW_BATTERY, n.type());
        assertEquals("12%", n.details().get("Заряд"));
        assertEquals("20%", n.details().get("Нижняя допустимая граница"));
        assertEquals("8%", n.details().get("Ниже границы на"));
        assertTrue(n.telegramText().contains("нижняя граница 20%, ниже на 8%"), n.telegramText());
    }

    @Test
    void lowBatteryWithoutBounds() {
        Notification n = Notifications.lowBattery(device, 12, null, null, NOW);
        assertEquals(1, n.details().size());
        assertFalse(n.telegramText().contains("null"));
    }

    @Test
    void userRegisteredContainsUserId() {
        UUID id = UUID.randomUUID();
        Notification n = Notifications.userRegistered(id, "vera", NOW);
        assertEquals(NotificationType.USER_REGISTERED, n.type());
        assertEquals(id, n.userId());
        assertNull(n.deviceId());
        assertEquals("", n.deviceName());
        assertEquals(id.toString(), n.details().get("ID пользователя"));
        assertTrue(n.telegramText().contains(id.toString()));
    }

    @Test
    void emailTypes() {
        assertTrue(NotificationType.DEVICE_REGISTERED.byEmail());
        assertTrue(NotificationType.USER_REGISTERED.byEmail());
        assertTrue(NotificationType.LOW_BATTERY.byEmail());
        assertTrue(NotificationType.TELEMETRY_ALERT.byEmail());
        assertFalse(NotificationType.DEVICE_STATUS.byEmail());
    }

    @Test
    void normOperatorParsing() {
        assertEquals(NormOperator.GE, NormOperator.fromSymbol(">=").orElseThrow());
        assertEquals(NormOperator.LE, NormOperator.fromSymbol("<=").orElseThrow());
        assertEquals(NormOperator.LE, NormOperator.fromSymbol("≤").orElseThrow());
        assertTrue(NormOperator.fromSymbol(">").isEmpty());
        assertTrue(NormOperator.fromSymbol("=").isEmpty());
        assertTrue(NormOperator.fromSymbol(null).isEmpty());
    }
}