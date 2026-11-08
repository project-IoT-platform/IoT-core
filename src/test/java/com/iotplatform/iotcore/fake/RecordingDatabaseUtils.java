package com.iotplatform.iotcore.fake;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.db.RowMapper;
import com.iotplatform.iotcore.exception.DatabaseException;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DatabaseUtils без базы: запоминает SQL и параметры, а на SELECT
 * отдаёт заранее заданные строки через поддельный ResultSet.
 */
public class RecordingDatabaseUtils extends DatabaseUtils {
    public record Call(String sql, List<Object> params) {}

    public final List<Call> updates = new ArrayList<>();
    public final List<Call> queries = new ArrayList<>();
    public List<Map<String, Object>> rowsToReturn = new ArrayList<>();
    public int updateResult = 1;

    public RecordingDatabaseUtils() {
        super("jdbc:none", "u", "p");
    }

    @Override
    public int update(String sql, Object... params) {
        updates.add(new Call(sql, java.util.Arrays.asList(params.clone())));   // asList: параметры бывают null
        return updateResult;
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        queries.add(new Call(sql, java.util.Arrays.asList(params)));
        List<T> result = new ArrayList<>();
        try {
            for (Map<String, Object> row : rowsToReturn) {
                result.add(mapper.map(resultSet(row)));
            }
        } catch (SQLException e) {
            throw new DatabaseException("ошибка SQL-запроса", e);
        }
        return result;
    }

    public Call lastUpdate() {
        return updates.get(updates.size() - 1);
    }

    /** Поддельный ResultSet для одной строки. Колонки ищутся по имени; индекс 1 = первая колонка. */
    public static ResultSet resultSet(Map<String, Object> row) {
        List<String> order = new ArrayList<>(row.keySet());
        Map<String, Object> state = new HashMap<>();
        state.put("wasNull", false);
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    Object key = args != null && args.length > 0 ? args[0] : null;
                    String column = key instanceof Integer i ? order.get(i - 1) : (String) key;
                    if (method.getName().equals("wasNull")) return state.get("wasNull");
                    if (column != null && !row.containsKey(column)) {
                        throw new SQLException("нет колонки " + column);
                    }
                    Object value = column == null ? null : row.get(column);
                    state.put("wasNull", value == null);
                    return switch (method.getName()) {
                        case "getObject", "getString", "getBytes" -> value;
                        case "getShort" -> value == null ? (short) 0 : ((Number) value).shortValue();
                        case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                        case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                        case "getDouble" -> value == null ? 0.0 : ((Number) value).doubleValue();
                        case "getBoolean" -> value != null && (Boolean) value;
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }
}