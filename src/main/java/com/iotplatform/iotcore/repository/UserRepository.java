package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.UserEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<UserEntity> findById(UUID id); // ищет польователя по id
    List<UserEntity> findAll(); // даёт список всех пользователей из базы
    boolean existsById(UUID id); // существует ли пользователь с таким id?
    UserEntity save(UserEntity user); // сохраняет пользователя в бд
    boolean deleteById(UUID id);  // удаляет пользователя из бд по id
}
