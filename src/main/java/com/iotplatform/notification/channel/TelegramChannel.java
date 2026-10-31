package com.iotplatform.notification.channel;

import com.iotplatform.notification.exception.NotificationDeliveryException;
import com.iotplatform.notification.exception.NotificationDeliveryException.Reason;
import com.iotplatform.notification.model.NotificationEvent;
import com.iotplatform.notification.port.TelegramLinks;
import com.iotplatform.notification.telegram.TelegramClient;

import java.io.IOException;
import java.util.Optional;

public class TelegramChannel implements NotificationChannel {
    private final TelegramClient telegram;
    private final TelegramLinks links;

    public TelegramChannel(TelegramClient telegram, TelegramLinks links) {
        this.telegram = telegram;
        this.links = links;
    }

    @Override
    public String name() {
        return "telegram";
    }

    @Override
    public boolean isEnabled() {
        return telegram.isConfigured();
    }

    @Override
    public DeliveryStatus send(NotificationEvent event) throws NotificationDeliveryException {
        Optional<Long> chatId = links.findChatId(event.recipientUserId());
        if (chatId.isEmpty()) return DeliveryStatus.NO_RECIPIENT;   // пользователь не писал боту
        try {
            telegram.sendMessageBlocking(chatId.get(), event.telegramText());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotificationDeliveryException(Reason.OTHER, "Telegram: отправка прервана", e);
        } catch (IOException e) {
            throw new NotificationDeliveryException(Reason.CONNECTION, "Telegram: " + e.getMessage(), e);
        }
        return DeliveryStatus.SENT;
    }
}