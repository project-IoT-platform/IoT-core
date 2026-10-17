package com.iotplatform.iotcore.entity;

import com.iotplatform.iotcore.model.UserRole;

import java.time.LocalDate;
import java.util.UUID;

public record UserEntity(
        UUID id,                // PK
        String username,
        String email,
        String phone,
        LocalDate dateOfBirth,
        byte[] passwordHash,
        UserRole role) {
}