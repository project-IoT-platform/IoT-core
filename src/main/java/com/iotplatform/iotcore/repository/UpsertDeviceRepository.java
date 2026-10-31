package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.model.DeviceState;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий устройств, который работает вместо PostgresDeviceRepository (его файл не тронут).
 * Исправляет два места, из-за которых не доходили уведомления:
 *  1) save() раньше делал только INSERT и падал на уже существующем устройстве
 *     (его вызывает TelemetryService при каждом показании) -> теперь INSERT ... ON CONFLICT DO UPDATE;
 *  2) чтение падало с NullPointerException, если last_trans (или charge_level) пустой -> теперь null допустим.
 */
public class UpsertDeviceRepository implements DeviceRepository {
    private final DatabaseUtils databaseUtils;

    public UpsertDeviceRepository(DatabaseUtils databaseUtils) {
        this.databaseUtils = databaseUtils;
    }

    @Override
    public Optional<DeviceEntity> findById(UUID id) {
        return databaseUtils.query("SELECT * FROM device WHERE id = ?",
                UpsertDeviceRepository::mapRow, id).stream().findFirst();
    }

    @Override
    public boolean existsById(UUID id) {
        return !databaseUtils.query("SELECT 1 FROM device WHERE id = ?", rs -> 1, id).isEmpty();
    }

    @Override
    public DeviceEntity save(DeviceEntity d) {
        // Instant превращаем в OffsetDateTime (UTC): этот тип драйвер PostgreSQL принимает гарантированно
        OffsetDateTime lastTrans = d.getLastDataTime() == null
                ? null : d.getLastDataTime().atOffset(ZoneOffset.UTC);
        databaseUtils.update(
                "INSERT INTO device (id, user_id, name, last_trans, charge_level, "
                        + "manufacturer, device_state, geolocation) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::state, ?) "
                        // если устройство уже есть, обновляем его поля вместо ошибки
                        + "ON CONFLICT (id) DO UPDATE SET user_id = EXCLUDED.user_id, name = EXCLUDED.name, "
                        + "last_trans = EXCLUDED.last_trans, charge_level = EXCLUDED.charge_level, "
                        + "manufacturer = EXCLUDED.manufacturer, device_state = EXCLUDED.device_state, "
                        + "geolocation = EXCLUDED.geolocation",
                d.getId(), d.getUserId(), d.getName(), lastTrans, d.getBatteryLevel(),
                d.getManufacturer(), d.getState(), d.getGeolocation());
        return d;
    }

    @Override
    public boolean deleteById(UUID id) {
        return databaseUtils.update("DELETE FROM device WHERE id = ?", id) > 0;
    }

    private static DeviceEntity mapRow(ResultSet rs) throws SQLException {
        // last_trans может быть NULL у только что зарегистрированного устройства
        OffsetDateTime lastTrans = rs.getObject("last_trans", OffsetDateTime.class);
        // charge_level тоже может быть NULL: getShort вернёт 0, поэтому проверяем wasNull()
        short charge = rs.getShort("charge_level");
        Integer battery = rs.wasNull() ? null : (int) charge;
        return new DeviceEntity(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("name"),
                lastTrans == null ? null : lastTrans.toInstant(),
                battery,
                rs.getString("manufacturer"),
                DeviceState.valueOf(rs.getString("device_state")),
                rs.getString("geolocation"));
    }
}