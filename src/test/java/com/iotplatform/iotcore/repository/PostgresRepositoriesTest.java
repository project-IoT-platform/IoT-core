package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.DeviceEntity;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.entity.TelemetryEntity;
import com.iotplatform.iotcore.entity.UserEntity;
import com.iotplatform.iotcore.fake.RecordingDatabaseUtils;
import com.iotplatform.iotcore.model.DeviceState;
import com.iotplatform.iotcore.model.RuleOperator;
import com.iotplatform.iotcore.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты репозиториев без настоящей базы: проверяем, какой SQL и с какими
 * параметрами уходит в DatabaseUtils, и как строки из базы превращаются в сущности.
 * Сам SQL против PostgreSQL здесь не выполняется: для этого нужны интеграционные тесты.
 */
class PostgresRepositoriesTest {
    private RecordingDatabaseUtils db;

    @BeforeEach
    void setUp() {
        db = new RecordingDatabaseUtils();
    }

    // ---------- устройства ----------

    @Test
    void deviceSaveIsUpsertAndConvertsInstant() {
        PostgresDeviceRepository repo = new PostgresDeviceRepository(db);
        UUID id = UUID.randomUUID();
        Instant t = Instant.parse("2026-11-06T12:00:00Z");

        repo.save(new DeviceEntity(id, null, "n", t, 50, "m", DeviceState.ON, "geo"));

        RecordingDatabaseUtils.Call call = db.lastUpdate();
        assertTrue(call.sql().contains("ON CONFLICT (id) DO UPDATE"));
        assertEquals(id, call.params().get(0));
        // драйвер PostgreSQL не умеет Instant, поэтому должен уйти OffsetDateTime
        assertEquals(OffsetDateTime.of(2026, 11, 6, 12, 0, 0, 0, ZoneOffset.UTC), call.params().get(3));
        assertEquals(DeviceState.ON, call.params().get(6));
    }

    @Test
    void deviceSaveWithoutLastDataTimePassesNull() {
        new PostgresDeviceRepository(db).save(
                new DeviceEntity(UUID.randomUUID(), null, "n", null, null, "m", DeviceState.OFF, null));
        assertNull(db.lastUpdate().params().get(3));
        assertNull(db.lastUpdate().params().get(4));
    }

    @Test
    void deviceRowIsMapped() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("user_id", owner);
        row.put("name", "Термометр");
        row.put("last_trans", OffsetDateTime.of(2026, 11, 6, 15, 0, 0, 0, ZoneOffset.ofHours(3)));
        row.put("charge_level", (short) 77);
        row.put("manufacturer", "Acme");
        row.put("device_state", "OFF");
        row.put("geolocation", "55.7,37.6");
        db.rowsToReturn = List.of(row);

        DeviceEntity d = new PostgresDeviceRepository(db).findById(id).orElseThrow();

        assertEquals(id, d.getId());
        assertEquals(owner, d.getUserId());
        assertEquals("Термометр", d.getName());
        assertEquals(Instant.parse("2026-11-06T12:00:00Z"), d.getLastDataTime());
        assertEquals(77, d.getBatteryLevel());
        assertEquals(DeviceState.OFF, d.getState());
        assertEquals(id, db.queries.get(0).params().get(0));
    }

    @Test
    void deviceRowWithNullsIsMapped() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("user_id", null);
        row.put("name", "n");
        row.put("last_trans", null);
        row.put("charge_level", null);
        row.put("manufacturer", null);
        row.put("device_state", "ON");
        row.put("geolocation", null);
        db.rowsToReturn = List.of(row);

        DeviceEntity d = new PostgresDeviceRepository(db).findById(UUID.randomUUID()).orElseThrow();

        assertNull(d.getLastDataTime());
        assertNull(d.getBatteryLevel());   // не 0: NULL в базе должен остаться null
    }

    @Test
    void deviceNotFoundIsEmpty() {
        PostgresDeviceRepository repo = new PostgresDeviceRepository(db);
        assertTrue(repo.findById(UUID.randomUUID()).isEmpty());
        assertFalse(repo.existsById(UUID.randomUUID()));
    }

    @Test
    void deviceExistsWhenRowReturned() {
        db.rowsToReturn = List.of(Map.of("?column?", 1));
        assertTrue(new PostgresDeviceRepository(db).existsById(UUID.randomUUID()));
    }

    @Test
    void deviceDeleteReportsWhetherRowWasDeleted() {
        PostgresDeviceRepository repo = new PostgresDeviceRepository(db);
        db.updateResult = 1;
        assertTrue(repo.deleteById(UUID.randomUUID()));
        db.updateResult = 0;
        assertFalse(repo.deleteById(UUID.randomUUID()));
    }

    @Test
    void updateLastDataTimeDoesNotGoBackInTime() {
        UUID id = UUID.randomUUID();
        new PostgresDeviceRepository(db).updateLastDataTime(id, Instant.parse("2026-11-06T12:00:00Z"));

        RecordingDatabaseUtils.Call call = db.lastUpdate();
        assertTrue(call.sql().startsWith("UPDATE device SET last_trans = ?"));
        assertTrue(call.sql().contains("last_trans < ?"));
        OffsetDateTime expected = OffsetDateTime.of(2026, 11, 6, 12, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(List.of(expected, id, expected), call.params());
    }

    // ---------- пользователи ----------

    @Test
    void userSaveIsUpsertSoUpdateWorks() {
        UUID id = UUID.randomUUID();
        byte[] hash = {1, 2, 3};
        new PostgresUserRepository(db).save(
                new UserEntity(id, "vera", "v@x.ru", "+7", LocalDate.of(2005, 1, 1), hash, UserRole.ADMIN));

        RecordingDatabaseUtils.Call call = db.lastUpdate();
        assertTrue(call.sql().contains("ON CONFLICT (id) DO UPDATE"));
        assertFalse(call.sql().contains("email = EXCLUDED.email"), "почта не должна меняться при обновлении");
        assertEquals(List.of(id, "vera", "v@x.ru", "+7", LocalDate.of(2005, 1, 1), hash, UserRole.ADMIN),
                call.params());
    }

    @Test
    void userRowIsMappedAndRoleIsCaseInsensitive() {
        UUID id = UUID.randomUUID();
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("username", "vera");
        row.put("email", "v@x.ru");
        row.put("phone", null);
        row.put("birthdate", LocalDate.of(2005, 1, 1));
        row.put("password_hash", new byte[]{9});
        row.put("user_role", "admin");
        db.rowsToReturn = List.of(row);

        UserEntity u = new PostgresUserRepository(db).findById(id).orElseThrow();

        assertEquals(id, u.getId());
        assertEquals("v@x.ru", u.getEmail());
        assertNull(u.getPhone());
        assertEquals(LocalDate.of(2005, 1, 1), u.getDateOfBirth());
        assertArrayEquals(new byte[]{9}, u.getPasswordHash());
        assertEquals(UserRole.ADMIN, u.getRole());
    }

    @Test
    void findPasswordHashByEmail() {
        db.rowsToReturn = List.of(Map.of("password_hash", new byte[]{1, 2}));
        PostgresUserRepository repo = new PostgresUserRepository(db);

        assertArrayEquals(new byte[]{1, 2}, repo.findPasswordHashByEmail("v@x.ru").orElseThrow());
        assertEquals(List.of("v@x.ru"), db.queries.get(0).params());

        db.rowsToReturn = List.of();
        assertTrue(repo.findPasswordHashByEmail("nobody@x.ru").isEmpty());
        assertFalse(repo.existsByEmail("nobody@x.ru"));
    }

    // ---------- телеметрия ----------

    @Test
    void telemetrySaveConvertsInstant() {
        UUID id = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        new PostgresTelemetryRepository(db).save(
                new TelemetryEntity(id, device, Instant.parse("2026-11-06T12:00:00Z"), (short) 2, 4.5));

        assertEquals(List.of(id, device, OffsetDateTime.of(2026, 11, 6, 12, 0, 0, 0, ZoneOffset.UTC), (short) 2, 4.5),
                db.lastUpdate().params());
    }

    @Test
    void averageValueSince() {
        Map<String, Object> row = new HashMap<>();
        row.put("avg", 12.5);
        db.rowsToReturn = List.of(row);

        assertEquals(12.5, new PostgresTelemetryRepository(db)
                .averageValueSince(UUID.randomUUID(), Instant.EPOCH).getAsDouble());
        assertEquals(OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), db.queries.get(0).params().get(1));
    }

    @Test
    void averageOfNoRowsIsEmpty() {
        Map<String, Object> row = new HashMap<>();
        row.put("avg", null);   // AVG по пустому набору в SQL даёт NULL
        db.rowsToReturn = List.of(row);

        assertTrue(new PostgresTelemetryRepository(db)
                .averageValueSince(UUID.randomUUID(), Instant.EPOCH).isEmpty());
    }

    // ---------- флаг уведомления о батарее ----------

    @Test
    void batteryFlagIsChangedByConditionalUpdate() {
        PostgresDeviceRepository repo = new PostgresDeviceRepository(db);
        UUID id = UUID.randomUUID();

        db.updateResult = 1;
        assertTrue(repo.disableBatteryNotification(id));
        assertTrue(db.lastUpdate().sql().contains("SET battery_notify_allowed = FALSE"));
        assertTrue(db.lastUpdate().sql().endsWith("AND battery_notify_allowed"));
        assertEquals(List.of(id), db.lastUpdate().params());

        db.updateResult = 0;   // флаг уже был false: уведомление не нужно
        assertFalse(repo.disableBatteryNotification(id));

        db.updateResult = 1;
        assertTrue(repo.enableBatteryNotification(id));
        assertTrue(db.lastUpdate().sql().contains("SET battery_notify_allowed = TRUE"));
        assertTrue(db.lastUpdate().sql().endsWith("AND NOT battery_notify_allowed"));
    }

    // ---------- правила ----------

    @Test
    void rulesAreReadThroughDeviceToRule() {
        UUID ruleId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        Map<String, Object> row = new HashMap<>();
        row.put("id", ruleId);
        row.put("telemetry_type", (short) 2);
        row.put("operator", "LE");
        row.put("value", 100.0);
        row.put("is_strict", true);
        db.rowsToReturn = List.of(row);

        List<RuleEntity> rules = new PostgresRuleRepository(db).findByDeviceId(deviceId);

        assertEquals(List.of(new RuleEntity(ruleId, (short) 2, RuleOperator.LE, 100.0, true)), rules);
        assertTrue(db.queries.get(0).sql().contains("JOIN device_to_rule"));
        assertTrue(db.queries.get(0).sql().contains("is_strict"));
        assertEquals(List.of(deviceId), db.queries.get(0).params());
    }
}