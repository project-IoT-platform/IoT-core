package com.iotplatform.iotcore.entity;

import com.iotplatform.iotcore.model.UserRole;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Пользователь в том виде, в котором он хранится в БД (с хешем пароля). Поля {@code id} и {@code email} неизменяемы.
 */
public class UserEntity {

    private final UUID id;
    private final String email;

    private String phone;
    private String username;
    private LocalDate dateOfBirth;
    private byte[] passwordHash;
    private UserRole role;

    /**
     * Создаёт пустую сущность: {@code id} и {@code email} равны {@code null}.
     */
    public UserEntity() {
        this.id = null;
        this.email = null;
    }

    /**
     * Создаёт сущность со всеми полями.
     *
     * @param id идентификатор пользователя
     * @param username имя пользователя
     * @param email почта
     * @param phone телефон
     * @param dateOfBirth дата рождения
     * @param passwordHash хеш пароля (соль и ключ PBKDF2)
     * @param role роль
     */
    public UserEntity(UUID id, String username, String email, String phone,
                      LocalDate dateOfBirth, byte[] passwordHash, UserRole role) {
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.username = username;
        this.dateOfBirth = dateOfBirth;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /**
     * Возвращает идентификатор пользователя.
     *
     * @return идентификатор пользователя
     */
    public UUID getId() { return id; }
    /**
     * Возвращает почту.
     *
     * @return почта
     */
    public String getEmail() { return email; }

    /**
     * Возвращает телефон.
     *
     * @return телефон
     */
    public String getPhone() { return phone; }
    /**
     * Возвращает имя пользователя.
     *
     * @return имя пользователя
     */
    public String getUsername() { return username; }
    /**
     * Возвращает дату рождения.
     *
     * @return дата рождения
     */
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    /**
     * Возвращает хеш пароля.
     *
     * @return хеш пароля
     */
    public byte[] getPasswordHash() { return passwordHash; }
    /**
     * Возвращает роль.
     *
     * @return роль
     */
    public UserRole getRole() { return role; }

    // Сеттера для email нет специально: почту менять нельзя
    /**
     * Задаёт телефон.
     *
     * @param phone телефон
     */
    public void setPhone(String phone) { this.phone = phone; }
    /**
     * Задаёт имя пользователя.
     *
     * @param username имя пользователя
     */
    public void setUsername(String username) { this.username = username; }
    /**
     * Задаёт дату рождения.
     *
     * @param dateOfBirth дата рождения
     */
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    /**
     * Задаёт хеш пароля.
     *
     * @param passwordHash хеш пароля
     */
    public void setPasswordHash(byte[] passwordHash) { this.passwordHash = passwordHash; }
    /**
     * Задаёт роль.
     *
     * @param role роль
     */
    public void setRole(UserRole role) { this.role = role; }
}