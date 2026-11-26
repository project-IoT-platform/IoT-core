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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Реализация {@link DeviceRepository} на PostgreSQL (таблица {@code device}).
 */
public class PostgresDeviceRepository implements DeviceRepository {
    private final DatabaseUtils databaseUtils;

    @Override
    public List<DeviceEntity> findByOwner(UUID owner, String search, Integer limit, int offset) {
        return databaseUtils.query("""
                SELECT * FROM device
                WHERE user_id = ? AND strpos(lower(name), lower(?)) > 0
                ORDER BY lower(name), id
                LIMIT ? OFFSET ?
                """, PostgresDeviceRepository::mapRow, owner, search, limit, offset);
    }


    /**
     * Создаёт репозиторий.
     *
     * @param databaseUtils доступ к БД
     */
    public PostgresDeviceRepository(DatabaseUtils databaseUtils) {
        this.databaseUtils = databaseUtils;
    }

    /**
     * Ищет устройство по id.
     *
     * @param id идентификатор устройства
     * @return устройство или пустой {@code Optional}
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public Optional<DeviceEntity> findById(UUID id) {
        return databaseUtils.query("SELECT * FROM device WHERE id = ?",
                PostgresDeviceRepository::mapRow, id).stream().findFirst();
    }

    /**
     * Проверяет, есть ли устройство с таким id.
     *
     * @param id идентификатор устройства
     * @return {@code true}, если устройство существует
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public boolean existsById(UUID id) {
        return !databaseUtils.query("SELECT 1 FROM device WHERE id = ?",
                rs -> 1, id).isEmpty();

    }

    /**
     * Вставляет устройство, а при совпадении id обновляет данные устройства, сохраняя время последнего показания ({@code INSERT ... ON CONFLICT DO UPDATE}).
     *
     * @param device сохраняемое устройство
     * @return то же устройство
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public DeviceEntity save(DeviceEntity device) {
        OffsetDateTime lastTrans = device.getLastDataTime() == null
                ? null : device.getLastDataTime().atOffset(ZoneOffset.UTC);
        databaseUtils.update(
                "INSERT INTO device (id, user_id, name, last_trans, charge_level, "
                        + "manufacturer, device_state, geolocation) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::state, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET "
                        + "user_id = EXCLUDED.user_id, name = EXCLUDED.name, "
                        + "charge_level = EXCLUDED.charge_level, "
                        + "manufacturer = EXCLUDED.manufacturer, device_state = EXCLUDED.device_state, "
                        + "geolocation = EXCLUDED.geolocation",
                device.getId(), device.getUserId(), device.getName(), lastTrans,
                device.getBatteryLevel(), device.getManufacturer(), device.getState(), device.getGeolocation());

        return device;
    }

    /**
     * Удаляет устройство.
     *
     * @param id идентификатор устройства
     * @return {@code true}, если запись удалена
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public boolean deleteById(UUID id) {
        return databaseUtils.update("DELETE FROM device WHERE id = ?", id) > 0;
    }

    private static DeviceEntity mapRow(ResultSet rs) throws DatabaseException { // преобразование результата запроса к бд в Java-объект
        try {
            OffsetDateTime lastTrans = rs.getObject("last_trans", OffsetDateTime.class);
            Instant lastDataTime = lastTrans == null ? null : lastTrans.toInstant();

            short charge = rs.getShort("charge_level");
            Integer batteryLevel = rs.wasNull() ? null : (int) charge;

            return new DeviceEntity(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getString("name"),
                    lastDataTime,
                    batteryLevel,
                    rs.getString("manufacturer"),
                    DeviceState.valueOf(rs.getString("device_state")),
                    rs.getString("geolocation"));
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка преобразования в класс Java", e);
        }
    }
    /**
     * Обновляет время последних данных, только если новое время позже сохранённого (или сохранённого ещё нет),
     * поэтому запоздавшее показание не отодвигает время назад.
     *
     * @param id идентификатор устройства
     * @param lastTrans время показания
     * @return количество обновлённых строк
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public int updateLastDataTime(UUID id, Instant lastTrans) { //для обновления из телеметрии
        OffsetDateTime time = lastTrans.atOffset(ZoneOffset.UTC);
        return databaseUtils.update(
                "UPDATE device SET last_trans = ? WHERE id = ? AND (last_trans IS NULL OR last_trans < ?)",
                time, id, time);
    }

    // Проверка и смена флага одним UPDATE: даже если два запроса придут одновременно,
    // поменять флаг (и отправить уведомление) сможет только один из них.
    /**
     * Сбрасывает флаг {@code battery_notify_allowed} одним {@code UPDATE} с проверкой, поэтому при одновременных запросах
     * флаг сбросит только один из них.
     *
     * @param id идентификатор устройства
     * @return {@code true}, если именно этот вызов сбросил флаг
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public boolean disableBatteryNotification(UUID id) {
        return databaseUtils.update(
                "UPDATE device SET battery_notify_allowed = FALSE WHERE id = ? AND battery_notify_allowed", id) > 0;
    }

    /**
     * Включает флаг {@code battery_notify_allowed} одним {@code UPDATE}, только если он был выключен.
     *
     * @param id идентификатор устройства
     * @return {@code true}, если именно этот вызов включил флаг
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    @Override
    public boolean enableBatteryNotification(UUID id) {
        return databaseUtils.update(
                "UPDATE device SET battery_notify_allowed = TRUE WHERE id = ? AND NOT battery_notify_allowed", id) > 0;
    }
}