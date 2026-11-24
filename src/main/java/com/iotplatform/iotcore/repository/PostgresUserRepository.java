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

/**
 * Реализация {@link UserRepository} на PostgreSQL (таблица {@code users}).
 */
public class PostgresUserRepository implements UserRepository {
    private final DatabaseUtils databaseUtils;

    /**
     * Создаёт репозиторий.
     *
     * @param databaseUtils доступ к БД
     */
    public PostgresUserRepository(DatabaseUtils databaseUtils) {
        this.databaseUtils = databaseUtils;
    }

    /**
     * Ищет пользователя по id.
     *
     * @param id идентификатор пользователя
     * @return пользователь или пустой {@code Optional}
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public Optional<UserEntity> findById(UUID id) {
        return databaseUtils.query("SELECT * FROM users WHERE id = ?",
                PostgresUserRepository::mapRow, id).stream().findFirst();
    }

    /**
     * Проверяет, есть ли пользователь с таким id.
     *
     * @param id идентификатор пользователя
     * @return {@code true}, если пользователь существует
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public boolean existsById(UUID id) {
        return !databaseUtils.query("SELECT 1 FROM users WHERE id = ?",
                rs -> 1, id).isEmpty();
    }

    /**
     * Вставляет пользователя, а при совпадении id обновляет имя, телефон, дату рождения, хеш пароля и роль.
     * Почта при обновлении не меняется.
     *
     * @param user сохраняемый пользователь
     * @return тот же пользователь
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public UserEntity save(UserEntity user) throws DatabaseException {
        databaseUtils.update(
                "INSERT INTO users (id, username, email, phone, birthdate, password_hash, user_role) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::role) "
                        + "ON CONFLICT (id) DO UPDATE SET "
                        + "username = EXCLUDED.username, phone = EXCLUDED.phone, "
                        + "birthdate = EXCLUDED.birthdate, password_hash = EXCLUDED.password_hash, "
                        + "user_role = EXCLUDED.user_role",
                user.getId(), user.getUsername(), user.getEmail(), user.getPhone(),
                user.getDateOfBirth(), user.getPasswordHash(), user.getRole());

        return user;
    }

    /**
     * Удаляет пользователя.
     *
     * @param id идентификатор пользователя
     * @return {@code true}, если запись удалена
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public boolean deleteById(UUID id) {
        return databaseUtils.update("DELETE FROM users WHERE id = ?", id) > 0;
    }

    /**
     * Ищет хеш пароля по почте.
     *
     * @param email почта
     * @return хеш пароля или пустой {@code Optional}
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public Optional<byte[]> findPasswordHashByEmail(String email) {
        return databaseUtils.query("SELECT password_hash FROM users WHERE email = ?",
                rs -> rs.getBytes("password_hash"), email).stream().findFirst();
    }

    /**
     * Проверяет, занята ли почта.
     *
     * @param email почта
     * @return {@code true}, если пользователь с такой почтой есть
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
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