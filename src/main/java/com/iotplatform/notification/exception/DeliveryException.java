package com.iotplatform.notification.exception;

/** Ошибка доставки по одному каналу (SMTP, Telegram). Текст без секретов. */
public class DeliveryException extends Exception {
    public DeliveryException(String message) {
        super(message);
    }

    public DeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}