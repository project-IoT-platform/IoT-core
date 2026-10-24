package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.exception.DatabaseException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class PostgresTelegramLinkRepository implements TelegramLinkRepository {
    private final DatabaseUtils databaseUtils;

    public PostgresTelegramLinkRepository(DatabaseUtils databaseUtils) {
        this.databaseUtils = databaseUtils;
    }

    @Override
    public Optional<UUID> findUserId(long chatId) {
        return databaseUtils.query("SELECT user_id FROM telegram_chats WHERE chat_id = ?",
                rs -> rs.getObject("user_id", UUID.class), chatId).stream().findFirst();
    }

    @Override
    public Optional<Long> findChatId(UUID userId) {
        return databaseUtils.query("SELECT chat_id FROM telegram_chats WHERE user_id = ?",
                rs -> rs.getLong("chat_id"), userId).stream().findFirst();
    }

    @Override
    public void save(long chatId, UUID userId) throws DatabaseException {
        try (Connection conn = databaseUtils.getConnection()) {
            conn.setAutoCommit(false); // начинаем транзакцию: изменения применятся только после commit()
            try (PreparedStatement delete = conn.prepareStatement(
                    "DELETE FROM telegram_chats WHERE chat_id = ? OR user_id = ?");
                 PreparedStatement insert = conn.prepareStatement(
                         "INSERT INTO telegram_chats (user_id, chat_id) VALUES (?, ?)")) {

                delete.setLong(1, chatId);
                delete.setObject(2, userId);
                delete.executeUpdate();

                insert.setObject(1, userId);
                insert.setLong(2, chatId);
                insert.executeUpdate();

                conn.commit();
            } catch (SQLException e) {
                conn.rollback(); // если любая из двух команд упала, откатываем обе
                throw new DatabaseException("Ошибка сохранения привязки Telegram-чата", e);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка транзакции при сохранении привязки Telegram-чата", e);
        }
    }

    @Override
    public void delete(long chatId) {
        databaseUtils.update("DELETE FROM telegram_chats WHERE chat_id = ?", chatId);
    }
}