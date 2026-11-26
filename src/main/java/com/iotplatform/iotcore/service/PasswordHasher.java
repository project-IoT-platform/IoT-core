package com.iotplatform.iotcore.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Arrays;

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
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] key = derive(password, salt);
        byte[] result = Arrays.copyOf(salt, SALT_BYTES + key.length);
        System.arraycopy(key, 0, result, SALT_BYTES, key.length);
        return result;
    }

    static boolean matches(String password, byte[] stored) {
        boolean valid = stored != null && stored.length == SALT_BYTES + KEY_BITS / Byte.SIZE;
        byte[] hash = valid ? stored : new byte[SALT_BYTES + KEY_BITS / Byte.SIZE];
        byte[] actual = derive(password, Arrays.copyOf(hash, SALT_BYTES));
        return MessageDigest.isEqual(actual, Arrays.copyOfRange(hash, SALT_BYTES, hash.length)) && valid;
    }

    private static byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось захешировать пароль", e);
        } finally {
            spec.clearPassword();
        }
    }
}
