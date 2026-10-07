package com.iotplatform.iotcore.exception;

import java.util.UUID;

public class DeviceNotFoundException extends RuntimeException {
    public DeviceNotFoundException(UUID id) {
        super("Устройство не найдено: " + id);
    }
}
