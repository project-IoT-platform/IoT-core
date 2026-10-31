package com.iotplatform.notification.exception;

/** Ошибка доставки по одному каналу (SMTP, Telegram). Сообщение не содержит секретов. */
public class NotificationDeliveryException extends Exception {

    public enum Reason { NOT_CONFIGURED, AUTHENTICATION, CONNECTION, REJECTED_RECIPIENT, TEMPLATE, OTHER }

    private final Reason reason;

    public NotificationDeliveryException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public NotificationDeliveryException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
