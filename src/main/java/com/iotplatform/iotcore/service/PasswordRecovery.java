package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.exception.ValidationException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.slf4j.LoggerFactory;

public final class PasswordRecovery {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ThreadPoolExecutor MAIL = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), runnable -> Thread.ofVirtual().unstarted(runnable),
            new ThreadPoolExecutor.DiscardPolicy());

    private final DatabaseUtils database;
    private final BiConsumer<String, String> sendMail;

    public PasswordRecovery(DatabaseUtils database, BiConsumer<String, String> sendMail) {
        this.database = database;
        this.sendMail = sendMail;
    }

    public void request(String email) {
        if (email == null || email.length() > 254 || !email.strip().matches("[^\\s@]+@[^\\s@]+")) {
            throw new ValidationException(Map.of("email", "Введите корректную почту"));
        }
        String address = email.strip().toLowerCase(Locale.ROOT);
        MAIL.execute(() -> {
            try {
                byte[] bytes = new byte[32];
                RANDOM.nextBytes(bytes);
                String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                int changed = database.update("""
                        UPDATE users SET password_reset_hash = ?,
                            password_reset_expires = now() + interval '20 minutes',
                            password_reset_sent = now()
                        WHERE id = (SELECT id FROM users WHERE email = ? ORDER BY id LIMIT 1)
                            AND (password_reset_sent IS NULL
                            OR password_reset_sent <= now() - interval '1 minute')
                        """, digest(token), address);
                if (changed > 0) sendMail.accept(address, token);
            } catch (RuntimeException ex) {
                LoggerFactory.getLogger(PasswordRecovery.class).error("Password recovery delivery failed", ex);
            }
        });
    }

    public boolean reset(String token, String password) {
        validatePassword(password);
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return false;
        byte[] hash = digest(token);
        if (database.query("SELECT 1 FROM users WHERE password_reset_hash = ? AND password_reset_expires > now()",
                row -> 1, hash).isEmpty()) return false;
        return database.update("""
                UPDATE users SET password_hash = ?, password_reset_hash = NULL, password_reset_expires = NULL
                WHERE password_reset_hash = ? AND password_reset_expires > now()
                """, PasswordHasher.hash(password), hash) > 0;
    }

    public boolean change(UUID id, String currentPassword, String password) {
        validatePassword(password);
        if (currentPassword == null || currentPassword.length() > 256) return false;
        var hashes = database.query("SELECT password_hash FROM users WHERE id = ?", row -> {
            try {
                return row.getBytes(1);
            } catch (java.sql.SQLException ex) {
                throw new com.iotplatform.iotcore.exception.DatabaseException("Ошибка чтения пароля", ex);
            }
        }, id);
        if (hashes.isEmpty() || !PasswordHasher.matches(currentPassword, hashes.getFirst())) return false;
        return database.update("""
                UPDATE users SET password_hash = ?, password_reset_hash = NULL, password_reset_expires = NULL
                WHERE id = ? AND password_hash = ?
                """, PasswordHasher.hash(password), id, hashes.getFirst()) > 0;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 256) {
            throw new ValidationException(Map.of("password", "Пароль должен содержать от 8 до 256 символов"));
        }
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}