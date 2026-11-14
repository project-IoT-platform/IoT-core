package com.iotplatform.notification.exception;

import java.util.UUID;

/** Устройство не найдено (или принадлежит другому пользователю: чужие устройства не раскрываем). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(UUID id) {
        super("Устройство не найдено: " + id);
    }
}