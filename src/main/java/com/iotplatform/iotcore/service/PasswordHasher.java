package com.iotplatform.iotcore.service;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class PasswordHasher {

    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final String PEPPER_ENV = "PASSWORD_PEPPER";

    // Поле для тестов: позволяет задать "перец" напрямую, не трогая System.getenv()
    static String pepperForTests = null;

    private PasswordHasher() {}

    private static byte[] getPepper() {
        // Если тест задал перец, используем его. Иначе берем из окружения.
        String pepper = pepperForTests != null ? pepperForTests : System.getenv(PEPPER_ENV);

        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException(
                    "Переменная окружения PASSWORD_PEPPER не задана"
            );
        }

        return Base64.getDecoder().decode(pepper);
    }

    private static byte[] applyPepper(String password) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec key = new SecretKeySpec(getPepper(), "HmacSHA256");
        mac.init(key);
        return mac.doFinal(password.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Пароль не может быть null");
        }

        try {
            // Генерируем уникальную соль
            byte[] salt = new byte[SALT_BYTES];
            new SecureRandom().nextBytes(salt);

            // Добавляем pepper к паролю через HMAC
            byte[] pepperedPassword = applyPepper(password);

            // Выполняем PBKDF2
            PBEKeySpec spec = new PBEKeySpec(
                    Base64.getEncoder().encodeToString(pepperedPassword).toCharArray(),
                    salt,
                    ITERATIONS,
                    KEY_BITS
            );

            byte[] derivedKey = SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();

            spec.clearPassword();

            // Формат: соль (16 байт) + хеш (32 байта)
            byte[] result = new byte[SALT_BYTES + derivedKey.length];

            System.arraycopy(salt, 0, result, 0, SALT_BYTES);
            System.arraycopy(derivedKey, 0, result, SALT_BYTES, derivedKey.length);

            Arrays.fill(pepperedPassword, (byte) 0);
            Arrays.fill(derivedKey, (byte) 0);

            return result;

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось захешировать пароль", e);
        }
    }

    public static boolean verify(String password, byte[] stored) {
        if (password == null || stored == null || stored.length != SALT_BYTES + KEY_BITS / 8) {
            return false;
        }

        try {
            // Извлекаем соль и сохранённый хеш
            byte[] salt = Arrays.copyOfRange(stored, 0, SALT_BYTES);
            byte[] expected = Arrays.copyOfRange(stored, SALT_BYTES, stored.length);

            // Повторно применяем pepper
            byte[] pepperedPassword = applyPepper(password);

            // Вычисляем хеш
            PBEKeySpec spec = new PBEKeySpec(
                    Base64.getEncoder().encodeToString(pepperedPassword).toCharArray(),
                    salt,
                    ITERATIONS,
                    KEY_BITS
            );

            byte[] actual = SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();

            spec.clearPassword();

            // Сравниваем хеши за постоянное время
            boolean matches = MessageDigest.isEqual(expected, actual);

            Arrays.fill(pepperedPassword, (byte) 0);
            Arrays.fill(actual, (byte) 0);

            return matches;

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось проверить пароль", e);
        }
    }
}