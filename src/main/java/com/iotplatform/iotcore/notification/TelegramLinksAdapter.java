package com.iotplatform.iotcore.notification;

import com.iotplatform.iotcore.repository.TelegramLinkRepository;
import com.iotplatform.notification.port.TelegramLinks;

import java.util.Optional;
import java.util.UUID;

/** Привязки Telegram для NotificationService поверх существующего TelegramLinkRepository. */
public class TelegramLinksAdapter implements TelegramLinks {
    private final TelegramLinkRepository repository;

    public TelegramLinksAdapter(TelegramLinkRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UUID> findUserId(long chatId) {
        return repository.findUserId(chatId);
    }

    @Override
    public Optional<Long> findChatId(UUID userId) {
        return repository.findChatId(userId);
    }

    @Override
    public void save(long chatId, UUID userId) {
        repository.save(chatId, userId);
    }

    @Override
    public void delete(long chatId) {
        repository.delete(chatId);
    }
}