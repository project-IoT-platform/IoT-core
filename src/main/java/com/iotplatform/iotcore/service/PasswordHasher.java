package com.iotplatform.iotcore.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

public final class PasswordHasher {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private PasswordHasher() {}

    public static byte[] hash(String password) {
        try {
            byte[] salt = new byte[SALT_BYTES];
            new SecureRandom().nextBytes(salt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            byte[] result = new byte[SALT_BYTES + key.length];
            System.arraycopy(salt, 0, result, 0, SALT_BYTES);
            System.arraycopy(key, 0, result, SALT_BYTES, key.length);
            return result;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось захешировать пароль", e);
        }
    }

    // ДОБАВЛЕНО: проверка пароля по сохранённому хешу (формат: соль 16 байт + ключ 32 байта).
    // Сравнение через MessageDigest.isEqual выполняется за постоянное время.
    public static boolean verify(String password, byte[] stored) {
        if (password == null || stored == null || stored.length != SALT_BYTES + KEY_BITS / 8) {
            return false;
        }
        try {
            byte[] salt = Arrays.copyOfRange(stored, 0, SALT_BYTES);
            byte[] expected = Arrays.copyOfRange(stored, SALT_BYTES, stored.length);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось проверить пароль", e);
        }
    }
}
