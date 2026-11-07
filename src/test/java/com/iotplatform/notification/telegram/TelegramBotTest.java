package com.iotplatform.notification.telegram;

import com.iotplatform.notification.fake.Fakes;
import com.iotplatform.notification.model.Device;
import com.iotplatform.notification.model.NormOperator;
import com.iotplatform.notification.model.TelemetryLimit;
import com.iotplatform.notification.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Ответы бота проверяем через handle(), без сети: TelegramClient без токена ничего не отправляет. */
class TelegramBotTest {
    private static final long CHAT = 42L;

    private Fakes.World w;
    private TelegramBot bot;
    private User user;
    private Device device;

    @BeforeEach
    void setUp() {
        w = new Fakes.World();
        bot = new TelegramBot(new TelegramClient(null, null, null), w.service, w.platform, w.links, ZoneId.of("UTC"));
        user = w.platform.addUser(false);
        device = w.platform.addDevice(user.id());
        w.links.save(CHAT, user.id());
    }

    @Test
    void unknownChatIsAskedForId() {
        assertTrue(bot.handle(7L, "/help").contains("отправьте ваш id"));
    }

    @Test
    void sendingUserIdLinksChat() {
        User other = w.platform.addUser(false);
        String answer = bot.handle(8L, other.id().toString());
        assertTrue(answer.startsWith("Готово"));
        assertEquals(other.id(), w.links.findUserId(8L).orElseThrow());
    }

    @Test
    void helpListsNewNormAndNoDeviation() {
        String help = bot.handle(CHAT, "/help");
        assertTrue(help.contains("/norm <id устройства> <тип телеметрии> <>= или <=> <число>"));
        assertFalse(help.contains("/deviation"));
    }

    @Test
    void normCreatesOrUpdatesRule() {
        String answer = bot.handle(CHAT, "/norm " + device.id() + " 1 <= 30,5");

        assertEquals("Норма добавлена: тип 1 <= 30.5", answer);
        assertEquals(List.of(new Fakes.Norms.Call(device.id(), (short) 1, NormOperator.LE, 30.5)), w.norms.calls);
    }

    @Test
    void normWithGreaterOrEqual() {
        bot.handle(CHAT, "/norm " + device.id() + " 2 >= -5");
        assertEquals(NormOperator.GE, w.norms.calls.get(0).op());
        assertEquals(-5.0, w.norms.calls.get(0).value());
    }

    @Test
    void normWithWrongOperator() {
        assertTrue(bot.handle(CHAT, "/norm " + device.id() + " 1 > 30").startsWith("Оператор должен быть >= или <="));
        assertTrue(w.norms.calls.isEmpty());
    }

    @Test
    void normWithWrongType() {
        assertTrue(bot.handle(CHAT, "/norm " + device.id() + " темп <= 30").startsWith("Тип телеметрии"));
    }

    @Test
    void normWithWrongArgumentCountShowsUsage() {
        assertTrue(bot.handle(CHAT, "/norm " + device.id() + " 30").startsWith("Формат: /norm"));
        assertTrue(bot.handle(CHAT, "/norm").startsWith("Формат: /norm"));
    }

    @Test
    void normWithBadNumberOrIdIsFormatError() {
        assertTrue(bot.handle(CHAT, "/norm " + device.id() + " 1 <= abc").startsWith("Неверный формат"));
        assertTrue(bot.handle(CHAT, "/norm not-uuid 1 <= 3").startsWith("Неверный формат"));
    }

    @Test
    void normOnForeignDeviceIsNotFound() {
        Device foreign = w.platform.addDevice(UUID.randomUUID());
        assertTrue(bot.handle(CHAT, "/norm " + foreign.id() + " 1 <= 3").startsWith("Устройство не найдено"));
        assertTrue(w.norms.calls.isEmpty());
    }

    @Test
    void deviationCommandIsGone() {
        assertTrue(bot.handle(CHAT, "/deviation " + device.id() + " 3").startsWith("Неизвестная команда"));
    }

    @Test
    void settingsCommand() {
        w.limits.battery.put(device.id(), 15);
        w.limits.telemetry.put(device.id(), List.of(new TelemetryLimit((short) 1, 18.0, 25.0)));

        String answer = bot.handle(CHAT, "/settings " + device.id());

        assertTrue(answer.contains("Нижняя граница заряда батареи: 15%"));
        assertTrue(answer.contains("тип 1: нижняя граница 18: верхняя граница 25"));
    }

    @Test
    void statusCommandStillWorks() {
        assertEquals("Устройство Термометр: ВКЛ", bot.handle(CHAT, "/status " + device.id()));
    }
}