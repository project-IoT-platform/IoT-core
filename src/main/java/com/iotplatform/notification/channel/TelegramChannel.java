package com.iotplatform.notification.channel;

import com.iotplatform.notification.data.ChatLinks;
import com.iotplatform.notification.exception.DeliveryException;
import com.iotplatform.notification.model.Notification;
import com.iotplatform.notification.telegram.TelegramClient;

import java.io.IOException;
import java.util.Optional;

/** Отправляет уведомление в Telegram-чат владельца устройства. */
public class TelegramChannel implements Channel {
    private final TelegramClient telegram;
    private final ChatLinks links;

    public TelegramChannel(TelegramClient telegram, ChatLinks links) {
        this.telegram = telegram;
        this.links = links;
    }

    @Override
    public String name() {
        return "telegram";
    }

    @Override
    public boolean enabled() {
        return telegram.isConfigured();
    }

    @Override
    public Status send(Notification n) throws DeliveryException {
        Optional<Long> chatId = links.findChatId(n.userId());
        if (chatId.isEmpty()) return Status.NO_RECIPIENT;   // пользователь не писал боту
        try {
            telegram.sendBlocking(chatId.get(), n.telegramText());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeliveryException("Telegram: отправка прервана", e);
        } catch (IOException e) {
            throw new DeliveryException("Telegram: " + e.getMessage(), e);
        }
        return Status.SENT;
    }
}