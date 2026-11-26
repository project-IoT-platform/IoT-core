package com.iotplatform.iotcore.exception;

import java.util.UUID;

/**
 * Устройство с указанным id не найдено. HTTP-слой превращает его в ответ 404.
 */
public class DeviceNotFoundException extends RuntimeException {
    /**
     * Создаёт исключение.
     *
     * @param id идентификатор ненайденного устройства
     */
    public DeviceNotFoundException(UUID id) {
        super("Устройство не найдено: " + id);
    }
}
