package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.Database;

import java.util.Optional;
import java.util.UUID;

// Таблица telegram_chats (создать в database.initSchema()):
//
//   CREATE TABLE IF NOT EXISTS telegram_chats (
//       user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
//       chat_id BIGINT NOT NULL UNIQUE
//   );

public class PostgresTelegramLinkRepository implements TelegramLinkRepository {
    private final Database database;

    public PostgresTelegramLinkRepository(Database database) {
        this.database = database;
    }

    @Override
    public Optional<UUID> findUserId(long chatId) {
        // TODO Лёша: достать id польователя у которого chat id равен ...
        // Если строки нет, вернуть Optional.empty().
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public Optional<Long> findChatId(UUID userId) {
        // TODO Лёша: достать chat id где id равен ...
        // Если строки нет, вернуть Optional.empty().
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public void save(long chatId, UUID userId) {
        // TODO Лёша: две команды в одной транзакции:
        //   1) удалить и таблицы telegram_chats строки где chat id равен ... или id равен ...
        //   2) вставить в таблицу новую строку id и chat id
        // (так старая привязка этого пользователя или этого чата заменяется новой)
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public void delete(long chatId) {
        // TODO Лёша: удалить и таблицы по начению chai id
        throw new UnsupportedOperationException("TODO Лёша");
    }
}