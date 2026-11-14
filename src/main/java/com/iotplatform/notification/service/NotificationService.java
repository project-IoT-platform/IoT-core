package com.iotplatform.notification.service;

import com.iotplatform.notification.channel.Dispatcher;
import com.iotplatform.notification.data.DeviceLimits;
import com.iotplatform.notification.data.NormRules;
import com.iotplatform.notification.data.PlatformData;
import com.iotplatform.notification.data.RuleStore;
import com.iotplatform.notification.data.SettingsStore;
import com.iotplatform.notification.exception.NotFoundException;
import com.iotplatform.notification.exception.ValidationException;
import com.iotplatform.notification.model.Device;
import com.iotplatform.notification.model.NormOperator;
import com.iotplatform.notification.model.Notifications;
import com.iotplatform.notification.model.Reading;
import com.iotplatform.notification.model.Rule;
import com.iotplatform.notification.model.Settings;
import com.iotplatform.notification.model.TelemetryLimit;
import com.iotplatform.notification.model.User;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Главная логика сервиса уведомлений. ИЗМЕНЕНО: проверки на норму теперь делает IoT Core,
 * сюда приходят готовые результаты (battery-low, telemetry-alert), и сервис только формирует и рассылает
 * уведомления. Ответы для бота: /status, /avg, /settings, /norm, /battery.
 */
public class NotificationService {
    private static final System.Logger LOG = System.getLogger(NotificationService.class.getName());

    private final PlatformData platform;
    private final SettingsStore settings;
    private final RuleStore rules;
    private final DeviceLimits limits;
    private final NormRules normRules;
    private final Dispatcher dispatcher;
    private final int defaultBatteryLimit;

    public NotificationService(PlatformData platform, SettingsStore settings, RuleStore rules,
                               DeviceLimits limits, NormRules normRules,
                               Dispatcher dispatcher, int defaultBatteryLimit) {
        this.platform = platform;
        this.settings = settings;
        this.rules = rules;
        this.limits = limits;
        this.normRules = normRules;
        this.dispatcher = dispatcher;
        this.defaultBatteryLimit = defaultBatteryLimit;
    }

    // ---- события от IoT Core ----

    public void notifyRegistered(Device d) {
        if (d.userId() == null) {
            LOG.log(System.Logger.Level.INFO, "Регистрация устройства " + d.id() + ": нет владельца, письмо не шлём");
            return;
        }
        LOG.log(System.Logger.Level.INFO, "Регистрация устройства " + d.id() + ": уведомляем владельца " + d.userId());
        dispatcher.send(Notifications.deviceRegistered(d, Instant.now()));
    }

    /** Новый пользователь: письмо с его id. */
    public void notifyUserRegistered(UUID userId, String username) {
        LOG.log(System.Logger.Level.INFO, "Регистрация пользователя " + userId + ": отправляем письмо с id");
        dispatcher.send(Notifications.userRegistered(userId, username, Instant.now()));
    }

    /** IoT Core уже решил, что заряд ниже нормы и что уведомление нужно (повторы он отсекает сам). */
    public void batteryLow(UUID deviceId, int level, Integer belowBy, Integer lowerBound) {
        Optional<Device> d = ownedDevice(deviceId, "низкий заряд");
        d.ifPresent(device -> dispatcher.send(
                Notifications.lowBattery(device, level, belowBy, lowerBound, Instant.now())));
    }

    /** IoT Core уже решил, что показание вне нормы. */
    public void telemetryAlert(Reading r, Double deviation, Double lowerBound, Double upperBound) {
        Optional<Device> d = ownedDevice(r.deviceId(), "отклонение телеметрии");
        d.ifPresent(device -> dispatcher.send(
                Notifications.telemetryAlert(device, r, deviation, lowerBound, upperBound, Instant.now())));
    }

    private Optional<Device> ownedDevice(UUID deviceId, String what) {
        Optional<Device> d = platform.findDevice(deviceId);
        if (d.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, what + ": устройство " + deviceId + " не найдено, уведомление не шлём");
            return Optional.empty();
        }
        if (d.get().userId() == null) {
            LOG.log(System.Logger.Level.INFO, what + ": у устройства " + deviceId + " нет владельца, уведомлять некого");
            return Optional.empty();
        }
        LOG.log(System.Logger.Level.INFO, what + ": устройство " + deviceId + ", уведомляем " + d.get().userId());
        return d;
    }

    /** Отправляет статус владельцу и возвращает состояние (true = включено). */
    public boolean sendStatus(UUID deviceId) {
        Device d = platform.findDevice(deviceId).orElseThrow(() -> new NotFoundException(deviceId));
        if (d.userId() != null) {
            dispatcher.send(Notifications.deviceStatus(d, Instant.now()));
        }
        return d.on();
    }

    // ---- /settings и /norm ----

    /** Нижняя граница батарейки, затем «тип телеметрии: нижняя граница: верхняя граница» по каждому типу. */
    public String settingsText(UUID deviceId, User requester) {
        Device d = deviceFor(deviceId, requester);
        StringBuilder sb = new StringBuilder("Устройство ").append(d.name()).append("\n");
        sb.append("Нижняя граница заряда батареи: ")
                .append(limits.batteryLowerBound(deviceId).map(v -> v + "%").orElse("не задана"));
        List<TelemetryLimit> list = limits.telemetryLimits(deviceId);
        if (list.isEmpty()) {
            sb.append("\nГраницы телеметрии не заданы");
        } else {
            sb.append("\nГраницы телеметрии:");
            for (TelemetryLimit l : list) {
                sb.append("\nтип ").append(l.telemetryType());
                if (l.lowerBound() != null) sb.append(": нижняя граница ").append(Notifications.format(l.lowerBound()));
                if (l.upperBound() != null) sb.append(": верхняя граница ").append(Notifications.format(l.upperBound()));
                if (l.lowerBound() == null && l.upperBound() == null) sb.append(": границ нет");
            }
        }
        return sb.toString();
    }

    /** /norm <id> <тип> <>= или <=> <число>: только владелец или админ. */
    public synchronized String setNorm(UUID deviceId, User requester, short telemetryType,
                                       NormOperator operator, double value) {
        deviceFor(deviceId, requester);
        if (!Double.isFinite(value)) throw new ValidationException(Map.of("value", "должно быть числом"));
        NormRules.Result result = normRules.setNorm(deviceId, telemetryType, operator, value);
        String verb = result == NormRules.Result.CREATED ? "добавлена" : "изменена";
        return "Норма " + verb + ": тип " + telemetryType + " " + operator.symbol() + " "
                + Notifications.format(value);
    }

    // ---- старые правила (таблица rute): нужны команде /battery, которую оставили как есть ----

    /** Действующие правила устройства: свои из rute, а чего там нет, берётся из общих значений. */
    public Rule rulesFor(UUID deviceId) {
        Settings common = settings.defaults();
        Rule own = rules.find(deviceId).orElse(new Rule(null, null, null));
        return new Rule(
                own.batteryLimit() != null ? own.batteryLimit() : defaultBatteryLimit,
                own.norm() != null ? own.norm() : common.norm(),
                own.deviation() != null ? own.deviation() : common.deviation());
    }

    /** Меняет правила устройства (только владелец или админ). Переданные null значения не меняются. */
    public synchronized Rule updateRule(UUID deviceId, User requester, Rule change) {
        deviceFor(deviceId, requester);
        validate(change.batteryLimit(), change.norm(), change.deviation());
        Rule current = rulesFor(deviceId);
        Rule merged = new Rule(
                change.batteryLimit() != null ? change.batteryLimit() : current.batteryLimit(),
                change.norm() != null ? change.norm() : current.norm(),
                change.deviation() != null ? change.deviation() : current.deviation());
        rules.save(deviceId, merged);
        return merged;
    }

    // ---- ответы для Telegram-бота ----

    public String statusText(UUID deviceId, User requester) {
        return Notifications.statusLine(deviceFor(deviceId, requester));
    }

    /** Среднее значение телеметрии начиная с момента since (в БД: recorded_at >= since). */
    public String averageText(UUID deviceId, User requester, Instant since, ZoneId zone) {
        Device d = deviceFor(deviceId, requester);
        OptionalDouble avg = platform.averageSince(deviceId, since);
        String from = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(zone).format(since)
                + " (" + zone.getId() + ")";
        if (avg.isEmpty()) {
            return "Устройство " + d.name() + ": с " + from + " показаний нет";
        }
        return String.format(Locale.ROOT, "Устройство %s: среднее с %s: %.2f", d.name(), from, avg.getAsDouble());
    }

    // ---- общие значения (GET/PUT /settings от IoT Core) ----

    public Settings getDefaults() {
        return settings.defaults();
    }

    public synchronized Settings updateDefaults(Settings s) {
        validate(null, s.norm(), s.deviation());
        Settings current = settings.defaults();
        settings.saveDefaults(new Settings(
                s.norm() != null ? s.norm() : current.norm(),
                s.deviation() != null ? s.deviation() : current.deviation()));
        return settings.defaults();
    }

    private void validate(Integer battery, Double norm, Double deviation) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (battery == null && norm == null && deviation == null) {
            errors.put("body", "укажите norm и/или deviation");
        }
        if (battery != null && (battery < 0 || battery > 100)) {
            errors.put("battery", "должно быть от 0 до 100");
        }
        if (norm != null && !Double.isFinite(norm)) {
            errors.put("norm", "должно быть числом");
        }
        if (deviation != null && (!Double.isFinite(deviation) || deviation < 0)) {
            errors.put("deviation", "должно быть числом не меньше 0");
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    private Device deviceFor(UUID deviceId, User requester) {
        Device d = platform.findDevice(deviceId).orElseThrow(() -> new NotFoundException(deviceId));
        boolean allowed = requester.admin() || requester.id().equals(d.userId());
        if (!allowed) throw new NotFoundException(deviceId);   // чужое устройство не раскрываем
        return d;
    }
}