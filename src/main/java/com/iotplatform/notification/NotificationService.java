package com.iotplatform.notification;

import com.iotplatform.notification.channel.NotificationDispatcher;
import com.iotplatform.notification.exception.NotificationValidationException;
import com.iotplatform.notification.exception.UnknownDeviceException;
import com.iotplatform.notification.model.NotificationEvents;
import com.iotplatform.notification.port.PlatformData;
import com.iotplatform.notification.port.PlatformData.DeviceInfo;
import com.iotplatform.notification.port.PlatformData.DeviceStatus;
import com.iotplatform.notification.port.PlatformData.TelemetryReading;
import com.iotplatform.notification.port.PlatformData.UserInfo;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Ядро уведомлений: правила (батарея, телеметрия, настройки) и отправка через каналы. */
public class NotificationService {
    private static final System.Logger LOG = System.getLogger(NotificationService.class.getName());

    private static final int LOW_BATTERY_PERCENT = 15;
    private static final Duration AVERAGE_WINDOW = Duration.ofHours(1);

    public record Settings(Double norm, Double deviation) {}

    private final PlatformData platform;
    private final NotificationDispatcher dispatcher;

    private volatile double defaultNorm;
    private volatile double defaultDeviation;
    private final Map<UUID, Settings> userSettings = new ConcurrentHashMap<>();
    private final Set<UUID> lowBatteryNotified = ConcurrentHashMap.newKeySet();

    public NotificationService(PlatformData platform, NotificationDispatcher dispatcher,
                               double norm, double deviation) {
        this.platform = platform;
        this.dispatcher = dispatcher;
        this.defaultNorm = norm;
        this.defaultDeviation = deviation;
    }

    public void notifyDeviceRegistered(DeviceInfo device) {
        safe(() -> {
            if (device.ownerUserId() == null) return;
            dispatcher.dispatchAsync(NotificationEvents.deviceRegistered(device, Instant.now()));
        });
    }

    public void checkBattery(DeviceInfo device) {
        Integer level = device.batteryLevel();
        UUID id = device.id();
        if (level == null || id == null) return;

        if (level >= LOW_BATTERY_PERCENT) {
            lowBatteryNotified.remove(id);
            return;
        }
        if (device.ownerUserId() == null) return;
        if (!lowBatteryNotified.add(id)) return;

        safe(() -> dispatcher.dispatchAsync(
                NotificationEvents.lowBattery(device, level, LOW_BATTERY_PERCENT, Instant.now())));
    }

    public void checkTelemetry(TelemetryReading t) {
        if (t.value() == null) return;
        safe(() -> {
            DeviceInfo device = platform.findDevice(t.deviceId()).orElse(null);
            if (device == null || device.ownerUserId() == null) return;

            Settings s = settingsFor(device.ownerUserId());
            if (Math.abs(t.value() - s.norm()) <= s.deviation()) return;

            dispatcher.dispatchAsync(
                    NotificationEvents.telemetryAlert(device, t, s.norm(), s.deviation(), Instant.now()));
        });
    }

    public String deviceStatusText(UUID deviceId, UserInfo requester) {
        return NotificationEvents.statusLine(deviceFor(deviceId, requester));
    }

    /** Среднее за последний час (прежнее поведение). */
    public String deviceAverageText(UUID deviceId, UserInfo requester) {
        DeviceInfo device = deviceFor(deviceId, requester);
        OptionalDouble avg = platform.averageValueSince(deviceId, Instant.now().minus(AVERAGE_WINDOW));
        if (avg.isEmpty()) {
            return "Устройство " + device.name() + ": за последний час показаний нет";
        }
        return String.format(Locale.ROOT, "Устройство %s: среднее за последний час: %.2f",
                device.name(), avg.getAsDouble());
    }

    /** Среднее значение телеметрии начиная с момента since (условие recorded_at >= since). */
    public String deviceAverageText(UUID deviceId, UserInfo requester, Instant since, ZoneId zone) {
        DeviceInfo device = deviceFor(deviceId, requester);
        OptionalDouble avg = platform.averageValueSince(deviceId, since);
        String from = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(zone).format(since)
                + " (" + zone.getId() + ")";
        if (avg.isEmpty()) {
            return "Устройство " + device.name() + ": с " + from + " показаний нет";
        }
        return String.format(Locale.ROOT, "Устройство %s: среднее с %s: %.2f",
                device.name(), from, avg.getAsDouble());
    }

    public DeviceStatus sendDeviceStatus(UUID deviceId) {
        DeviceInfo device = platform.findDevice(deviceId)
                .orElseThrow(() -> new UnknownDeviceException(deviceId));
        if (device.ownerUserId() != null) {
            dispatcher.dispatchAsync(NotificationEvents.deviceStatus(device, Instant.now()));
        }
        return device.status();
    }

    private DeviceInfo deviceFor(UUID deviceId, UserInfo requester) {
        DeviceInfo device = platform.findDevice(deviceId)
                .orElseThrow(() -> new UnknownDeviceException(deviceId));
        boolean allowed = requester.admin() || requester.id().equals(device.ownerUserId());
        if (!allowed) throw new UnknownDeviceException(deviceId);   // чужое не раскрываем
        return device;
    }

    public Settings settingsFor(UUID userId) {
        Settings own = userSettings.get(userId);
        double norm = own != null && own.norm() != null ? own.norm() : defaultNorm;
        double dev = own != null && own.deviation() != null ? own.deviation() : defaultDeviation;
        return new Settings(norm, dev);
    }

    public synchronized Settings updateUserSettings(UUID userId, Settings s) {
        validate(s);
        Settings current = settingsFor(userId);
        userSettings.put(userId, new Settings(
                s.norm() != null ? s.norm() : current.norm(),
                s.deviation() != null ? s.deviation() : current.deviation()));
        return settingsFor(userId);
    }

    public String settingsText(UUID userId) {
        Settings s = settingsFor(userId);
        return "Норма: " + s.norm() + "\nДопустимое отклонение: " + s.deviation();
    }

    public Settings getDefaults() {
        return new Settings(defaultNorm, defaultDeviation);
    }

    public synchronized Settings updateDefaults(Settings s) {
        validate(s);
        if (s.norm() != null) defaultNorm = s.norm();
        if (s.deviation() != null) defaultDeviation = s.deviation();
        return getDefaults();
    }

    private void validate(Settings s) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (s.norm() == null && s.deviation() == null) {
            errors.put("body", "укажите norm и/или deviation");
        }
        if (s.norm() != null && !Double.isFinite(s.norm())) {
            errors.put("norm", "должно быть числом");
        }
        if (s.deviation() != null && (!Double.isFinite(s.deviation()) || s.deviation() < 0)) {
            errors.put("deviation", "должно быть числом не меньше 0");
        }
        if (!errors.isEmpty()) throw new NotificationValidationException(errors);
    }

    private void safe(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Не удалось отправить уведомление", e);
        }
    }
}