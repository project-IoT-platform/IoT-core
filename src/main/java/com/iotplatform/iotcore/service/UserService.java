package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.exception.UserNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.UserRole;
import com.iotplatform.iotcore.repository.UserRepository;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public UserResponse findById(UUID id) {
        return users.findById(id).map(this::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    public boolean existsByEmail(String email) {
        return email != null && users.existsByEmail(normalizeEmail(email));
    }

    public Optional<byte[]> findPasswordHashByEmail(String email) {
        if (email == null) return Optional.empty();
        return users.findPasswordHashByEmail(normalizeEmail(email));
    }

    public UserResponse create(UserDto dto) {
        dto.validate(true);
        String email = normalizeEmail(dto.getEmail());
        if (users.existsByEmail(email)) {
            throw new ValidationException(Map.of("email", "пользователь с такой почтой уже существует"));
        }

        UserRole role = dto.getRole() != null ? dto.getRole() : UserRole.GUEST;

        UserEntity entity = new UserEntity(UUID.randomUUID(), dto.getUsername(), email,
                dto.getPhone(), dto.getDateOfBirth(), PasswordHasher.hash(dto.getPassword()), role);
        return toResponse(users.save(entity));
    }

    public UserResponse update(UUID id, UserDto dto) {
        dto.validate(false);
        UserEntity user = users.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (dto.getEmail() != null && !dto.getEmail().trim().equalsIgnoreCase(user.getEmail())) {
            throw new ValidationException(Map.of("email", "почту изменить нельзя"));
        }

        if (dto.getUsername() != null) user.setUsername(dto.getUsername());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getDateOfBirth() != null) user.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getRole() != null) user.setRole(dto.getRole());
        if (dto.getPassword() != null) user.setPasswordHash(PasswordHasher.hash(dto.getPassword()));

        return toResponse(users.save(user));
    }

    public void delete(UUID id) {
        if (!users.deleteById(id)) {
            throw new UserNotFoundException(id);
        }
    }

    private UserResponse toResponse(UserEntity e) {
        return new UserResponse(e.getId(), e.getUsername(), e.getEmail(),
                e.getPhone(), e.getDateOfBirth(), e.getRole());
    }
}