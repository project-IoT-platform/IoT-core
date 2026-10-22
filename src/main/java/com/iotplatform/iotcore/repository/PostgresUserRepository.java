package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.Database;
import com.iotplatform.iotcore.entity.UserEntity;

import java.util.Optional;
import java.util.UUID;

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
    public boolean existsById(UUID id) {
        // TODO Лёша
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public UserEntity save(UserEntity user) {
        // TODO Лёша: INSERT, а если id уже есть, UPDATE (email при UPDATE не менять)
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public boolean deleteById(UUID id) {
        // TODO Лёша
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public Optional<byte[]> findPasswordHashByEmail(String email) {
        // TODO Лёша: достать password_hash и таблицы users где email = ...
        // Почта уже нормализована сервисом, сравнивать точно. Если пользователя нет, Optional.empty().
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public boolean existsByEmail(String email) {
        // TODO Лёша: есть ли в таблице строка где email = ...
        throw new UnsupportedOperationException("TODO Лёша");
    }
}