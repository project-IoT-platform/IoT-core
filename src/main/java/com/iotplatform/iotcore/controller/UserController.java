package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.service.UserService;

import java.util.UUID;

public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    public UserResponse getById(UUID id) {
        return service.findById(id);
    }

    public UserResponse create(UserDto dto) {
        return service.create(dto);
    }

    public UserResponse update(UUID id, UserDto dto) {
        return service.update(id, dto);
    }

    public void delete(UUID id) {
        service.delete(id);
    }
}