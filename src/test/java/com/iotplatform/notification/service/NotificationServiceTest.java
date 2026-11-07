package com.iotplatform.notification.service;

import com.iotplatform.notification.data.NormRules;
import com.iotplatform.notification.exception.NotFoundException;
import com.iotplatform.notification.exception.ValidationException;
import com.iotplatform.notification.fake.Fakes;
import com.iotplatform.notification.model.Device;
import com.iotplatform.notification.model.NormOperator;
import com.iotplatform.notification.model.Notification;
import com.iotplatform.notification.model.NotificationType;
import com.iotplatform.notification.model.Reading;
import com.iotplatform.notification.model.TelemetryLimit;
import com.iotplatform.notification.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationServiceTest {
    private Fakes.World w;
    private User owner;
    private Device device;

    @BeforeEach
    void setUp() {
        w = new Fakes.World();
        owner = w.platform.addUser(false);
        device = w.platform.addDevice(owner.id());
    }

    // ---------- события от IoT Core ----------

    @Test
    void batteryLowIsSentToOwner() {
        w.service.batteryLow(device.id(), 12, 8, 20);

        assertEquals(1, w.channel.sent.size());
        Notification n = w.channel.sent.get(0);
        assertEquals(NotificationType.LOW_BATTERY, n.type());
        assertEquals(owner.id(), n.userId());
        assertEquals("20%", n.details().get("Нижняя допустимая граница"));
    }

    @Test
    void batteryLowIsSentEveryTimeIoTCoreAsks() {
        // повторы отсекает IoT Core (флаг battery_notify_allowed), здесь лишней логики нет
        w.service.batteryLow(device.id(), 12, 8, 20);
        w.service.batteryLow(device.id(), 12, 8, 20);
        assertEquals(2, w.channel.sent.size());
    }

    @Test
    void batteryLowForUnknownOrOwnerlessDeviceIsSkipped() {
        w.service.batteryLow(UUID.randomUUID(), 12, 8, 20);
        Device ownerless = w.platform.addDevice(null);
        w.service.batteryLow(ownerless.id(), 12, 8, 20);
        assertTrue(w.channel.sent.isEmpty());
    }

    @Test
    void telemetryAlertIsSentWithDeviationText() {
        Reading r = new Reading(device.id(), Instant.parse("2026-11-07T09:00:00Z"), (short) 1, 15.0);
        w.service.telemetryAlert(r, -3.0, 18.0, null);

        Notification n = w.channel.sent.get(0);
        assertEquals(NotificationType.TELEMETRY_ALERT, n.type());
        assertEquals("Телеметрия меньше нормы на 3", n.details().get("Отклонение"));
    }

    @Test
    void telemetryAlertForUnknownDeviceIsSkipped() {
        w.service.telemetryAlert(new Reading(UUID.randomUUID(), Instant.now(), (short) 1, 1.0), 1.0, null, null);
        assertTrue(w.channel.sent.isEmpty());
    }

    @Test
    void userRegisteredSendsNotification() {
        UUID id = UUID.randomUUID();
        w.service.notifyUserRegistered(id, "vera");
        Notification n = w.channel.sent.get(0);
        assertEquals(NotificationType.USER_REGISTERED, n.type());
        assertEquals(id, n.userId());
    }

    @Test
    void deviceRegisteredOnlyWithOwner() {
        w.service.notifyRegistered(w.platform.addDevice(null));
        assertTrue(w.channel.sent.isEmpty());
        w.service.notifyRegistered(device);
        assertEquals(NotificationType.DEVICE_REGISTERED, w.channel.sent.get(0).type());
    }

    // ---------- /settings ----------

    @Test
    void settingsShowsBatteryAndEachTelemetryType() {
        w.limits.battery.put(device.id(), 20);
        w.limits.telemetry.put(device.id(), List.of(
                new TelemetryLimit((short) 1, 18.0, 25.0),
                new TelemetryLimit((short) 2, null, 80.0),
                new TelemetryLimit((short) 3, 0.5, null)));

        String text = w.service.settingsText(device.id(), owner);

        assertEquals("""
                Устройство Термометр
                Нижняя граница заряда батареи: 20%
                Границы телеметрии:
                тип 1: нижняя граница 18: верхняя граница 25
                тип 2: верхняя граница 80
                тип 3: нижняя граница 0.5""", text);
    }

    @Test
    void settingsWithStubShowsNothingSet() {
        String text = w.service.settingsText(device.id(), owner);
        assertTrue(text.contains("Нижняя граница заряда батареи: не задана"));
        assertTrue(text.contains("Границы телеметрии не заданы"));
    }

    @Test
    void settingsOfForeignDeviceIsHidden() {
        User stranger = w.platform.addUser(false);
        assertThrows(NotFoundException.class, () -> w.service.settingsText(device.id(), stranger));
    }

    // ---------- /norm ----------

    @Test
    void setNormPassesEverythingToNormRules() {
        String answer = w.service.setNorm(device.id(), owner, (short) 1, NormOperator.LE, 30.0);

        assertEquals(List.of(new Fakes.Norms.Call(device.id(), (short) 1, NormOperator.LE, 30.0)), w.norms.calls);
        assertEquals("Норма добавлена: тип 1 <= 30", answer);
    }

    @Test
    void setNormSaysUpdatedWhenRuleExisted() {
        w.norms.result = NormRules.Result.UPDATED;
        assertEquals("Норма изменена: тип 2 >= 1.5",
                w.service.setNorm(device.id(), owner, (short) 2, NormOperator.GE, 1.5));
    }

    @Test
    void adminCanSetNormOnAnyDevice() {
        User admin = w.platform.addUser(true);
        assertDoesNotThrow(() -> w.service.setNorm(device.id(), admin, (short) 1, NormOperator.LE, 30.0));
    }

    @Test
    void strangerCannotSetNorm() {
        User stranger = w.platform.addUser(false);
        assertThrows(NotFoundException.class,
                () -> w.service.setNorm(device.id(), stranger, (short) 1, NormOperator.LE, 30.0));
        assertTrue(w.norms.calls.isEmpty());
    }

    @Test
    void setNormRejectsNotANumber() {
        assertThrows(ValidationException.class,
                () -> w.service.setNorm(device.id(), owner, (short) 1, NormOperator.LE, Double.NaN));
        assertTrue(w.norms.calls.isEmpty());
    }

    @Test
    void setNormForUnknownDevice() {
        assertThrows(NotFoundException.class,
                () -> w.service.setNorm(UUID.randomUUID(), owner, (short) 1, NormOperator.LE, 1.0));
    }
}