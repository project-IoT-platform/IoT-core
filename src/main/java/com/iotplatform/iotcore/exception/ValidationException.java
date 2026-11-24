package com.iotplatform.iotcore.exception;

import java.util.Map;

/**
 * Некорректные данные запроса. Хранит ошибки по полям; HTTP-слой отдаёт их в теле ответа 400.
 */
public class ValidationException extends RuntimeException {
    private final Map<String, String> errors;

    /**
     * Создаёт исключение.
     *
     * @param errors карта «имя поля: что не так»
     */
    public ValidationException(Map<String, String> errors) {
        super("Некорректный запрос: " + errors);
        this.errors = errors;
    }

    /**
     * Возвращает карту «имя поля: что не так».
     *
     * @return карта «имя поля: что не так»
     */
    public Map<String, String> getErrors() {
        return errors;
    }
}
