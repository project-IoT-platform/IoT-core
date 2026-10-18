package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.exception.DatabaseException;
import com.iotplatform.iotcore.model.DeviceState;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public List<DeviceEntity> findAll() {
        // TODO Лёша: дстать данные из таблицы device
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public boolean existsById(UUID id) {
        return !databaseUtils.query("SELECT 1 FROM device WHERE id = ?",
                rs -> 1, id).isEmpty();

    }

    @Override
    public DeviceEntity save(DeviceEntity device) {
        databaseUtils.update(
                "INSERT INTO device (id, user_id, name, last_trans, charge_level, "
                        + "manufacturer, device_state, geolocation) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::state, ?)",
                device.getId(), device.getUserId(), device.getName(), device.getLastDataTime(), device.getBatteryLevel(),
                device.getManufacturer(), device.getState(), device.getGeolocation());

        return device;
    }

    @Override
    public boolean deleteById(UUID id) {
        return databaseUtils.update("DELETE FROM device WHERE id = ?", id) > 0;
    }

    private static DeviceEntity mapRow(ResultSet rs) throws DatabaseException { // преобразование результата запроса к бд в Java-объект
        try {
            return new DeviceEntity(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getString("name"),
                    rs.getObject("last_trans", OffsetDateTime.class).toInstant(),
                    (int) rs.getShort("charge_level"),
                    rs.getString("manufacturer"),
                    DeviceState.valueOf(rs.getString("device_state")),
                    rs.getString("geolocation"));
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка преобразования в класс Java", e);
        }
    }

    public int updateLastDataTime(UUID id, Instant lastTrans) { //для обновления из телеметрии
        return databaseUtils.update("UPDATE device SET last_trans = ? WHERE id = ?", lastTrans, id);
    }
}
