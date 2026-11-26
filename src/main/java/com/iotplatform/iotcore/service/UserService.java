package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.exception.UserNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.UserRole;
import com.iotplatform.iotcore.repository.UserRepository;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Бизнес-логика пользователей. Почта нормализуется (пробелы по краям срезаются, регистр приводится к нижнему),
 * пароль хранится только в виде хеша.
 */
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;

    /**
     * Создаёт сервис.
     *
     * @param users хранилище пользователей
     */
    public UserService(UserRepository users) {
        this.users = users;
    }

    public Optional<UUID> authenticate(String email, String password) {
        Optional<UserEntity> user = users.findByEmail(normalizeEmail(email));
        byte[] hash = user.map(UserEntity::getPasswordHash).orElse(null);
        return PasswordHasher.matches(password, hash) ? user.map(UserEntity::getId) : Optional.empty();
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Ищет пользователя по id.
     *
     * @param id идентификатор пользователя
     * @return данные пользователя без пароля
     * @throws UserNotFoundException если пользователя нет
     */
    public UserResponse findById(UUID id) {
        return users.findById(id).map(this::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Проверяет, занята ли почта.
     *
     * @param email почта (будет нормализована); {@code null} даёт {@code false}
     * @return {@code true}, если пользователь с такой почтой есть
     */
    public boolean existsByEmail(String email) {
        return email != null && users.existsByEmail(normalizeEmail(email));
    }

    /**
     * Ищет хеш пароля по почте.
     *
     * @param email почта (будет нормализована)
     * @return хеш пароля; пустой {@code Optional}, если почта {@code null} или пользователя нет
     */
    public Optional<byte[]> findPasswordHashByEmail(String email) {
        if (email == null) return Optional.empty();
        return users.findPasswordHashByEmail(normalizeEmail(email));
    }

    /**
     * Создаёт пользователя с новым id. Роль по умолчанию {@code GUEST}, пароль хешируется.
     *
     * @param dto данные пользователя (обязательны почта и пароль)
     * @return созданный пользователь
     * @throws ValidationException если данные некорректны или почта уже занята
     */
    public UserResponse create(UserDto dto) {
        dto.validate(true);
        String email = normalizeEmail(dto.getEmail());
        if (users.existsByEmail(email)) {
            throw new ValidationException(Map.of("email", "пользователь с такой почтой уже существует"));
        }

        UserRole role = dto.getRole() != null ? dto.getRole() : UserRole.GUEST;

        String username = dto.getUsername() == null ? email.substring(0, email.indexOf('@')) : dto.getUsername();
        UserEntity entity = new UserEntity(UUID.randomUUID(), username, email,
                dto.getPhone(), dto.getDateOfBirth(), PasswordHasher.hash(dto.getPassword()), role);
        UserResponse response = toResponse(users.save(entity));
        log.info("User {} created", entity.getId());
        return response;
    }

    /**
     * Обновляет только те поля, которые переданы (не {@code null}). Почту изменить нельзя.
     *
     * @param id идентификатор пользователя
     * @param dto новые значения полей
     * @return обновлённый пользователь
     * @throws UserNotFoundException если пользователя нет
     * @throws ValidationException если данные некорректны или передана другая почта
     */
    public UserResponse update(UUID id, UserDto dto) {
        dto.validate(false);
        UserEntity user = users.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (dto.getEmail() != null && !dto.getEmail().trim().equalsIgnoreCase(user.getEmail())) {
            throw new ValidationException(Map.of("email", "почту изменить нельзя"));
        }

        if (dto.getUsername() != null) user.setUsername(dto.getUsername());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getDateOfBirth() != null) user.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getRole() != null) user.setRole(dto.getRole());
        if (dto.getPassword() != null) user.setPasswordHash(PasswordHasher.hash(dto.getPassword()));
        log.info("User {} updated", id);

        return toResponse(dto.getPassword() == null ? users.saveProfile(user) : users.save(user));
    }

    /**
     * Удаляет пользователя.
     *
     * @param id идентификатор пользователя
     * @throws UserNotFoundException если пользователя нет
     */
    public void delete(UUID id) {
        if (!users.deleteById(id)) {
            throw new UserNotFoundException(id);
        }
        log.info("User {} deleted", id);
    }

    private UserResponse toResponse(UserEntity e) {
        return new UserResponse(e.getId(), e.getUsername(), e.getEmail(),
                e.getPhone(), e.getDateOfBirth(), e.getRole());
    }
}