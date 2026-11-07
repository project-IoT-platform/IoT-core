package com.iotplatform.notification.email;

import com.iotplatform.notification.channel.Channel;
import com.iotplatform.notification.channel.EmailChannel;
import com.iotplatform.notification.config.Config;
import com.iotplatform.notification.fake.Fakes;
import com.iotplatform.notification.model.Device;
import com.iotplatform.notification.model.Notifications;
import com.iotplatform.notification.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Письма: рендер по шаблону и какие типы уходят по почте. SMTP подменён. */
class EmailTest {
    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    record Sent(String to, String subject, String html, String text) {}

    static class FakeSender implements EmailSender {
        final List<Sent> sent = new ArrayList<>();
        @Override public boolean isConfigured() { return true; }
        @Override public void send(String to, String subject, String html, String text) {
            sent.add(new Sent(to, subject, html, text));
        }
    }

    private EmailRenderer renderer;
    private Fakes.Platform platform;
    private FakeSender sender;
    private EmailChannel channel;
    private User user;
    private Device device;

    @BeforeEach
    void setUp() {
        renderer = new EmailRenderer(Config.fromMap(Map.of("PLATFORM_NAME", "IoT", "NOTIFICATION_ZONE", "Europe/Moscow")));
        platform = new Fakes.Platform();
        sender = new FakeSender();
        channel = new EmailChannel(sender, renderer, platform);
        user = platform.addUser(false);
        device = platform.addDevice(user.id());
    }

    @Test
    void userRegistrationEmailHasIdAndNoDeviceLine() throws Exception {
        assertEquals(Channel.Status.SENT, channel.send(Notifications.userRegistered(user.id(), "vera", NOW)));

        Sent mail = sender.sent.get(0);
        assertEquals(user.email(), mail.to());
        assertEquals("IoT: Вы зарегистрированы на платформе", mail.subject());   // без « — »
        assertTrue(mail.html().contains(user.id().toString()));
        assertFalse(mail.html().contains("Устройство:"));
        assertTrue(mail.text().contains(user.id().toString()));
    }

    @Test
    void lowBatteryNowGoesByEmail() throws Exception {
        assertEquals(Channel.Status.SENT, channel.send(Notifications.lowBattery(device, 12, 8, 20, NOW)));
        Sent mail = sender.sent.get(0);
        assertEquals("IoT: Низкий заряд батареи — Термометр", mail.subject());
        assertTrue(mail.html().contains("Устройство: <b>Термометр</b>"));
        assertTrue(mail.html().contains("20%"));
    }

    @Test
    void telemetryEmailSaysLessThanNorm() throws Exception {
        channel.send(Notifications.telemetryAlert(device,
                new com.iotplatform.notification.model.Reading(device.id(), NOW, (short) 1, 15.0), -3.0, 18.0, null, NOW));
        Sent mail = sender.sent.get(0);
        assertTrue(mail.html().contains("Телеметрия меньше нормы на 3"));
        assertTrue(mail.text().contains("Телеметрия меньше нормы на 3"));
    }

    @Test
    void statusIsNotSentByEmail() throws Exception {
        assertEquals(Channel.Status.SKIPPED, channel.send(Notifications.deviceStatus(device, NOW)));
        assertTrue(sender.sent.isEmpty());
    }

    @Test
    void deviceNameIsEscaped() {
        Device evil = new Device(device.id(), user.id(), "<script>", null, null, true, null, null);
        String html = renderer.render(Notifications.lowBattery(evil, 1, 1, 2, NOW)).html();
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void userWithoutEmailIsNoRecipient() throws Exception {
        User noMail = new User(java.util.UUID.randomUUID(), "x", null, false);
        platform.users.put(noMail.id(), noMail);
        assertEquals(Channel.Status.NO_RECIPIENT, channel.send(Notifications.userRegistered(noMail.id(), "x", NOW)));
    }
}