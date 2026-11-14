package com.iotplatform.notification.data;

import java.util.Optional;
import java.util.UUID;

/** Привязка Telegram-чата к пользователю платформы. У пользователя один чат. */
public interface ChatLinks {
    Optional<UUID> findUserId(long chatId);

    Optional<Long> findChatId(UUID userId);

    void save(long chatId, UUID userId);   // старая привязка этого пользователя или чата заменяется

    void delete(long chatId);
}