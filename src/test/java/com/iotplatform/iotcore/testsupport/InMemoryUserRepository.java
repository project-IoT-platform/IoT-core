package com.iotplatform.iotcore.testsupport;

import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Фейк вместо БД: пользователи в памяти. Почту сравнивает точно, как есть. */
public class InMemoryUserRepository implements UserRepository {
    private final Map<UUID, UserEntity> users = new LinkedHashMap<>();

    public int size() {
        return users.size();
    }

    @Override
    public Optional<UserEntity> findById(UUID id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public boolean existsById(UUID id) {
        return users.containsKey(id);
    }

    @Override
    public UserEntity save(UserEntity user) {
        users.put(user.getId(), user);
        return user;
    }

    @Override
    public boolean deleteById(UUID id) {
        return users.remove(id) != null;
    }

    @Override
    public boolean existsByEmail(String email) {
        return users.values().stream().anyMatch(u -> Objects.equals(u.getEmail(), email));
    }

    @Override
    public Optional<byte[]> findPasswordHashByEmail(String email) {
        return users.values().stream()
                .filter(u -> Objects.equals(u.getEmail(), email))
                .map(UserEntity::getPasswordHash)
                .findFirst();
    }
}