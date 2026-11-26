package com.iotplatform.iotcore.testsupport;

import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.model.DeviceState;
import com.iotplatform.iotcore.repository.DeviceRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Фейк вместо БД: устройства в памяти + счётчик вызовов updateLastDataTime. */
public class InMemoryDeviceRepository implements DeviceRepository {
    private final Map<UUID, DeviceEntity> devices = new HashMap<>();
    private int updateLastDataTimeCalls;
    private final Set<UUID> batteryNotifyDisabled = new HashSet<>();

    /** Быстро положить в «БД» устройство без владельца и без последних данных. */
    public DeviceEntity addDevice(UUID id) {
        return save(new DeviceEntity(id, null, "sensor", null, 80, "ACME", DeviceState.ON, null));
    }

    public int updateLastDataTimeCalls() {
        return updateLastDataTimeCalls;
    }

    @Override
    public List<DeviceEntity> findByOwner(UUID owner, String search, Integer limit, int offset) {
        return devices.values().stream()
                .filter(device -> owner.equals(device.getUserId()))
                .filter(device -> device.getName().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing((DeviceEntity device) -> device.getName().toLowerCase(Locale.ROOT))
                        .thenComparing(DeviceEntity::getId))
                .skip(offset)
                .limit(limit == null ? Long.MAX_VALUE : limit)
                .toList();
    }

    @Override
    public Optional<DeviceEntity> findById(UUID id) {
        return Optional.ofNullable(devices.get(id));
    }

    @Override
    public boolean existsById(UUID id) {
        return devices.containsKey(id);
    }

    @Override
    public DeviceEntity save(DeviceEntity device) {
        devices.put(device.getId(), device);
        return device;
    }

    @Override
    public boolean deleteById(UUID id) {
        return devices.remove(id) != null;
    }

    @Override
    public int updateLastDataTime(UUID id, Instant lastTrans) {
        updateLastDataTimeCalls++;
        DeviceEntity device = devices.get(id);
        if (device == null) return 0;
        device.setLastDataTime(lastTrans);
        return 1;
    }

    @Override
    public boolean disableBatteryNotification(UUID id) {
        return devices.containsKey(id) && batteryNotifyDisabled.add(id);
    }

    @Override
    public boolean enableBatteryNotification(UUID id) {
        return batteryNotifyDisabled.remove(id);
    }
}