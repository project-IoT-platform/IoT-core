package com.iotplatform.iotcore.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Хеширование паролей: PBKDF2WithHmacSHA256, 120 000 итераций, ключ 256 бит, случайная соль 16 байт.
 */
public final class PasswordHasher {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private PasswordHasher() {}

    /**
     * Хеширует пароль со случайной солью.
     *
     * @param password пароль в открытом виде
     * @return 48 байт: первые 16 это соль, остальные 32 это полученный ключ
     * @throws NullPointerException если {@code password} равен {@code null}
     * @throws IllegalStateException если алгоритм хеширования недоступен
     */
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
}
