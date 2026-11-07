package com.iotplatform.notification.data;

import com.iotplatform.notification.db.Db;
import com.iotplatform.notification.model.Settings;

/**
 * Общие значения нормы и отклонения хранятся в БД в таблице notification_defaults (одна строка с id = 1).
 * Таблицу сервис создаёт сам. Их меняет API IoT Core (GET и PUT /api/notifications/settings).
 * Начальные значения берутся из NORM и DEVIATION и записываются только один раз.
 */
public class PostgresSettingsStore implements SettingsStore {
    private final Db db;

    public PostgresSettingsStore(Db db, double initialNorm, double initialDeviation) {
        this.db = db;
        db.update("CREATE TABLE IF NOT EXISTS notification_defaults ("
                + "id INT PRIMARY KEY, norm DOUBLE PRECISION NOT NULL, deviation DOUBLE PRECISION NOT NULL)");
        db.update("INSERT INTO notification_defaults (id, norm, deviation) VALUES (1, ?, ?) "
                + "ON CONFLICT (id) DO NOTHING", initialNorm, initialDeviation);
    }

    @Override
    public Settings defaults() {
        return db.query("SELECT norm, deviation FROM notification_defaults WHERE id = 1",
                rs -> new Settings(rs.getDouble("norm"), rs.getDouble("deviation"))).get(0);
    }

    @Override
    public void saveDefaults(Settings s) {
        db.update("UPDATE notification_defaults SET norm = ?, deviation = ? WHERE id = 1", s.norm(), s.deviation());
    }
}