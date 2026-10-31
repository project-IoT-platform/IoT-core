package com.iotplatform.iotcore.notification;

import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.dto.TelemetryDto;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.DeviceState;
import com.iotplatform.iotcore.service.NotificationService;
import com.iotplatform.notification.exception.NotificationValidationException;
import com.iotplatform.notification.exception.UnknownDeviceException;
import com.iotplatform.notification.port.PlatformData.DeviceInfo;
import com.iotplatform.notification.port.PlatformData.DeviceStatus;
import com.iotplatform.notification.port.PlatformData.TelemetryReading;

import java.util.UUID;

/** Мост IoT Core → NotificationService (в одном процессе). */
public class NotificationServiceAdapter implements NotificationService {
    private final com.iotplatform.notification.NotificationService delegate;

    public NotificationServiceAdapter(com.iotplatform.notification.NotificationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void notifyDeviceRegistered(DeviceDto device) {
        delegate.notifyDeviceRegistered(toInfo(device));
    }

    @Override
    public void checkBattery(DeviceDto device) {
        delegate.checkBattery(toInfo(device));
    }

    @Override
    public void checkTelemetry(TelemetryDto t) {
        delegate.checkTelemetry(new TelemetryReading(t.deviceId(), t.time(), t.telemetryType(), t.value()));
    }

    @Override
    public DeviceState sendDeviceStatus(UUID deviceId) {
        try {
            return delegate.sendDeviceStatus(deviceId) == DeviceStatus.ON ? DeviceState.ON : DeviceState.OFF;
        } catch (UnknownDeviceException e) {
            throw new DeviceNotFoundException(deviceId);
        }
    }

    @Override
    public Settings getDefaults() {
        return toSettings(delegate.getDefaults());
    }

    @Override
    public Settings updateDefaults(Settings s) {
        try {
            return toSettings(delegate.updateDefaults(
                    new com.iotplatform.notification.NotificationService.Settings(s.norm(), s.deviation())));
        } catch (NotificationValidationException e) {
            throw new ValidationException(e.getErrors());
        }
    }

    private static Settings toSettings(com.iotplatform.notification.NotificationService.Settings s) {
        return new Settings(s.norm(), s.deviation());
    }

    private static DeviceInfo toInfo(DeviceDto d) {
        return new DeviceInfo(d.getDeviceId(), d.getUserId(), d.getName(), d.getManufacturer(),
                d.getBatteryLevel(), d.getState() == DeviceState.ON ? DeviceStatus.ON : DeviceStatus.OFF,
                d.getGeolocation(), d.getLastDataTime());
    }
}