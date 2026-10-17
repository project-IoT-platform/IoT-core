package com.iotplatform.iotcore.db;

import java.sql.Connection;

// ===== ДЛЯ ЛЁШИ =====
// подключение к PostgreSQL.
public class Database {
    public Connection connection() {
        throw new UnsupportedOperationException("TODO Лёша: открыть соединение с БД");
    }
    public void initSchema() {
        System.out.println("TODO Лёша: создание таблиц из schema.sql ещё не написано");
    }
}