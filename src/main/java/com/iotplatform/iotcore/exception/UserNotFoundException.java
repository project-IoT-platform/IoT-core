package com.iotplatform.iotcore.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("Пользователь не найден: " + id);
    }
}
