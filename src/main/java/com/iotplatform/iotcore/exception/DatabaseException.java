package com.iotplatform.iotcore.exception;

/**
 * Ошибка работы с БД (обёртка над {@link java.sql.SQLException}).
 */
public class DatabaseException extends RuntimeException {
    /**
     * Создаёт исключение.
     *
     * @param message описание ошибки
     * @param cause исходная причина
     */
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
