package com.iotplatform.iotcore.entity;

import com.iotplatform.iotcore.model.UserRole;

import java.time.LocalDate;
import java.util.UUID;

public class UserEntity {

    private final UUID id;
    private final String email;

    private String phone;
    private String username;
    private LocalDate dateOfBirth;
    private byte[] passwordHash;
    private UserRole role;

    public UserEntity() {
        this.id = null;
        this.email = null;
    }

    public UserEntity(UUID id, String username, String email, String phone,
                      LocalDate dateOfBirth, byte[] passwordHash, UserRole role) {
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.username = username;
        this.dateOfBirth = dateOfBirth;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }

    public String getPhone() { return phone; }
    public String getUsername() { return username; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public byte[] getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }

    // Сеттера для email нет специально: почту менять нельзя
    public void setPhone(String phone) { this.phone = phone; }
    public void setUsername(String username) { this.username = username; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setPasswordHash(byte[] passwordHash) { this.passwordHash = passwordHash; }
    public void setRole(UserRole role) { this.role = role; }
}