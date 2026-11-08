package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.model.RuleOperator;

import java.util.List;
import java.util.UUID;

/** Чтение правил из таблиц rule и device_to_rule. */
public class PostgresRuleRepository implements RuleRepository {
    private final DatabaseUtils databaseUtils;

    public PostgresRuleRepository(DatabaseUtils databaseUtils) {
        this.databaseUtils = databaseUtils;
    }

    @Override
    public List<RuleEntity> findByDeviceId(UUID deviceId) {
        return databaseUtils.query(
                "SELECT r.id, r.telemetry_type, r.operator, r.value, r.is_strict "
                        + "FROM rule r JOIN device_to_rule d ON d.rule_id = r.id "
                        + "WHERE d.device_id = ?",
                rs -> new RuleEntity(
                        rs.getObject("id", UUID.class),
                        rs.getShort("telemetry_type"),
                        RuleOperator.valueOf(rs.getString("operator")),
                        rs.getDouble("value"),
                        rs.getBoolean("is_strict")),
                deviceId);
    }
}