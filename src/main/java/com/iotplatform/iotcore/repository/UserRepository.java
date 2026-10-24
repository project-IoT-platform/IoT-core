package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.UserEntity;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<UserEntity> findById(UUID id); // ищет пользователя по id
    boolean existsById(UUID id); // существует ли пользователь с таким id?
    UserEntity save(UserEntity user); // сохраняет пользователя в бд
    boolean deleteById(UUID id);  // удаляет пользователя из бд по id
    boolean existsByEmail(String email);                    // есть ли пользователь с такой почтой
    Optional<byte[]> findPasswordHashByEmail(String email); // хеш пароля по почте (для входа)
}