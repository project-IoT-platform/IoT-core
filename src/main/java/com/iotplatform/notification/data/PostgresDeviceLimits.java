package com.iotplatform.notification.data;

import com.iotplatform.notification.db.Db;
import com.iotplatform.notification.model.TelemetryLimit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Лимиты устройства в PostgreSQL: заряд в таблице rute, границы телеметрии в таблицах rule и device_to_rule. */
public class PostgresDeviceLimits implements DeviceLimits {
    private record BoundRow(short type, String operator, double value) {}
    private record RuleRef(UUID id, long links) {}

    private final Db db;

    public PostgresDeviceLimits(Db db) {
        this.db = db;
    }

    @Override
    public Optional<Integer> batteryLowerBound(UUID deviceId) {
        List<Optional<Integer>> rows = db.query(
                "SELECT battery_limit FROM rute WHERE device_id = ?",
                rs -> Optional.ofNullable(rs.getObject("battery_limit", Integer.class)),
                deviceId);
        return rows.isEmpty() ? Optional.empty() : rows.get(0);
    }

    @Override
    public List<TelemetryLimit> telemetryLimits(UUID deviceId) {
        List<BoundRow> rows = db.query(
                "SELECT r.telemetry_type AS t, r.operator::text AS op, r.value AS v "
                        + "FROM rule r JOIN device_to_rule d ON d.rule_id = r.id "
                        + "WHERE d.device_id = ? ORDER BY r.telemetry_type",
                rs -> new BoundRow(rs.getShort("t"), rs.getString("op"), rs.getDouble("v")),
                deviceId);

        Map<Short, Double[]> byType = new TreeMap<>();
        for (BoundRow row : rows) {
            Double[] bounds = byType.computeIfAbsent(row.type(), k -> new Double[2]);
            switch (row.operator()) {
                case "GE", "GT" -> bounds[0] = row.value();
                case "LE", "LT" -> bounds[1] = row.value();
                default -> { }
            }
        }
        List<TelemetryLimit> out = new ArrayList<>();
        for (Map.Entry<Short, Double[]> e : byType.entrySet()) {
            if (e.getValue()[0] != null || e.getValue()[1] != null) {
                out.add(new TelemetryLimit(e.getKey(), e.getValue()[0], e.getValue()[1]));
            }
        }
        return out;
    }

    /** Сохраняет нижнюю границу заряда в rute (создаёт строку, если её нет). */
    public void saveBatteryLowerBound(UUID deviceId, int value) {
        db.update("INSERT INTO rute (device_id, battery_limit) VALUES (?, ?) "
                        + "ON CONFLICT (device_id) DO UPDATE SET battery_limit = EXCLUDED.battery_limit",
                deviceId, value);
    }

    /** Сохраняет одну границу телеметрии устройства в rule: lower = true нижняя (GE), иначе верхняя (LE). */
    public void saveTelemetryBound(UUID deviceId, short type, boolean lower, double value) {
        String ops = lower ? "('GE','GT')" : "('LE','LT')";
        List<RuleRef> found = db.query(
                "SELECT r.id AS id, (SELECT COUNT(*) FROM device_to_rule x WHERE x.rule_id = r.id) AS links "
                        + "FROM rule r JOIN device_to_rule d ON d.rule_id = r.id "
                        + "WHERE d.device_id = ? AND r.telemetry_type = ? AND r.operator::text IN " + ops + " LIMIT 1",
                rs -> new RuleRef(rs.getObject("id", UUID.class), rs.getLong("links")),
                deviceId, type);

        if (!found.isEmpty()) {
            RuleRef ref = found.get(0);
            if (ref.links() == 1) {   // правило только этого устройства: меняем значение
                db.update("UPDATE rule SET value = ? WHERE id = ?", value, ref.id());
                return;
            }
            // правило общее с другими устройствами: отвязываем это устройство и создаём своё
            db.update("DELETE FROM device_to_rule WHERE rule_id = ? AND device_id = ?", ref.id(), deviceId);
        }

        UUID ruleId = UUID.randomUUID();
        db.update("INSERT INTO rule (id, telemetry_type, operator, value, is_strict) "
                        + "VALUES (?, ?, ?::rule_operator, ?, FALSE)",
                ruleId, type, lower ? "GE" : "LE", value);
        db.update("INSERT INTO device_to_rule (id, rule_id, device_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), ruleId, deviceId);
    }
}