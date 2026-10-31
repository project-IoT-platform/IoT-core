package com.iotplatform.notification.email;

import com.iotplatform.notification.exception.NotificationDeliveryException;

public interface EmailSender {
    boolean isConfigured();

    void send(EmailMessage message) throws NotificationDeliveryException;
}
