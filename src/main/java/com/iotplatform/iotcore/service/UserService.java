package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.UserDto;
import com.iotplatform.iotcore.dto.UserResponse;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.exception.UserNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.UserRole;
import com.iotplatform.iotcore.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UserService {
    private static final System.Logger LOG = System.getLogger(UserService.class.getName());

    // сколько живёт неподтверждённая регистрация
    private static final Duration PENDING_TTL = Duration.ofMinutes(30);

    private final UserRepository users;

    // ИЗМЕНЕНО: пользователь уже в БД (чтобы Notification-service нашёл email),
    // а здесь хранится только метка «не подтверждён» и срок её жизни
    private final Map<UUID, Instant> pending = new ConcurrentHashMap<>();

    public UserService(UserRepository users) {
        this.users = users;
        // раз в минуту удаляем из БД регистрации, которые не подтвердили вовремя
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pending-users-cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleWithFixedDelay(() -> {
            try {
                purgeExpired();
            } catch (Exception e) {
                LOG.log(System.Logger.Level.ERROR, "Не удалось очистить неподтверждённые регистрации", e);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // колонка username в БД NOT NULL, а при регистрации имя не вводится,
    // поэтому по умолчанию берём часть почты до «@»
    private static String defaultUsername(String email) {
        int at = email.indexOf('@');
        String name = (at > 0 ? email.substring(0, at) : email).trim();
        return name.isEmpty() ? "user" : name;
    }

    // удаляет из БД и из памяти регистрации, у которых вышел срок подтверждения
    private synchronized void purgeExpired() {
        Instant now = Instant.now();
        for (Map.Entry<UUID, Instant> e : pending.entrySet()) {
            if (e.getValue().isBefore(now)) {
                pending.remove(e.getKey());
                users.deleteById(e.getKey());
                LOG.log(System.Logger.Level.INFO, "Регистрация " + e.getKey() + " не подтверждена вовремя, удалена");
            }
        }
    }

    public UserResponse findById(UUID id) {
        return users.findById(id).map(this::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    public boolean existsByEmail(String email) {
        return email != null && users.existsByEmail(normalizeEmail(email));
    }

    public Optional<byte[]> findPasswordHashByEmail(String email) {
        if (email == null) return Optional.empty();
        return users.findPasswordHashByEmail(normalizeEmail(email));
    }

    // вход по почте и паролю. Пусто = неверная почта или пароль (причину не раскрываем).
    // ИЗМЕНЕНО: неподтверждённого пользователя не пускаем, хотя он уже есть в БД
    public Optional<UserResponse> login(String email, String password) {
        if (email == null || password == null) return Optional.empty();
        purgeExpired();
        Optional<UserEntity> found = users.findByEmail(normalizeEmail(email));
        if (found.isEmpty()) {
            PasswordHasher.hash(password); // тратим столько же времени, чтобы по скорости ответа нельзя было угадать почту
            return Optional.empty();
        }
        UserEntity user = found.get();
        boolean passwordOk = PasswordHasher.verify(password, user.getPasswordHash());
        if (!passwordOk || pending.containsKey(user.getId())) {
            return Optional.empty();
        }
        LOG.log(System.Logger.Level.INFO, "Пользователь " + user.getId() + " вошёл");
        return Optional.of(toResponse(user));
    }

    // ИЗМЕНЕНО: сохраняем в БД сразу, но помечаем как неподтверждённого
    public synchronized UserResponse create(UserDto dto) {
        dto.validate(true);
        String email = normalizeEmail(dto.getEmail());
        purgeExpired();

        Optional<UserEntity> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            UUID oldId = existing.get().getId();
            if (pending.containsKey(oldId)) {
                // прошлая попытка так и не была подтверждена: заменяем её новой
                pending.remove(oldId);
                users.deleteById(oldId);
                LOG.log(System.Logger.Level.INFO, "Старая неподтверждённая регистрация " + oldId + " заменена");
            } else {
                throw new ValidationException(Map.of("email", "пользователь с такой почтой уже существует"));
            }
        }

        UserRole role = dto.getRole() != null ? dto.getRole() : UserRole.GUEST;
        String username = dto.getUsername() != null ? dto.getUsername() : defaultUsername(email);

        UserEntity entity = new UserEntity(UUID.randomUUID(), username, email,
                dto.getPhone(), dto.getDateOfBirth(), PasswordHasher.hash(dto.getPassword()), role);
        UserResponse created = toResponse(users.save(entity));
        pending.put(created.id(), Instant.now().plus(PENDING_TTL));
        LOG.log(System.Logger.Level.INFO, "Пользователь " + created.id() + " (" + email + ") создан, ждёт подтверждения");
        return created;
    }

    // ИЗМЕНЕНО: подтверждение только снимает метку «не подтверждён»
    public synchronized UserResponse confirm(UUID id) {
        purgeExpired();
        UserEntity user = users.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id)); // нет в БД или срок истёк -> 404
        if (pending.remove(id) != null) {
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
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getDateOfBirth() != null) user.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getRole() != null) user.setRole(dto.getRole());
        if (dto.getPassword() != null) user.setPasswordHash(PasswordHasher.hash(dto.getPassword()));

        UserResponse updated = toResponse(users.save(user));
        LOG.log(System.Logger.Level.INFO, "Пользователь " + id + " обновлён");
        return updated;
    }

    // ИЗМЕНЕНО: заодно снимаем метку (отмена на экране 2 тоже идёт через DELETE)
    public synchronized void delete(UUID id) {
        pending.remove(id);
        if (!users.deleteById(id)) {
            throw new UserNotFoundException(id);
        }
        LOG.log(System.Logger.Level.INFO, "Пользователь " + id + " удалён");
    }

    private UserResponse toResponse(UserEntity e) {
        return new UserResponse(e.getId(), e.getUsername(), e.getEmail(),
                e.getPhone(), e.getDateOfBirth(), e.getRole());
    }
}