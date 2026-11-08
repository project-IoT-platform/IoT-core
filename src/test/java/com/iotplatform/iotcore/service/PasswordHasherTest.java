package com.iotplatform.iotcore.service;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    @Test
    void hashIsSaltPlusKey() {
        assertEquals(16 + 32, PasswordHasher.hash("password123").length);
    }

    @Test
    void samePasswordGivesDifferentHashesBecauseOfSalt() {
        byte[] a = PasswordHasher.hash("password123");
        byte[] b = PasswordHasher.hash("password123");
        assertFalse(Arrays.equals(a, b));
    }

    @Test
    void keyCanBeRecomputedFromSalt() throws Exception {
        byte[] stored = PasswordHasher.hash("password123");
        byte[] salt = Arrays.copyOfRange(stored, 0, 16);
        byte[] key = Arrays.copyOfRange(stored, 16, stored.length);

        byte[] recomputed = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec("password123".toCharArray(), salt, 120_000, 256))
                .getEncoded();
        assertArrayEquals(key, recomputed);

        byte[] wrong = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec("wrong-password".toCharArray(), salt, 120_000, 256))
                .getEncoded();
        assertFalse(Arrays.equals(key, wrong));
    }
}