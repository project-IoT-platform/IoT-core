package com.iotplatform.iotcore.db;

import com.iotplatform.iotcore.exception.DatabaseException;

import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DatabaseUtils {

    private final String url;
    private final String user;
    private final String password;

    public DatabaseUtils(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static DatabaseUtils fromProperties(String fileName) {
        Properties props = new Properties();
        try (InputStream in = DatabaseUtils.class.getResourceAsStream(fileName)) {
            props.load(in);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось прочитать " + fileName + ": " + e);
        }
        return new DatabaseUtils(
                props.getProperty("db.url"),
                props.getProperty("db.user"),
                props.getProperty("db.password"));
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public int update(String sql, Object... params) throws DatabaseException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }

            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка запроса: " + sql, e);
        }
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) throws DatabaseException { // преобразование набора строки с "дырками" в SQL-запрос
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseException("ошибка SQL-запроса", e);
        }
    }

    public void initSchema() {
        System.out.println("TODO Лёша: создание таблиц из schema.sql ещё не написано");
    }
}