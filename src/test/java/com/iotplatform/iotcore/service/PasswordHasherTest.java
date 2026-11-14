package com.iotplatform.iotcore.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PasswordHasherTest {

    @Test
    void resultIsSaltPlusKey48Bytes() {
        assertEquals(48, PasswordHasher.hash("password123").length);
    }

    @Test
    void sameHashDiffersBetweenCallsBecauseOfSalt() {
        byte[] first = PasswordHasher.hash("password123");
        byte[] second = PasswordHasher.hash("password123");

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    void saltPartDiffersBetweenCalls() {
        byte[] first = Arrays.copyOfRange(PasswordHasher.hash("password123"), 0, 16);
        byte[] second = Arrays.copyOfRange(PasswordHasher.hash("password123"), 0, 16);

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    void hashDoesNotContainThePasswordInPlainText() {
        byte[] password = "password123".getBytes(StandardCharsets.UTF_8);
        byte[] hash = PasswordHasher.hash("password123");

        assertFalse(Arrays.equals(password, hash));
        assertFalse(new String(hash, StandardCharsets.ISO_8859_1).contains("password123"));
    }

    @Test
    void differentPasswordsGiveDifferentKeyParts() {
        byte[] first = Arrays.copyOfRange(PasswordHasher.hash("password123"), 16, 48);
        byte[] second = Arrays.copyOfRange(PasswordHasher.hash("password124"), 16, 48);

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    void longAndNonLatinPasswordsAreHashedToTheSameLength() {
        assertEquals(48, PasswordHasher.hash("пароль-на-русском-очень-длинный-1234567890").length);
        assertEquals(48, PasswordHasher.hash("x".repeat(500)).length);
    }
}