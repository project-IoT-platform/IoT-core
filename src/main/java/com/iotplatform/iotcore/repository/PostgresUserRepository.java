package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.exception.DatabaseException;
import com.iotplatform.iotcore.model.UserRole;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class PostgresUserRepository implements UserRepository {
    private final DatabaseUtils databaseUtils;

    public PostgresUserRepository(DatabaseUtils databaseUtils) {
        this.databaseUtils = databaseUtils;
    }

    @Override
    public Optional<UserEntity> findById(UUID id) {
        return databaseUtils.query("SELECT * FROM users WHERE id = ?",
                PostgresUserRepository::mapRow, id).stream().findFirst();
    }

    @Override
    public boolean existsById(UUID id) {
        return !databaseUtils.query("SELECT 1 FROM users WHERE id = ?",
                rs -> 1, id).isEmpty();
    }

    @Override
    public UserEntity save(UserEntity user) throws DatabaseException {
        databaseUtils.update(
                "INSERT INTO users (id, username, email, phone, birthdate, password_hash, user_role) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::role)",
                user.getId(), user.getUsername(), user.getEmail(), user.getPhone(), user.getDateOfBirth(), user.getPasswordHash(), user.getRole());

        return user;
    }

    @Override
    public boolean deleteById(UUID id) {
        return databaseUtils.update("DELETE FROM users WHERE id = ?", id) > 0;
    }

    @Override
    public Optional<byte[]> findPasswordHashByEmail(String email) {
        return databaseUtils.query("SELECT password_hash FROM users WHERE email = ?",
                rs -> rs.getBytes("password_hash"), email).stream().findFirst();
    }

    @Override
    public boolean existsByEmail(String email) {
        return !databaseUtils.query("SELECT 1 FROM users WHERE email = ?",
                rs -> 1, email).isEmpty();
    }

    private static UserEntity mapRow(ResultSet rs) throws DatabaseException {
        try {
            return new UserEntity(
                    rs.getObject("id", UUID.class),
                    rs.getString("username"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getObject("birthdate", LocalDate.class),
                    rs.getBytes("password_hash"),
                    UserRole.valueOf(rs.getString("user_role").toUpperCase(Locale.ROOT)));
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка преобразования в класс Java", e);
        }
    }
}