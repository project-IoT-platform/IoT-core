package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.Database;
import com.iotplatform.iotcore.entity.UserEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// ===== ДЛЯ ЛЁШИ =====
// посмотри, совпадают ли типы полей в "entity" с тем что у тебя в бд
public class PostgresUserRepository implements UserRepository {
    private final Database database;

    public PostgresUserRepository(Database database) {
        this.database = database;
    }

    @Override
    public Optional<UserEntity> findById(UUID id) {
        // TODO Лёша
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public List<UserEntity> findAll() {
        // TODO Лёша
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public boolean existsById(UUID id) {
        // TODO Лёша
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public UserEntity save(UserEntity user) {
        // TODO Лёша
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public boolean deleteById(UUID id) {
        // TODO Лёша
        throw new UnsupportedOperationException("TODO Лёша");
    }
}
