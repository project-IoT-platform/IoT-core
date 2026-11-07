package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserDtoTest {

    private static UserDto dto(String email, String password) {
        return new UserDto(null, null, email, null, null, null, password);
    }

    private static Set<String> errorsOnCreate(UserDto dto) {
        return assertThrows(ValidationException.class, () -> dto.validate(true)).getErrors().keySet();
    }

    @Test
    void emailAndPasswordAreEnoughOnCreate() {
        assertDoesNotThrow(() -> dto("a@example.com", "password123").validate(true));
    }

    @Test
    void emptyDtoOnCreateReportsEmailAndPassword() {
        assertEquals(Set.of("email", "password"), errorsOnCreate(dto(null, null)));
    }

    @Test
    void emailWithoutAtSignIsRejectedOnCreate() {
        assertEquals(Set.of("email"), errorsOnCreate(dto("not-an-email", "password123")));
    }

    @Test
    void passwordOfSevenCharactersIsRejected() {
        assertEquals(Set.of("password"), errorsOnCreate(dto("a@example.com", "1234567")));
    }

    @Test
    void passwordOfExactlyEightCharactersIsAccepted() {
        assertDoesNotThrow(() -> dto("a@example.com", "12345678").validate(true));
    }

    @Test
    void nothingIsRequiredOnUpdate() {
        assertDoesNotThrow(() -> dto(null, null).validate(false));
    }

    @Test
    void shortPasswordIsRejectedOnUpdateToo() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> dto(null, "short").validate(false));

        assertEquals(Set.of("password"), e.getErrors().keySet());
    }

    @Test
    void blankUsernameIsRejected() {
        UserDto dto = new UserDto(null, "   ", "a@example.com", null, null, null, "password123");

        assertEquals(Set.of("username"), errorsOnCreate(dto));
    }

    @Test
    void birthDateInTheFutureIsRejected() {
        UserDto dto = new UserDto(null, null, "a@example.com", null,
                LocalDate.now().plusDays(1), null, "password123");

        assertEquals(Set.of("dateOfBirth"), errorsOnCreate(dto));
    }

    @Test
    void birthDateTodayIsAccepted() {
        UserDto dto = new UserDto(null, null, "a@example.com", null, LocalDate.now(), null, "password123");

        assertDoesNotThrow(() -> dto.validate(true));
    }

    @Test
    void severalProblemsAreReportedTogether() {
        UserDto dto = new UserDto(null, " ", "bad", null, LocalDate.now().plusYears(1), null, "short");

        assertEquals(Set.of("email", "password", "username", "dateOfBirth"), errorsOnCreate(dto));
    }

    private static List<String> acceptedOf(List<String> emails) {
        List<String> accepted = new ArrayList<>();
        for (String email : emails) {
            try {
                dto(email, "password123").validate(true);
                accepted.add(email);
            } catch (ValidationException rejected) {
                // адрес отклонён
            }
        }
        return accepted;
    }

    @Test
    void brokenEmailsAreRejected() {
        List<String> broken = List.of(
                "@", "user@", "@example.com", "user@@example.com",
                "user@example", "user@.com", "user@example..com", "user@example.c",
                "user@-example.com", "user@example-.com",
                "us er@example.com", ".user@example.com", "user.@example.com", "us..er@example.com",
                "", "   ");

        List<String> acceptedByMistake = acceptedOf(broken);

        assertTrue(acceptedByMistake.isEmpty(), "Некорректные адреса приняты: " + acceptedByMistake);
    }

    @Test
    void normalEmailsAreAccepted() {
        List<String> good = List.of(
                "a@example.com", "first.last@example.com", "user+tag@example.co.uk",
                "USER@EXAMPLE.COM", "user_name@sub.domain.example.org", "a-b@my-site.io",
                "  a@example.com  ");   // пробелы по краям срежет UserService

        List<String> rejectedByMistake = new ArrayList<>(good);
        rejectedByMistake.removeAll(acceptedOf(good));

        assertTrue(rejectedByMistake.isEmpty(), "Корректные адреса отклонены: " + rejectedByMistake);
    }

    @Test
    void tooLongEmailIsRejected() {
        String local = "a".repeat(64);
        String longEmail = local + "@" + "b".repeat(190) + ".com";   // длиннее 254 символов

        assertEquals(Set.of("email"), errorsOnCreate(dto(longEmail, "password123")));
    }
}