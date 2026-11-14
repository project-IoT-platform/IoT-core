package com.iotplatform.notification.fake;

import com.iotplatform.notification.channel.Channel;
import com.iotplatform.notification.channel.Dispatcher;
import com.iotplatform.notification.data.ChatLinks;
import com.iotplatform.notification.data.DeviceLimits;
import com.iotplatform.notification.data.NormRules;
import com.iotplatform.notification.data.PlatformData;
import com.iotplatform.notification.data.RuleStore;
import com.iotplatform.notification.data.SettingsStore;
import com.iotplatform.notification.model.Device;
import com.iotplatform.notification.model.NormOperator;
import com.iotplatform.notification.model.Notification;
import com.iotplatform.notification.model.Rule;
import com.iotplatform.notification.model.Settings;
import com.iotplatform.notification.model.TelemetryLimit;
import com.iotplatform.notification.model.User;
import com.iotplatform.notification.service.NotificationService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Фейки для тестов сервиса уведомлений: всё в памяти, без БД, Telegram и SMTP. */
public final class Fakes {
    private Fakes() {}

    public static class Platform implements PlatformData {
        public final Map<UUID, Device> devices = new HashMap<>();
        public final Map<UUID, User> users = new HashMap<>();
        public OptionalDouble average = OptionalDouble.empty();

        public Device addDevice(UUID owner) {
            Device d = new Device(UUID.randomUUID(), owner, "Термометр", "Acme", 50, true, null, null);
            devices.put(d.id(), d);
            return d;
        }

        public User addUser(boolean admin) {
            User u = new User(UUID.randomUUID(), "vera", "vera@example.com", admin);
            users.put(u.id(), u);
            return u;
        }

        @Override public Optional<Device> findDevice(UUID id) { return Optional.ofNullable(devices.get(id)); }
        @Override public Optional<User> findUser(UUID id) { return Optional.ofNullable(users.get(id)); }
        @Override public OptionalDouble averageSince(UUID deviceId, Instant since) { return average; }
    }

    public static class Links implements ChatLinks {
        public final Map<Long, UUID> byChat = new HashMap<>();

        @Override public Optional<UUID> findUserId(long chatId) { return Optional.ofNullable(byChat.get(chatId)); }

        @Override
        public Optional<Long> findChatId(UUID userId) {
            return byChat.entrySet().stream().filter(e -> e.getValue().equals(userId)).map(Map.Entry::getKey).findFirst();
        }

        @Override
        public void save(long chatId, UUID userId) {
            byChat.values().remove(userId);
            byChat.put(chatId, userId);
        }

        @Override public void delete(long chatId) { byChat.remove(chatId); }
    }

    public static class SettingsMem implements SettingsStore {
        public Settings value = new Settings(25.0, 5.0);
        @Override public Settings defaults() { return value; }
        @Override public void saveDefaults(Settings s) { value = s; }
    }

    public static class Rules implements RuleStore {
        public final Map<UUID, Rule> rows = new HashMap<>();
        @Override public Optional<Rule> find(UUID deviceId) { return Optional.ofNullable(rows.get(deviceId)); }
        @Override public void save(UUID deviceId, Rule rule) { rows.put(deviceId, rule); }
    }

    public static class Limits implements DeviceLimits {
        public final Map<UUID, Integer> battery = new HashMap<>();
        public final Map<UUID, List<TelemetryLimit>> telemetry = new HashMap<>();
        @Override public Optional<Integer> batteryLowerBound(UUID id) { return Optional.ofNullable(battery.get(id)); }
        @Override public List<TelemetryLimit> telemetryLimits(UUID id) { return telemetry.getOrDefault(id, List.of()); }
    }

    public static class Norms implements NormRules {
        public record Call(UUID deviceId, short type, NormOperator op, double value) {}
        public final List<Call> calls = new ArrayList<>();
        public Result result = Result.CREATED;

        @Override
        public Result setNorm(UUID deviceId, short telemetryType, NormOperator operator, double value) {
            calls.add(new Call(deviceId, telemetryType, operator, value));
            return result;
        }
    }

    /** Канал, который запоминает уведомления. */
    public static class RecordingChannel implements Channel {
        public final List<Notification> sent = new CopyOnWriteArrayList<>();
        @Override public String name() { return "test"; }
        @Override public boolean enabled() { return true; }
        @Override public Status send(Notification n) { sent.add(n); return Status.SENT; }
    }

    /** Всё сразу: сервис уведомлений на фейках, рассылка синхронная (Runnable::run). */
    public static class World {
        public final Platform platform = new Platform();
        public final Links links = new Links();
        public final SettingsMem settings = new SettingsMem();
        public final Rules rules = new Rules();
        public final Limits limits = new Limits();
        public final Norms norms = new Norms();
        public final RecordingChannel channel = new RecordingChannel();
        public final NotificationService service = new NotificationService(platform, settings, rules, limits, norms,
                new Dispatcher(List.of(channel), Runnable::run), 15);
    }
}