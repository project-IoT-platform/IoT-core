package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.service.NotificationService;
import com.iotplatform.iotcore.service.UserService;

import java.util.UUID;

/**
 * Контроллер пользователей: создание, чтение, изменение и удаление; при создании отправляет письмо с id.
 */
public class UserController {
    private final UserService service;
    private final NotificationService notifications;

    /**
     * Создаёт контроллер.
     *
     * @param service сервис пользователей
     * @param notifications клиент сервиса уведомлений
     */
    public UserController(UserService service, NotificationService notifications) {
        this.service = service;
        this.notifications = notifications;
    }

    /**
     * Возвращает пользователя по id.
     *
     * @param id идентификатор пользователя
     * @return данные пользователя без пароля
     * @throws com.iotplatform.iotcore.exception.UserNotFoundException если пользователя нет
     */
    public UserResponse getById(UUID id) {
        return service.findById(id);
    }

    /**
     * Создаёт пользователя и отправляет ему уведомление с id.
     *
     * @param dto данные нового пользователя (обязательны почта и пароль)
     * @return созданный пользователь
     * @throws com.iotplatform.iotcore.exception.ValidationException если данные некорректны или почта уже занята
     */
    public UserResponse create(UserDto dto) {
        UserResponse created = service.create(dto);
        notifications.notifyUserRegistered(created);   // письмо с id пользователя
        return created;
    }

    /**
     * Обновляет переданные поля пользователя.
     *
     * @param id идентификатор пользователя
     * @param dto новые значения; поля со значением {@code null} не меняются
     * @return обновлённый пользователь
     * @throws com.iotplatform.iotcore.exception.UserNotFoundException если пользователя нет
     * @throws com.iotplatform.iotcore.exception.ValidationException если данные некорректны или передана другая почта
     */
    public UserResponse update(UUID id, UserDto dto) {
        return service.update(id, dto);
    }

    /**
     * Удаляет пользователя.
     *
     * @param id идентификатор пользователя
     * @throws com.iotplatform.iotcore.exception.UserNotFoundException если пользователя нет
     */
    public void delete(UUID id) {
        service.delete(id);
    }
}