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

/**
 * Реализация {@link RuleRepository} на PostgreSQL (таблицы {@code rule} и {@code device_to_rule}).
 */
public class PostgresRuleRepository implements RuleRepository {
    private final DatabaseUtils databaseUtils;

    /**
     * Создаёт репозиторий.
     *
     * @param databaseUtils доступ к БД
     */
    public PostgresRuleRepository(DatabaseUtils databaseUtils) {
        this.databaseUtils = databaseUtils;
    }

    /**
     * Вставляет новое правило.
     *
     * @param rule сохраняемое правило
     * @return то же правило
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public RuleEntity save(RuleEntity rule) {
        databaseUtils.update(
                "INSERT INTO rule (id, telemetry_type, operator, value, is_strict) "
                        + "VALUES (?, ?, ?::rule_operator, ?, ?)",
                rule.id(), rule.telemetryType(), rule.operator().name(), rule.value(), rule.strict());
        return rule;
    }

    /**
     * Ищет правило по id.
     *
     * @param id идентификатор правила
     * @return правило или пустой {@code Optional}
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public Optional<RuleEntity> findById(UUID id) {
        return databaseUtils.query("SELECT * FROM rule WHERE id = ?",
                PostgresRuleRepository::mapRow, id).stream().findFirst();
    }

    /**
     * Удаляет правило.
     *
     * @param id идентификатор правила
     * @return {@code true}, если запись удалена
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public boolean deleteById(UUID id) {
        return databaseUtils.update("DELETE FROM rule WHERE id = ?", id) > 0;
    }

    /**
     * Создаёт связь правила с устройством; если связь уже есть, ничего не делает.
     *
     * @param ruleId идентификатор правила
     * @param deviceId идентификатор устройства
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public void attachToDevice(UUID ruleId, UUID deviceId) {
        databaseUtils.update(
                "INSERT INTO device_to_rule (id, rule_id, device_id) VALUES (?, ?, ?) "
                        + "ON CONFLICT (rule_id, device_id) DO NOTHING",
                UUID.randomUUID(), ruleId, deviceId);
    }

    /**
     * Удаляет связь правила с устройством.
     *
     * @param ruleId идентификатор правила
     * @param deviceId идентификатор устройства
     * @return {@code true}, если связь существовала
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public boolean detachFromDevice(UUID ruleId, UUID deviceId) {
        return databaseUtils.update(
                "DELETE FROM device_to_rule WHERE rule_id = ? AND device_id = ?",
                ruleId, deviceId) > 0;
    }

    /**
     * Возвращает все правила, привязанные к устройству.
     *
     * @param deviceId идентификатор устройства
     * @return список правил (может быть пустым)
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public List<RuleEntity> findByDeviceId(UUID deviceId) {
        return databaseUtils.query(
                "SELECT r.id, r.telemetry_type, r.operator, r.value, r.is_strict "
                        + "FROM rule r JOIN device_to_rule dr ON dr.rule_id = r.id WHERE dr.device_id = ?",
                PostgresRuleRepository::mapRow, deviceId);
    }

    @Override
    public List<RuleEntity> findForEvaluation(UUID deviceId) {
        return databaseUtils.query("""
                SELECT r.id, r.telemetry_type, r.operator, r.value, r.is_strict
                FROM rule r JOIN device_to_rule dr ON dr.rule_id = r.id WHERE dr.device_id = ?
                UNION ALL
                SELECT id, telemetry_type, operator, value, FALSE
                FROM device_notification_rule WHERE device_id = ?
                """, PostgresRuleRepository::mapRow, deviceId, deviceId);
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