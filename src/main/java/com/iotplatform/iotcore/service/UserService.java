package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.exception.UserNotFoundException;
import com.iotplatform.iotcore.repository.UserRepository;

import java.util.List;
import java.util.UUID;

public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public List<UserResponse> findAll() {
        return users.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponse findById(UUID id) {
        return users.findById(id).map(this::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    public UserResponse create(UserDto dto) {
        dto.validate(true);
        UserEntity entity = new UserEntity(UUID.randomUUID(), dto.username(), dto.email(),
                dto.phone(), dto.dateOfBirth(), PasswordHasher.hash(dto.password()), dto.role());
        return toResponse(users.save(entity));
    }

    public UserResponse update(UUID id, UserDto dto) {
        dto.validate(false);
        UserEntity existing = users.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        byte[] hash = dto.password() == null
                ? existing.passwordHash()
                : PasswordHasher.hash(dto.password());
        UserEntity entity = new UserEntity(id, dto.username(), dto.email(),
                dto.phone(), dto.dateOfBirth(), hash, dto.role());
        return toResponse(users.save(entity));
    }

    public void delete(UUID id) {
        if (!users.deleteById(id)) {
            throw new UserNotFoundException(id);
        }
    }

    private UserResponse toResponse(UserEntity e) {
        return new UserResponse(e.id(), e.username(), e.email(),
                e.phone(), e.dateOfBirth(), e.role());
    }
}
