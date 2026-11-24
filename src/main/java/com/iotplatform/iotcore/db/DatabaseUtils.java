package com.iotplatform.iotcore.db;

import com.iotplatform.iotcore.exception.DatabaseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.time.Instant;
import java.time.ZoneOffset;
/**
 * Простая обёртка над JDBC: открывает соединение на каждую операцию, подставляет параметры в SQL-запрос,
 * выполняет запросы и применяет схему БД.
 */
public class DatabaseUtils {

    private final String url;
    private final String user;
    private final String password;

    /**
     * Создаёт объект с параметрами подключения.
     *
     * @param url JDBC-адрес базы
     * @param user имя пользователя БД
     * @param password пароль пользователя БД
     */
    public DatabaseUtils(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /**
     * Создаёт объект по файлу настроек из classpath с ключами {@code db.url}, {@code db.user}, {@code db.password}.
     *
     * @param fileName путь к файлу в ресурсах, например {@code /db.properties}
     * @return настроенный объект
     * @throws RuntimeException если файл не найден или не прочитан
     */
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

    /**
     * Открывает новое соединение с БД. Закрывать его должен вызывающий код.
     *
     * @return новое соединение
     * @throws SQLException если подключиться не удалось
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Выполняет изменяющий запрос (INSERT, UPDATE, DELETE).
     *
     * @param sql SQL с символами {@code ?} вместо параметров
     * @param params значения параметров; {@code Instant} преобразуется в UTC-время, {@code enum} в строку с его именем
     * @return количество затронутых строк
     * @throws DatabaseException если запрос не выполнился
     */
    public int update(String sql, Object... params) throws DatabaseException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setParams(ps, params);

            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка запроса: " + sql, e);
        }
    }

    /**
     * Выполняет SELECT и преобразует каждую строку результата в объект.
     *
     * @param sql SQL с символами {@code ?} вместо параметров
     * @param mapper преобразователь строки результата в объект
     * @param params значения параметров запроса
     * @param <T> тип объектов результата
     * @return список объектов в порядке строк результата (может быть пустым)
     * @throws DatabaseException если запрос не выполнился
     */
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) throws DatabaseException { // преобразование набора строки с "дырками" в SQL-запрос
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setParams(ps, params);

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

    private void setParams(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p instanceof Instant) {
                p = ((Instant) p).atOffset(ZoneOffset.UTC);   // Instant -> OffsetDateTime
            } else if (p instanceof Enum) {
                p = ((Enum<?>) p).name();                      // enum -> строка с его именем
            }
            ps.setObject(i + 1, p);
        }
    }

    /**
     * Выполняет скрипт {@code /schema.sql} из ресурсов. Повторный запуск безопасен, если скрипт идемпотентен.
     *
     * @throws RuntimeException если скрипт не найден, не прочитан или выполнился с ошибкой
     */
    public void initSchema() {
        String script;
        try (InputStream in = DatabaseUtils.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new RuntimeException("Не найден /schema.sql в resources");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось прочитать schema.sql", e);
        }

        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {
            st.execute(script);
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка при выполнении schema.sql", e);
        }
    }
}