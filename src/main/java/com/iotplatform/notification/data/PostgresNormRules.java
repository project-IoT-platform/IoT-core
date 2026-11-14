package com.iotplatform.notification.data;

import com.iotplatform.notification.db.Db;
import com.iotplatform.notification.model.NormOperator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Команда /norm в таблицах rule и device_to_rule. Всё в одной транзакции: либо всё записалось, либо ничего.
 *   1) ищем правило устройства (device_id + telemetry_type + operator);
 *   2) нет такого -> INSERT в rule (is_strict = false) и INSERT в device_to_rule;
 *   3) есть, и оно только у этого устройства -> UPDATE rule SET value;
 *   4) есть, но то же правило привязано и к другим устройствам -> делаем копию с новым value
 *      и перепривязываем на неё только это устройство (у остальных норма не меняется).
 */
public class PostgresNormRules implements NormRules {
    private static final Logger log = LoggerFactory.getLogger(PostgresNormRules.class);

    static final String FIND = "SELECT r.id, r.is_strict, "
            + "(SELECT COUNT(*) FROM device_to_rule x WHERE x.rule_id = r.id) AS links "
            + "FROM rule r JOIN device_to_rule d ON d.rule_id = r.id "
            + "WHERE d.device_id = ? AND r.telemetry_type = ? AND r.operator = ?::rule_operator";
    static final String UPDATE_VALUE = "UPDATE rule SET value = ? WHERE id = ?";
    static final String INSERT_RULE =
            "INSERT INTO rule (id, telemetry_type, operator, value, is_strict) VALUES (?, ?, ?::rule_operator, ?, ?)";
    static final String INSERT_LINK = "INSERT INTO device_to_rule (id, rule_id, device_id) VALUES (?, ?, ?)";
    static final String RELINK = "UPDATE device_to_rule SET rule_id = ? WHERE device_id = ? AND rule_id = ?";

    private record Found(UUID id, boolean strict, long links) {}

    private final Db db;

    public PostgresNormRules(Db db) {
        this.db = db;
    }

    @Override
    public Result setNorm(UUID deviceId, short telemetryType, NormOperator operator, double value) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                Result result = apply(c, deviceId, telemetryType, operator, value);
                c.commit();
                log.info("Device {} norm: type {} {} {} ({})", deviceId, telemetryType, operator.symbol(), value, result);
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Ошибка записи нормы: " + e.getMessage(), e);
        }
    }

    private Result apply(Connection c, UUID deviceId, short type, NormOperator op, double value) throws SQLException {
        List<Found> found = new ArrayList<>();
        try (PreparedStatement ps = prepare(c, FIND, deviceId, type, op); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                found.add(new Found(rs.getObject("id", UUID.class), rs.getBoolean("is_strict"), rs.getLong("links")));
            }
        }

        if (found.isEmpty()) {
            UUID ruleId = UUID.randomUUID();
            execute(c, INSERT_RULE, ruleId, type, op, value, false);   // новое правило по умолчанию нестрогое
            execute(c, INSERT_LINK, UUID.randomUUID(), ruleId, deviceId);
            return Result.CREATED;
        }

        for (Found f : found) {
            if (f.links() <= 1) {
                execute(c, UPDATE_VALUE, value, f.id());
            } else {
                UUID copy = UUID.randomUUID();
                execute(c, INSERT_RULE, copy, type, op, value, f.strict());
                execute(c, RELINK, copy, deviceId, f.id());
            }
        }
        return Result.UPDATED;
    }

    private static void execute(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, params)) {
            ps.executeUpdate();
        }
    }

    private static PreparedStatement prepare(Connection c, String sql, Object... params) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof NormOperator op) {
                ps.setObject(i + 1, op.name(), Types.OTHER);   // в тип rule_operator
            } else {
                ps.setObject(i + 1, params[i]);
            }
        }
        return ps;
    }
}