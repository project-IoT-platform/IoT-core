package com.iotplatform.notification.channel;

import com.iotplatform.notification.exception.DeliveryException;
import com.iotplatform.notification.model.Notification;

/** Канал доставки (Telegram, email, ...). Новый канал, например SMS, делается реализацией этого интерфейса. */
public interface Channel {
    /** SENT отправлено; NO_RECIPIENT нет адреса или чата; SKIPPED тип не для этого канала; DISABLED канал не настроен; FAILED ошибка. */
    enum Status { SENT, NO_RECIPIENT, SKIPPED, DISABLED, FAILED }

    String name();

    boolean enabled();

    Status send(Notification n) throws DeliveryException;
}