package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.dto.TelemetryRequest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public class TelemetryRepository {

    public void save(Connection c, TelemetryRequest t) throws SQLException {
        String sql = """
                INSERT INTO telemetry (event_id, device_id, time, telemetry_type, value)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, t.deviceId());
            ps.setObject(3, OffsetDateTime.ofInstant(t.time(), ZoneOffset.UTC));
            ps.setShort(4, t.telemetryType());
            ps.setDouble(5, t.value());
            ps.executeUpdate();
        }
    }
}
