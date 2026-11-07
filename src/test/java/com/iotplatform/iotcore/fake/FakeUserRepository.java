package com.iotplatform.iotcore.fake;

import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Репозиторий пользователей в памяти. */
public class FakeUserRepository implements UserRepository {
    public final Map<UUID, UserEntity> rows = new HashMap<>();

    @Override
    public Optional<UserEntity> findById(UUID id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public boolean existsById(UUID id) {
        return rows.containsKey(id);
    }

    @Override
    public UserEntity save(UserEntity user) {
        rows.put(user.getId(), user);
        return user;
    }

    @Override
    public boolean deleteById(UUID id) {
        return rows.remove(id) != null;
    }

    @Override
    public boolean existsByEmail(String email) {
        return rows.values().stream().anyMatch(u -> email.equals(u.getEmail()));
    }

    @Override
    public Optional<byte[]> findPasswordHashByEmail(String email) {
        return rows.values().stream()
                .filter(u -> email.equals(u.getEmail()))
                .map(UserEntity::getPasswordHash)
                .findFirst();
    }
}