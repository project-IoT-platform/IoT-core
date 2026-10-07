package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.db.Database;
import com.iotplatform.iotcore.dto.TelemetryRequest;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.TelemetryRepository;

import java.sql.Connection;
import java.sql.SQLException;

public class TelemetryService {
    private final Database db;
    private final DeviceRepository devices;
    private final TelemetryRepository telemetry;

    public TelemetryService(Database db, DeviceRepository devices, TelemetryRepository telemetry) {
        this.db = db;
        this.devices = devices;
        this.telemetry = telemetry;
    }

    public void process(TelemetryRequest request) {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false); // начинаем транзакцию
            try {
                if (!devices.exists(c, request.deviceId())) {
                    throw new DeviceNotFoundException(request.deviceId());
                }
                telemetry.save(c, request);
                devices.updateLastDataTime(c, request.deviceId(), request.time());
                c.commit(); // сохраняем всё вместе
            } catch (Exception e) {
                c.rollback(); // откатываем всё
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Ошибка БД", e);
        }
    }
}
