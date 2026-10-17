package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.UserRole;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record UserDto(
        UUID id,
        String username,
        String email,
        String phone,
        LocalDate dateOfBirth,
        UserRole role,
        String password) {

    /** passwordRequired: true при создании (POST), false при изменении (PUT). */
    public void validate(boolean passwordRequired) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (username == null || username.isBlank()) errors.put("username", "обязательное поле");
        if (email == null || !email.contains("@")) errors.put("email", "некорректный адрес");
        if (role == null) errors.put("role", "обязательное поле (ADMIN или GUEST)");
        if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now())) {
            errors.put("dateOfBirth", "дата не может быть в будущем");
        }
        if (password == null) {
            if (passwordRequired) errors.put("password", "обязательное поле");
        } else if (password.length() < 8) {
            errors.put("password", "минимум 8 символов");
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }
}
