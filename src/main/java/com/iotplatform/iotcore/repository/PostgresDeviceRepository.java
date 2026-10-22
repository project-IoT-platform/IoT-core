package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.db.Database;
import com.iotplatform.iotcore.entity.DeviceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PostgresDeviceRepository implements DeviceRepository {
    private final Database database;

    public PostgresDeviceRepository(Database database) {
        this.database = database;
    }

    @Override
    public Optional<DeviceEntity> findById(UUID id) {
        // TODO Лёша: достать данные и таблицы и таблицы devise где device_id = ...
        // если строки нет, вернуть Optional.empty()
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public boolean existsById(UUID id) {
        // TODO Лёша: есть ли в таблице device строка со начением device_id = ?
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public DeviceEntity save(DeviceEntity device) {
        // TODO Лёша: создать устройство, а если такой device_id уже есть, обновить
        // Вернуть то, что сохранили.
        throw new UnsupportedOperationException("TODO Лёша");
    }

    @Override
    public boolean deleteById(UUID id) {
        // TODO Лёша: удалить устройство с device_id = ?
        // вернуть true, если удалилась хотя бы одна строка
        throw new UnsupportedOperationException("TODO Лёша");
    }
}
