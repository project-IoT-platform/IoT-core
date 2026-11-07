package com.iotplatform.notification.model;

import java.util.UUID;

/** Пользователь платформы: нужен email для писем и роль для проверки прав в боте. */
public record User(UUID id, String username, String email, boolean admin) {
    public String displayName() {
        return username != null ? username : email;
    }
}
