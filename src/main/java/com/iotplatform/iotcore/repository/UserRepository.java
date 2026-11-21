package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.UserEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<UserEntity> findById(UUID id); // ищет пользователя по id
    boolean existsById(UUID id); // существует ли пользователь с таким id?
    UserEntity save(UserEntity user); // сохраняет пользователя в бд
    boolean deleteById(UUID id);  // удаляет пользователя из бд по id
    boolean existsByEmail(String email);                    // есть ли пользователь с такой почтой
    Optional<byte[]> findPasswordHashByEmail(String email); // хеш пароля по почте (для входа)

    // ДОБАВЛЕНО: пользователь целиком по почте (для POST /api/login)
    Optional<UserEntity> findByEmail(String email);

    /**
     * НОВОЕ: страница пользователей для админки. emailQuery = часть почты (без учёта регистра) или null.
     * Порядок стабильный: по почте, затем по id.
     */
    default List<UserEntity> findPage(String emailQuery, int limit, int offset) {
        throw new UnsupportedOperationException("findPage");
    }
}