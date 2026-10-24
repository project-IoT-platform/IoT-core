package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.DeviceState;
import com.iotplatform.iotcore.model.UserRole;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.TelegramLinkRepository;
import com.iotplatform.iotcore.repository.TelemetryRepository;
import com.iotplatform.iotcore.telegram.TelegramClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationService {
    private static final System.Logger LOG = System.getLogger(NotificationService.class.getName());

    private static final int LOW_BATTERY_PERCENT = 15;
    private static final Duration AVERAGE_WINDOW = Duration.ofHours(1);

    public record Settings(Double norm, Double deviation) {}

    private final TelegramClient telegram;
    private final DeviceRepository devices;
    private final TelegramLinkRepository links;
    private final TelemetryRepository telemetry;

    private volatile double defaultNorm;
    private volatile double defaultDeviation;
    private final Map<UUID, Settings> userSettings = new ConcurrentHashMap<>();
    private final Set<UUID> lowBatteryNotified = ConcurrentHashMap.newKeySet();

    public NotificationService(TelegramClient telegram, DeviceRepository devices,
                               TelegramLinkRepository links, TelemetryRepository telemetry,
                               double norm, double deviation) {
        this.telegram = telegram;
        this.devices = devices;
        this.links = links;
        this.telemetry = telemetry;
        this.defaultNorm = norm;
        this.defaultDeviation = deviation;
    }

    public void notifyDeviceRegistered(DeviceDto device) {
        safe(() -> {
            if (device.getUserId() == null) return;
            sendToUser(device.getUserId(), "📟 Зарегистрировано устройство\n"
                    + "Название: " + device.getName() + "\n"
                    + "ID: " + device.getDeviceId());
        });
    }

    public void checkBattery(DeviceDto device) {
        Integer level = device.getBatteryLevel();
        UUID id = device.getDeviceId();
        if (level == null || id == null) return;

        if (level >= LOW_BATTERY_PERCENT) {
            lowBatteryNotified.remove(id);
            return;
        }
        if (device.getUserId() == null) return;
        if (!lowBatteryNotified.add(id)) return;

        safe(() -> sendToUser(device.getUserId(), "🪫 Низкий заряд батареи\n"
                + "Устройство: " + device.getName() + "\n"
                + "Заряд: " + level + "% (порог " + LOW_BATTERY_PERCENT + "%)"));
    }

    public void checkTelemetry(TelemetryDto t) {
        if (t.value() == null) return;
        safe(() -> {
            DeviceEntity device = devices.findById(t.deviceId()).orElse(null);
            if (device == null || device.getUserId() == null) return;

            Settings s = settingsFor(device.getUserId());
            if (Math.abs(t.value() - s.norm()) <= s.deviation()) return;

            sendToUser(device.getUserId(), "⚠️ На устройстве " + device.getName()
                    + " показатели: " + t.value()
                    + ", норма: " + s.norm()
                    + " (допустимое отклонение: " + s.deviation() + ")");
        });
    }

    public String deviceStatusText(UUID deviceId, UserEntity requester) {
        return statusLine(deviceFor(deviceId, requester));
    }

    public String deviceAverageText(UUID deviceId, UserEntity requester) {
        DeviceEntity device = deviceFor(deviceId, requester);
        OptionalDouble avg = telemetry.averageValueSince(deviceId, Instant.now().minus(AVERAGE_WINDOW));
        if (avg.isEmpty()) {
            return "Устройство " + device.getName() + ": за последний час показаний нет";
        }
        return String.format(Locale.ROOT, "Устройство %s: среднее за последний час: %.2f",
                device.getName(), avg.getAsDouble());
    }

    public DeviceState sendDeviceStatus(UUID deviceId) {
        DeviceEntity device = devices.findById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));
        if (device.getUserId() != null) {
            sendToUser(device.getUserId(), statusLine(device));
        }
        return device.getState();
    }

    private DeviceEntity deviceFor(UUID deviceId, UserEntity requester) {
        DeviceEntity device = devices.findById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));
        boolean allowed = requester.getRole() == UserRole.ADMIN
                || requester.getId().equals(device.getUserId());
        if (!allowed) throw new DeviceNotFoundException(deviceId);   // чужое не раскрываем
        return device;
    }

    private String statusLine(DeviceEntity device) {
        String state = device.getState() == DeviceState.ON ? "ВКЛ" : "ВЫКЛ";
        return "Устройство " + device.getName() + ": " + state;
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
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    private void sendToUser(UUID userId, String text) {
        links.findChatId(userId).ifPresent(chatId -> telegram.sendMessage(chatId, text));
    }

    private void safe(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Не удалось отправить уведомление", e);
        }
    }
}