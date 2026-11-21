package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.exception.ForbiddenException;
import com.iotplatform.iotcore.exception.UserNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.UserRole;
import com.iotplatform.iotcore.repository.UserRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UserService {
    private static final System.Logger LOG = System.getLogger(UserService.class.getName());

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizePhone(String phone) {
        return phone == null ? null : phone.trim();
    }

    // колонка username в БД NOT NULL, а при регистрации имя не вводится,
    // поэтому по умолчанию берём часть почты до «@»
    private static String defaultUsername(String email) {
        int at = email.indexOf('@');
        String name = (at > 0 ? email.substring(0, at) : email).trim();
        return name.isEmpty() ? "user" : name;
    }

    public UserResponse findById(UUID id) {
        return users.findById(id).map(this::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * НОВОЕ: страница пользователей (вкладка «Пользователи», только для админов).
     * Роль запрашивающего берём из БД, а не из запроса. Не админ -> ForbiddenException (HTTP 403).
     */
    public List<UserResponse> list(UUID requesterId, String emailQuery, int limit, int offset) {
        UserEntity requester = users.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException(requesterId));
        if (requester.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Раздел доступен только администраторам");
        }
        return users.findPage(emailQuery, limit, offset).stream().map(this::toResponse).toList();
    }

    public boolean existsByEmail(String email) {
        return email != null && users.existsByEmail(normalizeEmail(email));
    }

    public Optional<byte[]> findPasswordHashByEmail(String email) {
        if (email == null) return Optional.empty();
        return users.findPasswordHashByEmail(normalizeEmail(email));
    }

    // Пусто = неверная почта или пароль. Неподтверждённый аккаунт -> 400 (ValidationException).
    // Подтверждение проверяем только после пароля, чтобы не раскрывать, есть ли такая почта.
    public Optional<UserResponse> login(String email, String password) {
        if (email == null || password == null) return Optional.empty();
        Optional<UserEntity> found = users.findByEmail(normalizeEmail(email));
        if (found.isEmpty()) {
            PasswordHasher.hash(password); // та же задержка, чтобы по времени ответа нельзя было угадать почту
            return Optional.empty();
        }
        UserEntity user = found.get();
        if (!PasswordHasher.verify(password, user.getPasswordHash())) {
            return Optional.empty();
        }
        if (!user.isConfirmed()) {
            throw new ValidationException(Map.of("error",
                    "Аккаунт не подтверждён. Нажмите «Зарегистрироваться», чтобы получить письмо с ID заново"));
        }
        LOG.log(System.Logger.Level.INFO, "Пользователь " + user.getId() + " вошёл");
        return Optional.of(toResponse(user));
    }

    // Новый пользователь создаётся с is_confirmed = false.
    // Почта подтверждённого аккаунта занята; почта брошенной регистрации перезаписывается.
    public synchronized UserResponse create(UserDto dto) {
        dto.validate(true);
        String email = normalizeEmail(dto.getEmail());
        String phone = normalizePhone(dto.getPhone());
        UserRole role = dto.getRole() != null ? dto.getRole() : UserRole.GUEST;
        String username = dto.getUsername() != null ? dto.getUsername() : defaultUsername(email);
        byte[] hash = PasswordHasher.hash(dto.getPassword());

        Optional<UserEntity> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            UserEntity user = existing.get();
            if (user.isConfirmed()) {
                throw new ValidationException(Map.of("email", "пользователь с такой почтой уже существует"));
            }
            user.setUsername(username);
            user.setPhone(phone);
            user.setDateOfBirth(dto.getDateOfBirth());
            user.setPasswordHash(hash);
            user.setRole(role);
            user.setConfirmed(false);
            UserResponse updated = toResponse(users.save(user));
            LOG.log(System.Logger.Level.INFO, "Брошенная регистрация " + updated.id() + " (" + email + ") перезаписана");
            return updated;
        }

        UserEntity entity = new UserEntity(UUID.randomUUID(), username, email,
                phone, dto.getDateOfBirth(), hash, role, false);
        UserResponse created = toResponse(users.save(entity));
        LOG.log(System.Logger.Level.INFO, "Пользователь " + created.id() + " (" + email + ") создан, ждёт подтверждения");
        return created;
    }

    // Подтверждение регистрации: is_confirmed = true. Нет такого пользователя -> 404.
    public synchronized UserResponse confirm(UUID id) {
        UserEntity user = users.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        if (!user.isConfirmed()) {
            user.setConfirmed(true);
            users.save(user);
            LOG.log(System.Logger.Level.INFO, "Пользователь " + id + " подтверждён");
        }
        return toResponse(user);
    }

    public UserResponse update(UUID id, UserDto dto) {
        dto.validate(false);
        UserEntity user = users.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (dto.getEmail() != null && !dto.getEmail().trim().equalsIgnoreCase(user.getEmail())) {
            throw new ValidationException(Map.of("email", "почту изменить нельзя"));
        }

        if (dto.getUsername() != null) user.setUsername(dto.getUsername());
        if (dto.getPhone() != null) user.setPhone(normalizePhone(dto.getPhone()));
        if (dto.getDateOfBirth() != null) user.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getRole() != null) user.setRole(dto.getRole());
        if (dto.getPassword() != null) user.setPasswordHash(PasswordHasher.hash(dto.getPassword()));

        UserResponse updated = toResponse(users.save(user));
        LOG.log(System.Logger.Level.INFO, "Пользователь " + id + " обновлён");
        return updated;
    }

    public void delete(UUID id) {
        if (!users.deleteById(id)) {
            throw new UserNotFoundException(id);
        }
        LOG.log(System.Logger.Level.INFO, "Пользователь " + id + " удалён");
    }

    private UserResponse toResponse(UserEntity e) {
        return new UserResponse(e.getId(), e.getUsername(), e.getEmail(),
                e.getPhone(), e.getDateOfBirth(), e.getRole(), e.isConfirmed());
    }
}
