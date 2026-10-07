package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.dto.DeviceRequest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public class DeviceRepository {

    public boolean exists(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM device WHERE device_id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void upsert(Connection c, DeviceRequest d) throws SQLException {
        String sql = """
                INSERT INTO device (device_id, name, manufacturer, battery_level, state, geolocation)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (device_id) DO UPDATE SET
                    name = EXCLUDED.name,
                    manufacturer = EXCLUDED.manufacturer,
                    battery_level = EXCLUDED.battery_level,
                    state = EXCLUDED.state,
                    geolocation = EXCLUDED.geolocation
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, d.deviceId());
            ps.setString(2, d.name());
            ps.setString(3, d.manufacturer());
            ps.setObject(4, d.batteryLevel(), Types.SMALLINT);
            ps.setString(5, d.state().name());
            ps.setString(6, d.geolocation());
            ps.executeUpdate();
        }
    }

    public void updateLastDataTime(Connection c, UUID id, Instant time) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE device SET last_data_time = ? WHERE device_id = ?")) {
            ps.setObject(1, OffsetDateTime.ofInstant(time, ZoneOffset.UTC));
            ps.setObject(2, id);
            ps.executeUpdate();
        }
    }
}
