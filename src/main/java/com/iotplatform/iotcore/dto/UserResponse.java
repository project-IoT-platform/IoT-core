package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.model.UserRole;

import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String phone,
        LocalDate dateOfBirth,
        UserRole role,
        boolean isConfirmed) {

    public UserResponse(UUID id, String username, String email, String phone,
                        LocalDate dateOfBirth, UserRole role) {
        this(id, username, email, phone, dateOfBirth, role, false);
    }
}