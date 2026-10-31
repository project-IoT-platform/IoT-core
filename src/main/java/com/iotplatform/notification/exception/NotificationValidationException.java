package com.iotplatform.notification.exception;

import java.util.Map;

public class NotificationValidationException extends RuntimeException {
    private final Map<String, String> errors;

    public NotificationValidationException(Map<String, String> errors) {
        super("Некорректный запрос: " + errors);
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
