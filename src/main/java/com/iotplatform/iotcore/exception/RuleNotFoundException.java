package com.iotplatform.iotcore.exception;

import java.util.UUID;

/**
 * Правило с указанным id не найдено (или не найдена его связь с устройством). HTTP-слой превращает его в ответ 404.
 */
public class RuleNotFoundException extends RuntimeException {
    /**
     * Создаёт исключение.
     *
     * @param id идентификатор ненайденного правила
     */
    public RuleNotFoundException(UUID id) {
        super("Правило не найдено: " + id);
    }
}