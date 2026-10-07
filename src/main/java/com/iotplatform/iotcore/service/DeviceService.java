package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.db.Database;
import com.iotplatform.iotcore.dto.DeviceRequest;
import com.iotplatform.iotcore.repository.DeviceRepository;

import java.sql.Connection;
import java.sql.SQLException;

public class DeviceService {
    private final Database db;
    private final DeviceRepository devices;

    public DeviceService(Database db, DeviceRepository devices) {
        this.db = db;
        this.devices = devices;
    }

    public void register(DeviceRequest request) {
        try (Connection c = db.connection()) {
            devices.upsert(c, request);
        } catch (SQLException e) {
            throw new IllegalStateException("Ошибка БД", e);
        }
    }
}
