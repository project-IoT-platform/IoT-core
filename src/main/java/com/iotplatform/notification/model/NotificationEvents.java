package com.iotplatform.notification.model;

import com.iotplatform.notification.port.PlatformData.DeviceInfo;
import com.iotplatform.notification.port.PlatformData.DeviceStatus;
import com.iotplatform.notification.port.PlatformData.TelemetryReading;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Фабрики событий. Для нового типа уведомления добавьте константу в NotificationType и метод сюда. */
public final class NotificationEvents {
    private NotificationEvents() {}

    public static NotificationEvent deviceRegistered(DeviceInfo d, Instant now) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("ID устройства", String.valueOf(d.id()));
        if (d.manufacturer() != null) details.put("Производитель", d.manufacturer());
        if (d.batteryLevel() != null) details.put("Заряд батареи", d.batteryLevel() + "%");
        if (d.geolocation() != null) details.put("Геолокация", d.geolocation());
        return new NotificationEvent(NotificationType.DEVICE_REGISTERED, d.ownerUserId(), d.id(), d.name(), now,
                "Устройство зарегистрировано на платформе.", details,
                "📟 Зарегистрировано устройство\n"
                        + "Название: " + d.name() + "\n"
                        + "ID: " + d.id());
    }

    public static NotificationEvent lowBattery(DeviceInfo d, int level, int threshold, Instant now) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Заряд", level + "%");
        details.put("Порог", threshold + "%");
        return new NotificationEvent(NotificationType.LOW_BATTERY, d.ownerUserId(), d.id(), d.name(), now,
                "Заряд батареи устройства ниже допустимого порога. Подключите устройство к питанию.", details,
                "🪫 Низкий заряд батареи\n"
                        + "Устройство: " + d.name() + "\n"
                        + "Заряд: " + level + "% (порог " + threshold + "%)");
    }

    public static NotificationEvent telemetryAlert(DeviceInfo d, TelemetryReading t,
                                                   double norm, double deviation, Instant now) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Показание", String.valueOf(t.value()));
        details.put("Норма", String.valueOf(norm));
        details.put("Допустимое отклонение", String.valueOf(deviation));
        details.put("Фактическое отклонение", String.format(Locale.ROOT, "%.2f", Math.abs(t.value() - norm)));
        if (t.telemetryType() != null) details.put("Тип телеметрии (код)", String.valueOf(t.telemetryType()));
        return new NotificationEvent(NotificationType.TELEMETRY_ALERT, d.ownerUserId(), d.id(), d.name(),
                t.time() != null ? t.time() : now,
                "Показание устройства вышло за пределы допустимого отклонения от нормы.", details,
                "⚠️ На устройстве " + d.name()
                        + " показатели: " + t.value()
                        + ", норма: " + norm
                        + " (допустимое отклонение: " + deviation + ")");
    }

    public static NotificationEvent deviceStatus(DeviceInfo d, Instant now) {
        String state = stateText(d);
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Состояние", state);
        if (d.batteryLevel() != null) details.put("Заряд батареи", d.batteryLevel() + "%");
        return new NotificationEvent(NotificationType.DEVICE_STATUS, d.ownerUserId(), d.id(), d.name(), now,
                "Текущее состояние устройства: " + state + ".", details, statusLine(d));
    }

    /** Строка статуса, как в старом NotificationService.statusLine. */
    public static String statusLine(DeviceInfo d) {
        return "Устройство " + d.name() + ": " + stateText(d);
    }

    private static String stateText(DeviceInfo d) {
        return d.status() == DeviceStatus.ON ? "ВКЛ" : "ВЫКЛ";
    }
}
