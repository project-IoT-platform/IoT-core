package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.dto.DeviceLimitsDto;
import com.iotplatform.iotcore.dto.DeviceLimitsDto.TelemetryLimit;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.exception.DatabaseException;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.RuleOperator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PostgresDeviceLimitsRepository {
    private static final String UPSERT_RULE = """
            INSERT INTO device_notification_rule (id, device_id, telemetry_type, operator, value)
            VALUES (?, ?, ?, ?::rule_operator, ?)
            ON CONFLICT (device_id, telemetry_type, operator) DO UPDATE SET value = EXCLUDED.value
            RETURNING id
            """;
    private final DatabaseUtils database;

    public PostgresDeviceLimitsRepository(DatabaseUtils database) {
        this.database = database;
    }

    public DeviceLimitsDto find(UUID deviceId) {
        return withDevice(deviceId, (conn, battery) -> read(conn, deviceId, battery));
    }

    public DeviceLimitsDto replace(UUID deviceId, DeviceLimitsDto limits) {
        limits.validate();
        return withDevice(deviceId, (conn, battery) -> {
            database.update(conn, "UPDATE device SET battery_limit = ? WHERE id = ?",
                    limits.batteryLimit(), deviceId);
            if (limits.telemetryLimits() != null) {
                replaceTelemetryLimits(conn, deviceId, limits.telemetryLimits());
            }
            return read(conn, deviceId, limits.batteryLimit());
        });
    }

    public RuleEntity setNorm(UUID deviceId, short type, RuleOperator operator, double value) {
        if (!Double.isFinite(value)) {
            throw new ValidationException(Map.of("value", "должно быть конечным числом"));
        }
        return withDevice(deviceId, (conn, battery) -> {
            RuleOperator alternate = switch (operator) {
                case GE -> RuleOperator.GT;
                case GT -> RuleOperator.GE;
                case LE -> RuleOperator.LT;
                case LT -> RuleOperator.LE;
                case EQ, NE -> null;
            };
            if (alternate != null) {
                database.update(conn, """
                        DELETE FROM device_notification_rule
                        WHERE device_id = ? AND telemetry_type = ? AND operator = ?::rule_operator
                        """, deviceId, type, alternate);
            }
            RuleEntity rule = upsert(conn, deviceId, type, operator, value);
            read(conn, deviceId, battery).validate();
            return rule;
        });
    }

    private void replaceTelemetryLimits(Connection conn, UUID deviceId, List<TelemetryLimit> limits)
            throws SQLException {
        List<ExistingBound> previous = database.query(conn, """
                SELECT id, telemetry_type, operator FROM device_notification_rule
                WHERE device_id = ? AND operator IN ('GE', 'GT', 'LE', 'LT')
                """, rs -> new ExistingBound(rs.getObject("id", UUID.class),
                rs.getShort("telemetry_type"), RuleOperator.valueOf(rs.getString("operator"))), deviceId);
        var lower = new HashMap<Short, RuleOperator>();
        var upper = new HashMap<Short, RuleOperator>();
        for (ExistingBound bound : previous) {
            var byType = bound.operator() == RuleOperator.GE || bound.operator() == RuleOperator.GT ? lower : upper;
            byType.put(bound.type(), bound.operator());
        }
        var retained = new HashSet<UUID>();
        for (TelemetryLimit limit : limits) {
            short type = limit.telemetryType();
            if (limit.lowerBound() != null) {
                retained.add(upsert(conn, deviceId, type, lower.getOrDefault(type, RuleOperator.GE),
                        limit.lowerBound()).id());
            }
            if (limit.upperBound() != null) {
                retained.add(upsert(conn, deviceId, type, upper.getOrDefault(type, RuleOperator.LE),
                        limit.upperBound()).id());
            }
        }
        for (ExistingBound bound : previous) {
            if (!retained.contains(bound.id())) {
                database.update(conn, "DELETE FROM device_notification_rule WHERE id = ?", bound.id());
            }
        }
    }

    private record ExistingBound(UUID id, short type, RuleOperator operator) {}

    private RuleEntity upsert(Connection conn, UUID deviceId, short type, RuleOperator operator, double value)
            throws SQLException {
        UUID id = database.query(conn, UPSERT_RULE, rs -> rs.getObject("id", UUID.class),
                UUID.randomUUID(), deviceId, type, operator, value).getFirst();
        return new RuleEntity(id, type, operator, value, false);
    }

    private DeviceLimitsDto read(Connection conn, UUID deviceId, Integer battery) throws SQLException {
        List<TelemetryLimit> limits = database.query(conn, """
                SELECT telemetry_type,
                    MAX(value) FILTER (WHERE operator IN ('GE', 'GT')) AS lower_bound,
                    MIN(value) FILTER (WHERE operator IN ('LE', 'LT')) AS upper_bound
                FROM device_notification_rule
                WHERE device_id = ? AND operator IN ('GE', 'GT', 'LE', 'LT')
                GROUP BY telemetry_type ORDER BY telemetry_type
                """, rs -> new TelemetryLimit(rs.getShort("telemetry_type"),
                rs.getObject("lower_bound", Double.class), rs.getObject("upper_bound", Double.class)), deviceId);
        return new DeviceLimitsDto(battery, limits);
    }

    private <T> T withDevice(UUID deviceId, DeviceOperation<T> operation) {
        try (Connection conn = database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<Integer> rows = database.query(conn, "SELECT battery_limit FROM device WHERE id = ? FOR UPDATE",
                        rs -> rs.getObject("battery_limit", Integer.class), deviceId);
                if (rows.isEmpty()) throw new DeviceNotFoundException(deviceId);
                T result = operation.run(conn, rows.getFirst());
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (SQLException failure) {
            throw new DatabaseException("Ошибка работы с лимитами устройства", failure);
        }
    }

    @FunctionalInterface
    private interface DeviceOperation<T> {
        T run(Connection conn, Integer battery) throws SQLException;
    }
}
