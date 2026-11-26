package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.UserEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Хранилище пользователей.
 */
public interface UserRepository {
    Optional<UserEntity> findByEmail(String email);

    /**
     * Ищет пользователя по id.
     *
     * @param id идентификатор пользователя
     * @return пользователь или пустой {@code Optional}
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    Optional<UserEntity> findById(UUID id); // ищет пользователя по id
    /**
     * Проверяет, есть ли пользователь с таким id.
     *
     * @param id идентификатор пользователя
     * @return {@code true}, если пользователь существует
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    boolean existsById(UUID id); // существует ли пользователь с таким id?
    /**
     * Сохраняет пользователя: создаёт запись или обновляет существующую (почта при обновлении не меняется).
     *
     * @param user сохраняемый пользователь
     * @return сохранённый пользователь
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    default UserEntity saveProfile(UserEntity user) {
        return save(user);
    }

    UserEntity save(UserEntity user); // сохраняет пользователя в бд
    /**
     * Удаляет пользователя.
     *
     * @param id идентификатор пользователя
     * @return {@code true}, если что-то было удалено
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    boolean deleteById(UUID id);  // удаляет пользователя из бд по id
    /**
     * Проверяет, занята ли почта.
     *
     * @param email почта (в том виде, в каком она хранится)
     * @return {@code true}, если пользователь с такой почтой есть
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    boolean existsByEmail(String email);                    // есть ли пользователь с такой почтой
    /**
     * Ищет хеш пароля по почте (нужен для входа).
     *
     * @param email почта (в том виде, в каком она хранится)
     * @return хеш пароля или пустой {@code Optional}, если пользователя нет
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    Optional<byte[]> findPasswordHashByEmail(String email); // хеш пароля по почте (для входа)
}