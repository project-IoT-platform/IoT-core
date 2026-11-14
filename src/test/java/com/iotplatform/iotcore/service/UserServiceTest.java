package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.exception.UserNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.UserRole;
import com.iotplatform.iotcore.testsupport.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceTest {
    private static final String PASSWORD = "password123";

    private InMemoryUserRepository users;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        service = new UserService(users);
    }

    private static UserDto newUser(String email, String password, UserRole role) {
        return new UserDto(null, "Alex", email, "+491234567", LocalDate.of(2000, 1, 1), role, password);
    }

    private UserResponse createUser(String email) {
        return service.create(newUser(email, PASSWORD, null));
    }

    private static UserDto patch(String email, String username, String phone, UserRole role, String password) {
        return new UserDto(null, username, email, phone, null, role, password);
    }

    // ---------- создание ----------

    @Test
    void emailIsTrimmedAndLowercasedOnCreate() {
        UserResponse created = createUser("  Test@Example.COM ");

        assertEquals("test@example.com", created.email());
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCase() {
        createUser("alex@example.com");

        ValidationException e = assertThrows(ValidationException.class, () -> createUser("ALEX@Example.com"));

        assertTrue(e.getErrors().containsKey("email"));
        assertEquals(1, users.size());
    }

    @Test
    void roleDefaultsToGuest() {
        assertEquals(UserRole.GUEST, createUser("a@example.com").role());
    }

    @Test
    void explicitRoleIsKept() {
        UserResponse admin = service.create(newUser("admin@example.com", PASSWORD, UserRole.ADMIN));

        assertEquals(UserRole.ADMIN, admin.role());
    }

    @Test
    void passwordIsStoredOnlyAsHash() {
        UserResponse created = createUser("a@example.com");

        byte[] hash = users.findById(created.id()).orElseThrow().getPasswordHash();

        assertEquals(48, hash.length);
        assertFalse(Arrays.equals(PASSWORD.getBytes(StandardCharsets.UTF_8), hash));
    }

    @Test
    void responseDoesNotExposeThePassword() {
        for (RecordComponent component : UserResponse.class.getRecordComponents()) {
            String name = component.getName().toLowerCase();
            assertFalse(name.contains("password"), "в ответе есть поле " + component.getName());
        }
    }

    @Test
    void shortPasswordIsRejected() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create(newUser("a@example.com", "short", null)));

        assertTrue(e.getErrors().containsKey("password"));
        assertEquals(0, users.size());
    }

    @Test
    void emailWithoutAtSignIsRejected() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create(newUser("not-an-email", PASSWORD, null)));

        assertTrue(e.getErrors().containsKey("email"));
    }

    @Test
    void createdUserIsStored() {
        UserResponse created = createUser("a@example.com");

        assertEquals(created.id(), service.findById(created.id()).id());
    }

    // ---------- чтение и вспомогательные методы ----------

    @Test
    void findingMissingUserThrows() {
        assertThrows(UserNotFoundException.class, () -> service.findById(UUID.randomUUID()));
    }

    @Test
    void normalizeEmailTrimsAndLowercases() {
        assertEquals("a@b.com", UserService.normalizeEmail("  A@B.Com "));
    }

    @Test
    void normalizeEmailKeepsNull() {
        assertNull(UserService.normalizeEmail(null));
    }

    @Test
    void existsByEmailNormalizesTheInput() {
        createUser("alex@example.com");

        assertTrue(service.existsByEmail("  ALEX@example.com "));
        assertFalse(service.existsByEmail("other@example.com"));
        assertFalse(service.existsByEmail(null));
    }

    @Test
    void passwordHashIsFoundByEmailInAnyCase() {
        createUser("alex@example.com");

        assertTrue(service.findPasswordHashByEmail("ALEX@Example.com").isPresent());
        assertTrue(service.findPasswordHashByEmail("nobody@example.com").isEmpty());
        assertTrue(service.findPasswordHashByEmail(null).isEmpty());
    }

    // ---------- обновление ----------

    @Test
    void emailCannotBeChanged() {
        UserResponse created = createUser("alex@example.com");

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.update(created.id(), patch("other@example.com", null, null, null, null)));

        assertTrue(e.getErrors().containsKey("email"));
        assertEquals("alex@example.com", users.findById(created.id()).orElseThrow().getEmail());
    }

    @Test
    void sendingTheSameEmailInAnotherCaseIsNotAChange() {
        UserResponse created = createUser("alex@example.com");

        UserResponse updated = service.update(created.id(),
                patch("  ALEX@example.com ", "New name", null, null, null));

        assertEquals("New name", updated.username());
        assertEquals("alex@example.com", updated.email());
    }

    @Test
    void updateChangesOnlyTheSentFields() {
        UserResponse created = createUser("alex@example.com");

        UserResponse updated = service.update(created.id(),
                patch(null, null, "+49000", UserRole.ADMIN, null));

        assertEquals("+49000", updated.phone());
        assertEquals(UserRole.ADMIN, updated.role());
        assertEquals("Alex", updated.username());
    }

    @Test
    void updateWithoutPasswordKeepsTheHash() {
        UserResponse created = createUser("alex@example.com");
        byte[] before = users.findById(created.id()).orElseThrow().getPasswordHash().clone();

        service.update(created.id(), patch(null, "New name", null, null, null));

        UserEntity after = users.findById(created.id()).orElseThrow();
        assertTrue(Arrays.equals(before, after.getPasswordHash()));
    }

    @Test
    void updateWithPasswordReplacesTheHash() {
        UserResponse created = createUser("alex@example.com");
        byte[] before = users.findById(created.id()).orElseThrow().getPasswordHash().clone();

        service.update(created.id(), patch(null, null, null, null, "another-password"));

        UserEntity after = users.findById(created.id()).orElseThrow();
        assertFalse(Arrays.equals(before, after.getPasswordHash()));
    }

    @Test
    void updateWithShortPasswordIsRejected() {
        UserResponse created = createUser("alex@example.com");

        assertThrows(ValidationException.class,
                () -> service.update(created.id(), patch(null, null, null, null, "short")));
    }

    @Test
    void updatingUnknownUserThrows() {
        assertThrows(UserNotFoundException.class,
                () -> service.update(UUID.randomUUID(), patch(null, "x", null, null, null)));
    }

    // ---------- удаление ----------

    @Test
    void deletedUserIsGone() {
        UserResponse created = createUser("alex@example.com");

        service.delete(created.id());

        assertThrows(UserNotFoundException.class, () -> service.findById(created.id()));
    }

    @Test
    void deletingMissingUserThrows() {
        assertThrows(UserNotFoundException.class, () -> service.delete(UUID.randomUUID()));
    }
}