package com.iotplatform.notification.data;

import com.iotplatform.notification.db.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** Привязки чатов в таблице telegram_chats (её создаёт IoT Core в schema.sql). */
public class PostgresChatLinks implements ChatLinks {
    private final Db db;

    public PostgresChatLinks(Db db) {
        this.db = db;
    }

    @Override
    public Optional<UUID> findUserId(long chatId) {
        return db.query("SELECT user_id FROM telegram_chats WHERE chat_id = ?",
                rs -> rs.getObject("user_id", UUID.class), chatId).stream().findFirst();
    }

    @Override
    public Optional<Long> findChatId(UUID userId) {
        return db.query("SELECT chat_id FROM telegram_chats WHERE user_id = ?",
                rs -> rs.getLong("chat_id"), userId).stream().findFirst();
    }

    @Override
    public void save(long chatId, UUID userId) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);   // удаление и вставка выполняются как одно целое
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
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Ошибка сохранения привязки Telegram-чата: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(long chatId) {
        db.update("DELETE FROM telegram_chats WHERE chat_id = ?", chatId);
    }
}