package com.iotplatform.iotcore.exception;

import java.util.UUID;

/**
 * Пользователь с указанным id не найден. HTTP-слой превращает его в ответ 404.
 */
public class UserNotFoundException extends RuntimeException {
    /**
     * Создаёт исключение.
     *
     * @param id идентификатор ненайденного пользователя
     */
    public UserNotFoundException(UUID id) {
        super("Пользователь не найден: " + id);
    }
}
