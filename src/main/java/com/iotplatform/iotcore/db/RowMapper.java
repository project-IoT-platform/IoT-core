package com.iotplatform.iotcore.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Преобразует текущую строку {@link ResultSet} в объект.
 *
 * @param <T> тип создаваемого объекта
 */
@FunctionalInterface
public interface RowMapper<T> {
    /**
     * Читает текущую строку результата.
     *
     * @param rs результат запроса, курсор стоит на нужной строке
     * @return объект, построенный по строке
     * @throws SQLException если колонку не удалось прочитать
     */
    T map(ResultSet rs) throws SQLException;
}