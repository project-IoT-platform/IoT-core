package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.service.NotificationService;
import com.iotplatform.iotcore.service.UserService;

import java.util.UUID;

public class UserController {
    private final UserService service;
    private final NotificationService notifications;

    public UserController(UserService service, NotificationService notifications) {
        this.service = service;
        this.notifications = notifications;
    }

    public UserResponse getById(UUID id) {
        return service.findById(id);
    }

    public UserResponse create(UserDto dto) {
        UserResponse created = service.create(dto);
        notifications.notifyUserRegistered(created);   // письмо с id пользователя
        return created;
    }

    public UserResponse update(UUID id, UserDto dto) {
        return service.update(id, dto);
    }

    public void delete(UUID id) {
        service.delete(id);
    }
}