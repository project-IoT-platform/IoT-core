package com.iotplatform.notification.data;

import com.iotplatform.notification.db.Db;
import com.iotplatform.notification.model.Rule;

import java.util.Optional;
import java.util.UUID;

/**
 * Читает и записывает правила устройств в таблице rute.
 * ВАЖНО: названия таблицы и колонок ниже взяты по описанию (структуру таблицы я не видел).
 * Если у вас они другие, поменяйте только эти пять констант.
 */
public class PostgresRuleStore implements RuleStore {
    private static final String TABLE = "rute";
    private static final String DEVICE_COLUMN = "device_id";        // id устройства
    private static final String BATTERY_COLUMN = "battery_limit";   // минимальный заряд батареи, %
    private static final String NORM_COLUMN = "norm";               // нормальное значение температуры
    private static final String DEVIATION_COLUMN = "deviation";     // допустимое отклонение

    private final Db db;

    public PostgresRuleStore(Db db) {
        this.db = db;
    }

    @Override
    public Optional<Rule> find(UUID deviceId) {
        return db.query("SELECT " + BATTERY_COLUMN + ", " + NORM_COLUMN + ", " + DEVIATION_COLUMN
                        + " FROM " + TABLE + " WHERE " + DEVICE_COLUMN + " = ?",
                rs -> new Rule(
                        rs.getObject(BATTERY_COLUMN, Integer.class),
                        rs.getObject(NORM_COLUMN, Double.class),
                        rs.getObject(DEVIATION_COLUMN, Double.class)),
                deviceId).stream().findFirst();
    }

    @Override
    public void save(UUID deviceId, Rule rule) {
        int updated = db.update("UPDATE " + TABLE + " SET " + BATTERY_COLUMN + " = ?, " + NORM_COLUMN + " = ?, "
                        + DEVIATION_COLUMN + " = ? WHERE " + DEVICE_COLUMN + " = ?",
                rule.batteryLimit(), rule.norm(), rule.deviation(), deviceId);
        if (updated == 0) {   // строки у устройства ещё нет: создаём
            db.update("INSERT INTO " + TABLE + " (" + DEVICE_COLUMN + ", " + BATTERY_COLUMN + ", " + NORM_COLUMN
                            + ", " + DEVIATION_COLUMN + ") VALUES (?, ?, ?, ?)",
                    deviceId, rule.batteryLimit(), rule.norm(), rule.deviation());
        }
    }
}