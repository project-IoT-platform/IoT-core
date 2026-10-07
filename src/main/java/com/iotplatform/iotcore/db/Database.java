package com.iotplatform.iotcore.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private final String url = env("DB_URL", "jdbc:postgresql://localhost:5432/iot");
    private final String user = env("DB_USER", "iot");
    private final String password = env("DB_PASSWORD", "iot");

    private static String env(String name, String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }

    public Connection connection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Выполняет schema.sql при старте, чтобы таблицы существовали. */
    public void initSchema() {
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection c = connection(); Statement s = c.createStatement()) {
                s.execute(sql);
            }
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Не удалось инициализировать БД", e);
        }
    }
}
