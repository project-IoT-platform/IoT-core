package com.iotplatform.iotcore.fake;

import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.repository.DeviceRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Репозиторий устройств в памяти: ведёт себя как Postgres-версия, но без базы. */
public class FakeDeviceRepository implements DeviceRepository {
    public final Map<UUID, DeviceEntity> rows = new HashMap<>();
    public int saveCalls = 0;

    @Override
    public Optional<DeviceEntity> findById(UUID id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public boolean existsById(UUID id) {
        return rows.containsKey(id);
    }

    @Override
    public DeviceEntity save(DeviceEntity device) {
        saveCalls++;
        rows.put(device.getId(), device);
        return device;
    }

    @Override
    public boolean deleteById(UUID id) {
        return rows.remove(id) != null;
    }

    @Override
    public int updateLastDataTime(UUID id, Instant lastDataTime) {
        DeviceEntity d = rows.get(id);
        if (d == null) return 0;
        if (d.getLastDataTime() != null && !d.getLastDataTime().isBefore(lastDataTime)) return 0;
        d.setLastDataTime(lastDataTime);
        return 1;
    }

    /** id устройств, у которых battery_notify_allowed = false (по умолчанию у всех true). */
    public final java.util.Set<UUID> batteryNotifyDisabled = new java.util.HashSet<>();

    @Override
    public boolean disableBatteryNotification(UUID id) {
        return rows.containsKey(id) && batteryNotifyDisabled.add(id);
    }

    @Override
    public boolean enableBatteryNotification(UUID id) {
        return batteryNotifyDisabled.remove(id);
    }
}