package com.iotplatform.notification.data;

import com.iotplatform.notification.db.Db;
import com.iotplatform.notification.model.Device;
import com.iotplatform.notification.model.User;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/** Читает устройства, пользователей и телеметрию из таблиц device, users, telemetry. Ничего не записывает. */
public class PostgresPlatformData implements PlatformData {
    private final Db db;

    public PostgresPlatformData(Db db) {
        this.db = db;
    }

    @Override
    public Optional<Device> findDevice(UUID id) {
        return db.query("SELECT id, user_id, name, manufacturer, charge_level, device_state, geolocation, last_trans "
                        + "FROM device WHERE id = ?",
                rs -> {
                    short level = rs.getShort("charge_level");
                    Integer battery = rs.wasNull() ? null : Integer.valueOf(level);   // NULL допустим
                    OffsetDateTime last = rs.getObject("last_trans", OffsetDateTime.class);
                    return new Device(
                            rs.getObject("id", UUID.class),
                            rs.getObject("user_id", UUID.class),
                            rs.getString("name"),
                            rs.getString("manufacturer"),
                            battery,
                            "ON".equals(rs.getString("device_state")),
                            rs.getString("geolocation"),
                            last == null ? null : last.toInstant());
                }, id).stream().findFirst();
    }

    @Override
    public Optional<User> findUser(UUID id) {
        return db.query("SELECT id, username, email, user_role FROM users WHERE id = ?",
                rs -> new User(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("email"),
                        "ADMIN".equalsIgnoreCase(rs.getString("user_role"))),
                id).stream().findFirst();
    }

    @Override
    public OptionalDouble averageSince(UUID deviceId, Instant since) {
        Double average = db.query(
                "SELECT AVG(value) FROM telemetry WHERE device_id = ? AND recorded_at >= ?",
                rs -> rs.getObject(1, Double.class),   // для пустой выборки NULL, а не 0
                deviceId, since).get(0);
        return average == null ? OptionalDouble.empty() : OptionalDouble.of(average);
    }
}