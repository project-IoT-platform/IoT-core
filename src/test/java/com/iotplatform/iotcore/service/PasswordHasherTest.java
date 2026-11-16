package com.iotplatform.iotcore.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    // Base64-строка, которая декодируется в 32 байта
    private final String testPepper = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";

    @BeforeEach
    void setUp() {
        // Задаем "перец" для тестов напрямую
        PasswordHasher.pepperForTests = testPepper;
    }

    @AfterEach
    void tearDown() {
        // Очищаем после каждого теста
        PasswordHasher.pepperForTests = null;
    }

    @Test
    void hashAndVerify_Success() {
        String password = "mySuperSecretPassword";

        byte[] hash = PasswordHasher.hash(password);

        assertNotNull(hash);
        assertEquals(16 + 32, hash.length); // 16 байт соли + 32 байта хеша
        assertTrue(PasswordHasher.verify(password, hash), "Пароль должен совпадать");
    }

    @Test
    void verify_WrongPassword_ReturnsFalse() {
        String password = "correctPassword";
        String wrongPassword = "wrongPassword";

        byte[] hash = PasswordHasher.hash(password);

        assertFalse(PasswordHasher.verify(wrongPassword, hash), "Неверный пароль не должен проходить");
    }

    @Test
    void verify_NullOrInvalidHash_ReturnsFalse() {
        assertFalse(PasswordHasher.verify("pass", null));
        assertFalse(PasswordHasher.verify("pass", new byte[10])); // Слишком короткий хеш
    }
}