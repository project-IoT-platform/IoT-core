package com.iotplatform.notification.email;

import com.iotplatform.notification.exception.DeliveryException;

/** Отправитель писем. Интерфейс нужен, чтобы в тестах подменять настоящий SMTP. */
public interface EmailSender {
    boolean isConfigured();

    void send(String to, String subject, String html, String text) throws DeliveryException;
}