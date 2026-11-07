package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.exception.UserNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.fake.FakeUserRepository;
import com.iotplatform.iotcore.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {
    private FakeUserRepository users;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = new FakeUserRepository();
        service = new UserService(users);
    }

    private static UserDto newUser(String email, String password, UserRole role) {
        return new UserDto(null, "vera", email, "+79990000000", LocalDate.of(2005, 1, 1), role, password);
    }

    private static UserDto patch(String username, String email, String password, UserRole role) {
        return new UserDto(null, username, email, null, null, role, password);
    }

    @Test
    void normalizeEmailTrimsAndLowercases() {
        assertEquals("vera@mail.ru", UserService.normalizeEmail("  Vera@Mail.RU "));
        assertNull(UserService.normalizeEmail(null));
    }

    @Test
    void createSavesUserWithNormalizedEmailAndHashedPassword() {
        UserResponse r = service.create(newUser(" Vera@Mail.ru ", "password123", UserRole.ADMIN));

        assertNotNull(r.id());
        assertEquals("vera@mail.ru", r.email());
        assertEquals(UserRole.ADMIN, r.role());
        UserEntity saved = users.rows.get(r.id());
        assertEquals(48, saved.getPasswordHash().length);   // 16 байт соли + 32 байта ключа
        assertNotEquals("password123", new String(saved.getPasswordHash()));
    }

    @Test
    void createWithoutRoleMakesGuest() {
        assertEquals(UserRole.GUEST, service.create(newUser("a@b.ru", "password123", null)).role());
    }

    @Test
    void createIgnoresIdFromBody() {
        UUID fromClient = UUID.randomUUID();
        UserDto dto = new UserDto(fromClient, null, "a@b.ru", null, null, null, "password123");
        assertNotEquals(fromClient, service.create(dto).id());
    }

    @Test
    void createWithDuplicateEmailFailsEvenWithDifferentCase() {
        service.create(newUser("a@b.ru", "password123", null));
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create(newUser("A@B.RU", "password123", null)));
        assertTrue(e.getErrors().containsKey("email"));
        assertEquals(1, users.rows.size());
    }

    @Test
    void createInvalidSavesNothing() {
        assertThrows(ValidationException.class, () -> service.create(newUser("bad", "123", null)));
        assertTrue(users.rows.isEmpty());
    }

    @Test
    void findByIdReturnsResponseWithoutPassword() {
        UserResponse created = service.create(newUser("a@b.ru", "password123", null));
        UserResponse found = service.findById(created.id());
        assertEquals(created, found);
    }

    @Test
    void findByIdUnknownThrows() {
        assertThrows(UserNotFoundException.class, () -> service.findById(UUID.randomUUID()));
    }

    @Test
    void updateChangesOnlySentFields() {
        UserResponse created = service.create(newUser("a@b.ru", "password123", UserRole.GUEST));
        byte[] oldHash = users.rows.get(created.id()).getPasswordHash();

        UserResponse updated = service.update(created.id(), patch("новое имя", null, null, null));

        assertEquals("новое имя", updated.username());
        assertEquals(created.phone(), updated.phone());
        assertEquals(created.dateOfBirth(), updated.dateOfBirth());
        assertEquals(UserRole.GUEST, updated.role());
        assertSame(oldHash, users.rows.get(created.id()).getPasswordHash());
    }

    @Test
    void updatePasswordChangesHash() {
        UserResponse created = service.create(newUser("a@b.ru", "password123", null));
        byte[] oldHash = users.rows.get(created.id()).getPasswordHash();

        service.update(created.id(), patch(null, null, "another-password", null));

        assertFalse(java.util.Arrays.equals(oldHash, users.rows.get(created.id()).getPasswordHash()));
    }

    @Test
    void updateRole() {
        UserResponse created = service.create(newUser("a@b.ru", "password123", null));
        assertEquals(UserRole.ADMIN, service.update(created.id(), patch(null, null, null, UserRole.ADMIN)).role());
    }

    @Test
    void updateWithSameEmailInOtherCaseIsAllowed() {
        UserResponse created = service.create(newUser("a@b.ru", "password123", null));
        assertDoesNotThrow(() -> service.update(created.id(), patch(null, " A@B.ru ", null, null)));
    }

    @Test
    void updateCannotChangeEmail() {
        UserResponse created = service.create(newUser("a@b.ru", "password123", null));
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.update(created.id(), patch(null, "other@b.ru", null, null)));
        assertTrue(e.getErrors().containsKey("email"));
        assertEquals("a@b.ru", users.rows.get(created.id()).getEmail());
    }

    @Test
    void updateUnknownUserThrows() {
        assertThrows(UserNotFoundException.class, () -> service.update(UUID.randomUUID(), patch("x", null, null, null)));
    }

    @Test
    void deleteRemovesUser() {
        UserResponse created = service.create(newUser("a@b.ru", "password123", null));
        service.delete(created.id());
        assertFalse(users.existsById(created.id()));
    }

    @Test
    void deleteUnknownThrows() {
        assertThrows(UserNotFoundException.class, () -> service.delete(UUID.randomUUID()));
    }

    @Test
    void existsByEmailNormalizesAndHandlesNull() {
        service.create(newUser("a@b.ru", "password123", null));
        assertTrue(service.existsByEmail("  A@B.RU"));
        assertFalse(service.existsByEmail("c@d.ru"));
        assertFalse(service.existsByEmail(null));
    }

    @Test
    void findPasswordHashByEmail() {
        UserResponse created = service.create(newUser("a@b.ru", "password123", null));
        assertArrayEquals(users.rows.get(created.id()).getPasswordHash(),
                service.findPasswordHashByEmail("A@b.ru").orElseThrow());
        assertTrue(service.findPasswordHashByEmail(null).isEmpty());
        assertTrue(service.findPasswordHashByEmail("nobody@b.ru").isEmpty());
    }
}