package com.iotplatform.iotcore.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

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
}
