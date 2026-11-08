package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.exception.DatabaseException;
import com.iotplatform.iotcore.model.DeviceState;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/** Хранение устройств в PostgreSQL. */
public class PostgresDeviceRepository implements DeviceRepository {
    private final DatabaseUtils databaseUtils;

    public PostgresDeviceRepository(DatabaseUtils databaseUtils) {
        this.databaseUtils = databaseUtils;
    }

    @Override
    public Optional<DeviceEntity> findById(UUID id) {
        return databaseUtils.query("SELECT * FROM device WHERE id = ?",
                PostgresDeviceRepository::mapRow, id).stream().findFirst();
    }

    @Override
    public boolean existsById(UUID id) {
        return !databaseUtils.query("SELECT 1 FROM device WHERE id = ?",
                rs -> 1, id).isEmpty();
    }

    @Override
    public DeviceEntity save(DeviceEntity device) {
        // ИСПРАВЛЕНО 1: драйвер PostgreSQL не принимает java.time.Instant, переводим в OffsetDateTime
        OffsetDateTime lastTrans = device.getLastDataTime() == null
                ? null : device.getLastDataTime().atOffset(ZoneOffset.UTC);
        databaseUtils.update(
                "INSERT INTO device (id, user_id, name, last_trans, charge_level, "
                        + "manufacturer, device_state, geolocation) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::state, ?) "
                        // ИСПРАВЛЕНО 2: по описанию интерфейса save «создаёт, а если id уже есть, обновляет»
                        + "ON CONFLICT (id) DO UPDATE SET user_id = EXCLUDED.user_id, name = EXCLUDED.name, "
                        + "last_trans = EXCLUDED.last_trans, charge_level = EXCLUDED.charge_level, "
                        + "manufacturer = EXCLUDED.manufacturer, device_state = EXCLUDED.device_state, "
                        + "geolocation = EXCLUDED.geolocation",
                device.getId(), device.getUserId(), device.getName(), lastTrans, device.getBatteryLevel(),
                device.getManufacturer(), device.getState(), device.getGeolocation());

        return device;
    }

    @Override
    public boolean deleteById(UUID id) {
        return databaseUtils.update("DELETE FROM device WHERE id = ?", id) > 0;
    }

    private static DeviceEntity mapRow(ResultSet rs) throws DatabaseException {
        try {
            // ИСПРАВЛЕНО 3: last_trans и charge_level могут быть NULL (новое устройство), раньше был NullPointerException
            OffsetDateTime lastTrans = rs.getObject("last_trans", OffsetDateTime.class);
            short charge = rs.getShort("charge_level");
            Integer battery = rs.wasNull() ? null : Integer.valueOf(charge);
            return new DeviceEntity(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getString("name"),
                    lastTrans == null ? null : lastTrans.toInstant(),
                    battery,
                    rs.getString("manufacturer"),
                    DeviceState.valueOf(rs.getString("device_state")),
                    rs.getString("geolocation"));
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка преобразования в класс Java", e);
        }
    }

    @Override
    public int updateLastDataTime(UUID id, Instant lastTrans) { // для обновления из телеметрии
        // тоже Instant -> OffsetDateTime
        // ИСПРАВЛЕНО: меняем одно поле (раньше телеметрия перезаписывала всё устройство и могла затереть
        // параллельный PUT) и не откатываем время назад, если пришла запоздавшая старая запись
        OffsetDateTime time = lastTrans.atOffset(ZoneOffset.UTC);
        return databaseUtils.update(
                "UPDATE device SET last_trans = ? WHERE id = ? AND (last_trans IS NULL OR last_trans < ?)",
                time, id, time);
    }

    // Проверка и смена флага одним UPDATE: даже если два запроса придут одновременно,
    // поменять флаг (и отправить уведомление) сможет только один из них.
    @Override
    public boolean disableBatteryNotification(UUID id) {
        return databaseUtils.update(
                "UPDATE device SET battery_notify_allowed = FALSE WHERE id = ? AND battery_notify_allowed", id) > 0;
    }

    @Override
    public boolean enableBatteryNotification(UUID id) {
        return databaseUtils.update(
                "UPDATE device SET battery_notify_allowed = TRUE WHERE id = ? AND NOT battery_notify_allowed", id) > 0;
    }
}