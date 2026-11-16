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
    public int saveCalls = 0; // Используется в тестах

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
        saveCalls++;
        rows.put(user.getId(), user);
        return user;
    }

    @Override
    public boolean deleteById(UUID id) {
        return rows.remove(id) != null;
    }

    @Override
    public boolean existsByEmail(String email) {
        return rows.values().stream().anyMatch(u -> email.equalsIgnoreCase(u.getEmail()));
    }

    @Override
    public Optional<byte[]> findPasswordHashByEmail(String email) {
        return rows.values().stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                .map(UserEntity::getPasswordHash)
                .findFirst();
    }

    // ЭТОГО МЕТОДА НЕ ХВАТАЛО (он требует интерфейс)
    @Override
    public Optional<UserEntity> findByEmail(String email) {
        return rows.values().stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                .findFirst();
    }
}