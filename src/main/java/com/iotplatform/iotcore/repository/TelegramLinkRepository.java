package com.iotplatform.iotcore.repository;

import java.util.Optional;
import java.util.UUID;

/** Привязка Telegram-чата к пользователю платформы. У пользователя один чат. */
public interface TelegramLinkRepository {
    Optional<UUID> findUserId(long chatId);   // чей это чат
    Optional<Long> findChatId(UUID userId);   // чат пользователя (пусто, если он не писал боту)
    void save(long chatId, UUID userId);      // привязать (старая привязка этого пользователя или чата заменится)
    void delete(long chatId);                 // отвязать чат
}