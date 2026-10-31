package com.iotplatform.notification.exception;

import java.util.UUID;

public class UnknownDeviceException extends RuntimeException {
    public UnknownDeviceException(UUID id) {
        super("Устройство не найдено: " + id);
    }
}