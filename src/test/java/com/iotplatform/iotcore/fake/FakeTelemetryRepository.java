package com.iotplatform.iotcore.fake;

import com.iotplatform.iotcore.entity.TelemetryEntity;
import com.iotplatform.iotcore.repository.TelemetryRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

/** Репозиторий телеметрии в памяти. */
public class FakeTelemetryRepository implements TelemetryRepository {
    public final List<TelemetryEntity> rows = new ArrayList<>();

    @Override
    public List<TelemetryEntity> latestByDevice(UUID deviceId) {
        var latest = new TreeMap<Short, TelemetryEntity>();
        rows.stream().filter(row -> deviceId.equals(row.deviceId()))
                .sorted(Comparator.comparing(TelemetryEntity::time).thenComparing(TelemetryEntity::id))
                .forEach(row -> latest.put(row.telemetryType(), row));
        return List.copyOf(latest.values());
    }

    @Override
    public List<TelemetryEntity> recentByType(UUID deviceId, short type, int limit) {
        return rows.stream().filter(row -> deviceId.equals(row.deviceId()) && row.telemetryType() == type)
                .sorted(Comparator.comparing(TelemetryEntity::time).thenComparing(TelemetryEntity::id).reversed())
                .limit(limit).toList().reversed();
    }

    @Override
    public Average averageByTypeSince(UUID deviceId, short type, Instant since) {
        var stats = rows.stream()
                .filter(row -> deviceId.equals(row.deviceId()) && row.telemetryType() == type && !row.time().isBefore(since))
                .mapToDouble(TelemetryEntity::value).summaryStatistics();
        return new Average(stats.getCount() == 0 ? null : stats.getAverage(), stats.getCount());
    }

    @Override
    public void save(TelemetryEntity telemetry) {
        rows.add(telemetry);
    }

    @Override
    public OptionalDouble averageValueSince(UUID deviceId, Instant since) {
        return rows.stream()
                .filter(t -> t.deviceId().equals(deviceId) && !t.time().isBefore(since))
                .mapToDouble(TelemetryEntity::value)
                .average();
    }
}