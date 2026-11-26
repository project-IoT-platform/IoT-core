package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.model.UserRole;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Данные пользователя для ответа клиенту (без пароля и его хеша).
 *
 * @param id идентификатор пользователя
 * @param username имя пользователя
 * @param email почта
 * @param phone телефон
 * @param dateOfBirth дата рождения
 * @param role роль
 */
public record UserResponse(
        UUID id,
        String username,
        String email,
        String phone,
        LocalDate dateOfBirth,
        UserRole role) {
}
