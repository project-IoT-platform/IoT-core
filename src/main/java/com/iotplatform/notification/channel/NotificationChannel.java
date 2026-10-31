package com.iotplatform.notification.channel;

import com.iotplatform.notification.exception.NotificationDeliveryException;
import com.iotplatform.notification.model.NotificationEvent;

/** Канал доставки уведомлений (Telegram, email, ...). */
public interface NotificationChannel {

    enum DeliveryStatus { SENT, NO_RECIPIENT, NOT_APPLICABLE, DISABLED, FAILED }

    String name();

    /** Настроен ли канал; выключенный канал пропускается без ошибки. */
    boolean isEnabled();

    /**
     * SENT — отправлено; NO_RECIPIENT — у пользователя нет адреса/чата; NOT_APPLICABLE — тип не для этого канала.
     * Ошибки доставки сообщаются исключением.
     */
    DeliveryStatus send(NotificationEvent event) throws NotificationDeliveryException;
}