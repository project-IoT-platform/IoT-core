package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.exception.DatabaseException;
import com.iotplatform.iotcore.model.RuleOperator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PostgresRuleRepository implements RuleRepository {
    private final DatabaseUtils databaseUtils;

    public PostgresRuleRepository(DatabaseUtils databaseUtils) {
        this.databaseUtils = databaseUtils;
    }

    @Override
    public RuleEntity save(RuleEntity rule) {
        databaseUtils.update(
                "INSERT INTO rule (id, telemetry_type, operator, value, is_strict) "
                        + "VALUES (?, ?, ?::rule_operator, ?, ?)",
                rule.id(), rule.telemetryType(), rule.operator().name(), rule.value(), rule.strict());
        return rule;
    }

    @Override
    public Optional<RuleEntity> findById(UUID id) {
        return databaseUtils.query("SELECT * FROM rule WHERE id = ?",
                PostgresRuleRepository::mapRow, id).stream().findFirst();
    }

    @Override
    public boolean deleteById(UUID id) {
        return databaseUtils.update("DELETE FROM rule WHERE id = ?", id) > 0;
    }

    @Override
    public void attachToDevice(UUID ruleId, UUID deviceId) {
        databaseUtils.update(
                "INSERT INTO device_to_rule (id, rule_id, device_id) VALUES (?, ?, ?) "
                        + "ON CONFLICT (rule_id, device_id) DO NOTHING",
                UUID.randomUUID(), ruleId, deviceId);
    }

    @Override
    public boolean detachFromDevice(UUID ruleId, UUID deviceId) {
        return databaseUtils.update(
                "DELETE FROM device_to_rule WHERE rule_id = ? AND device_id = ?",
                ruleId, deviceId) > 0;
    }

    @Override
    public List<RuleEntity> findByDeviceId(UUID deviceId) {
        return databaseUtils.query(
                "SELECT r.* FROM rule r JOIN device_to_rule dr ON dr.rule_id = r.id WHERE dr.device_id = ?",
                PostgresRuleRepository::mapRow, deviceId);
    }

    private static RuleEntity mapRow(ResultSet rs) throws DatabaseException {
        try {
            return new RuleEntity(
                    rs.getObject("id", UUID.class),
                    rs.getShort("telemetry_type"),
                    RuleOperator.valueOf(rs.getString("operator")),
                    rs.getDouble("value"),
                    rs.getBoolean("is_strict"));
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка преобразования в класс Java", e);
        }
    }
}
