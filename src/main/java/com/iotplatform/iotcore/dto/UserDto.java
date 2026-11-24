package com.iotplatform.iotcore.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.UserRole;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Данные пользователя из запроса на создание или изменение. Содержит пароль, поэтому наружу не возвращается
 * (для ответов есть {@link UserResponse}). Почта задаётся один раз и не меняется.
 */
public class UserDto {

    private static final int MAX_EMAIL_LENGTH = 254;

    // локальная часть: без точки в начале, в конце и подряд; домен: метки через точку без дефиса по краям;
    // зона: минимум 2 буквы (так отсеиваются user@, @host, user@host, user@@host)
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9_%+-]+(\\.[A-Za-z0-9_%+-]+)*"
                    + "@"
                    + "([A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?\\.)+"
                    + "[A-Za-z]{2,}$");

    private final UUID id;
    private final String email;

    private String phone;
    private String username;
    private LocalDate dateOfBirth;
    private UserRole role;
    private String password;

    /**
     * Создаёт DTO пользователя (используется Jackson при разборе JSON).
     *
     * @param id идентификатор пользователя
     * @param username имя пользователя
     * @param email почта
     * @param phone телефон
     * @param dateOfBirth дата рождения
     * @param role роль
     * @param password пароль в открытом виде
     */
    @JsonCreator
    public UserDto(@JsonProperty("id") UUID id,
                   @JsonProperty("username") String username,
                   @JsonProperty("email") String email,
                   @JsonProperty("phone") String phone,
                   @JsonProperty("dateOfBirth") LocalDate dateOfBirth,
                   @JsonProperty("role") UserRole role,
                   @JsonProperty("password") String password) {
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.username = username;
        this.dateOfBirth = dateOfBirth;
        this.role = role;
        this.password = password;
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
     * Возвращает роль.
     *
     * @return роль
     */
    public UserRole getRole() { return role; }
    /**
     * Возвращает пароль в открытом виде.
     *
     * @return пароль в открытом виде
     */
    public String getPassword() { return password; }

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
     * Задаёт роль.
     *
     * @param role роль
     */
    public void setRole(UserRole role) { this.role = role; }
    /**
     * Задаёт пароль в открытом виде.
     *
     * @param password пароль в открытом виде
     */
    public void setPassword(String password) { this.password = password; }

    /**
     * Проверяет данные. При создании обязательны только корректная почта и пароль, остальное можно не присылать,
     * но если поле прислали, оно должно быть корректным: пароль не короче 8 символов, имя не пустое, дата рождения не в будущем.
     *
     * @param creating {@code true} при регистрации (проверяются почта и наличие пароля), {@code false} при изменении
     * @throws com.iotplatform.iotcore.exception.ValidationException с картой «поле: описание ошибки», если есть нарушения
     */
    public void validate(boolean creating) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (creating) {
            if (!isValidEmail(email)) errors.put("email", "некорректный адрес");
            if (password == null) errors.put("password", "обязательное поле");
        }
        if (password != null && password.length() < 8) {
            errors.put("password", "минимум 8 символов");
        }
        if (username != null && username.isBlank()) {
            errors.put("username", "не может быть пустым");
        }
        if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now())) {
            errors.put("dateOfBirth", "дата не может быть в будущем");
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    // пробелы по краям срезаем, как это делает UserService при сохранении
    private static boolean isValidEmail(String email) {
        if (email == null) return false;
        String trimmed = email.trim();
        return trimmed.length() <= MAX_EMAIL_LENGTH && EMAIL.matcher(trimmed).matches();
    }

    /**
     * Строка для логов: без пароля и телефона.
     *
     * @return представление вида {@code UserDto[id=..., username=..., email=..., role=...]}
     */
    @Override
    public String toString() {
        return "UserDto[id=" + id + ", username=" + username + ", email=" + email + ", role=" + role + "]";
    }
}