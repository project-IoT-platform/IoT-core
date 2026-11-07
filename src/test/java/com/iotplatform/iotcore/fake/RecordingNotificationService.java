package com.iotplatform.iotcore.fake;

import com.iotplatform.iotcore.check.BatteryCheckResult;
import com.iotplatform.iotcore.check.TelemetryCheckResult;
import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.model.DeviceState;
import com.iotplatform.iotcore.service.NotificationService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * NotificationService, который никуда не ходит по сети, а только запоминает вызовы.
 * Ответы для запросов можно задать полями.
 */
public class RecordingNotificationService extends NotificationService {
    public record BatteryEvent(UUID deviceId, BatteryCheckResult result) {}
    public record TelemetryEvent(TelemetryDto telemetry, TelemetryCheckResult result) {}

    public final List<DeviceDto> registered = new ArrayList<>();
    public final List<UserResponse> usersRegistered = new ArrayList<>();
    public final List<BatteryEvent> batteryLow = new ArrayList<>();
    public final List<TelemetryEvent> telemetryAlerts = new ArrayList<>();
    public final List<UUID> statusRequests = new ArrayList<>();
    public final List<Settings> updatedSettings = new ArrayList<>();

    public DeviceState stateToReturn = DeviceState.ON;
    public Settings settings = new Settings(20.0, 5.0);
    public RuntimeException failWith;   // если задано, запросы бросают это исключение

    public RecordingNotificationService() {
        super("http://localhost:1");   // адрес не используется
    }

    @Override
    public void notifyDeviceRegistered(DeviceDto device) {
        registered.add(device);
    }

    @Override
    public void notifyUserRegistered(UserResponse user) {
        usersRegistered.add(user);
    }

    @Override
    public void batteryLow(UUID deviceId, BatteryCheckResult r) {
        batteryLow.add(new BatteryEvent(deviceId, r));
    }

    @Override
    public void telemetryAlert(TelemetryDto t, TelemetryCheckResult r) {
        telemetryAlerts.add(new TelemetryEvent(t, r));
    }

    @Override
    public DeviceState sendDeviceStatus(UUID deviceId) {
        statusRequests.add(deviceId);
        if (failWith != null) throw failWith;
        return stateToReturn;
    }

    @Override
    public Settings getDefaults() {
        if (failWith != null) throw failWith;
        return settings;
    }

    @Override
    public Settings updateDefaults(Settings s) {
        if (failWith != null) throw failWith;
        updatedSettings.add(s);
        settings = s;
        return s;
    }
}