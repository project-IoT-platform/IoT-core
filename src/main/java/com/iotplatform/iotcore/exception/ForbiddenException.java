package com.iotplatform.iotcore.exception;

/** НОВОЕ: действие запрещено для этого пользователя (например, список пользователей не для админа). ApiRouter -> HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}