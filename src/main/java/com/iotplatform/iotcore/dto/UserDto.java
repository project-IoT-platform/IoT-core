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

    public UUID getId() { return id; }
    public String getEmail() { return email; }

    public String getPhone() { return phone; }
    public String getUsername() { return username; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public UserRole getRole() { return role; }
    public String getPassword() { return password; }

    public void setPhone(String phone) { this.phone = phone; }
    public void setUsername(String username) { this.username = username; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setRole(UserRole role) { this.role = role; }
    public void setPassword(String password) { this.password = password; }

    /**
     * При регистрации обязательны только почта и пароль.
     * Всё остальное (имя, телефон, дата рождения, роль) можно не присылать.
     * Но если поле прислали, оно должно быть корректным.
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

    @Override
    public String toString() {
        return "UserDto[id=" + id + ", username=" + username + ", email=" + email + ", role=" + role + "]";
    }
}