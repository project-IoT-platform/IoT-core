package com.iotplatform.iotcore.notification;

import com.iotplatform.iotcore.model.DeviceState;
import com.iotplatform.iotcore.model.UserRole;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.TelemetryRepository;
import com.iotplatform.iotcore.repository.UserRepository;
import com.iotplatform.notification.port.PlatformData;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/** Данные платформы для NotificationService поверх репозиториев IoT Core. */
public class IotCorePlatformData implements PlatformData {
    private final DeviceRepository devices;
    private final UserRepository users;
    private final TelemetryRepository telemetry;

    public IotCorePlatformData(DeviceRepository devices, UserRepository users, TelemetryRepository telemetry) {
        this.devices = devices;
        this.users = users;
        this.telemetry = telemetry;
    }

    @Override
    public Optional<DeviceInfo> findDevice(UUID deviceId) {
        return devices.findById(deviceId).map(e -> new DeviceInfo(e.getId(), e.getUserId(), e.getName(),
                e.getManufacturer(), e.getBatteryLevel(),
                e.getState() == DeviceState.ON ? DeviceStatus.ON : DeviceStatus.OFF,
                e.getGeolocation(), e.getLastDataTime()));
    }

    @Override
    public Optional<UserInfo> findUser(UUID userId) {
        return users.findById(userId).map(u -> new UserInfo(u.getId(), u.getUsername(), u.getEmail(),
                u.getRole() == UserRole.ADMIN));
    }

    @Override
    public OptionalDouble averageValueSince(UUID deviceId, Instant since) {
        return telemetry.averageValueSince(deviceId, since);
    }
}