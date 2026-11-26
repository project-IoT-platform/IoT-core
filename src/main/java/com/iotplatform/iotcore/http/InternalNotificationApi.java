package com.iotplatform.iotcore.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.iotplatform.iotcore.controller.DeviceController;
import com.iotplatform.iotcore.controller.UserController;
import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.UserNotFoundException;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;

/** Private storage API for Notification Service. */
public final class InternalNotificationApi {
    private final DatabaseUtils db;
    private final String token;
    private final DeviceController devices;
    private final UserController users;

    public InternalNotificationApi(
            DatabaseUtils db, String token, DeviceController devices, UserController users) {
        this.db = db;
        this.token = token;
        this.devices = devices;
        this.users = users;
    }

    ApiRouter.Response handle(String method, String uri, byte[] body, String authorization) {
        if (token == null
                || token.isBlank()
                || authorization == null
                || !MessageDigest.isEqual(
                ("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8)))
            return new ApiRouter.Response(401, null);
        return respond(
                () -> {
                    URI u = URI.create(uri);
                    String[] p =
                            u.getPath().substring("/internal/notifications".length()).split("/");
                    Map<String, String> q = new HashMap<>();
                    if (u.getRawQuery() != null)
                        for (String pair : u.getRawQuery().split("&")) {
                            String[] kv = pair.split("=", 2);
                            if (kv.length != 2) throw new IllegalArgumentException();
                            q.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                        }
                    return dispatch(method, p, q, readBody(body));
                });
    }

    ApiRouter.Response handleDeviceLimits(String method, UUID device, byte[] body) {
        return respond(
                () ->
                        method.equals("GET")
                                ? limits(device)
                                : replaceLimits(device, readBody(body)));
    }

    ApiRouter.Response handleUserPreferences(String method, UUID user, byte[] body) {
        return respond(
                () -> {
                    if (method.equals("GET")) {
                        requireUser(user, false);
                        return frontendPreferences(user);
                    }
                    var changes =
                            preferenceChanges(
                                    readBody(body),
                                    "emailIntervalSeconds",
                                    "telegramIntervalSeconds");
                    if (changes.isEmpty()
                            || changes.values().stream()
                            .anyMatch(
                                    seconds ->
                                            seconds != 0
                                                    && (seconds < 60
                                                    || seconds > 7 * 24 * 3600))) {
                        throw new IllegalArgumentException(
                                "Интервал должен быть равен 0 или составлять от минуты до 7 дней");
                    }
                    return db.transaction(
                            () -> {
                                requireUser(user, true);
                                savePreferences(user, changes);
                                return frontendPreferences(user);
                            });
                });
    }

    private void requireUser(UUID user, boolean lock) {
        String sql = "SELECT id FROM users WHERE id=?" + (lock ? " FOR NO KEY UPDATE" : "");
        if (db.query(sql, row -> row.getObject(1), user).isEmpty()) throw new Missing();
    }

    private Map<String, Integer> frontendPreferences(UUID user) {
        var intervals = preferences(user);
        return Map.of(
                "emailIntervalSeconds",
                intervals.get("email"),
                "telegramIntervalSeconds",
                intervals.get("telegram"));
    }

    private Map<String, Integer> preferences(UUID user) {
        var intervals = new HashMap<>(Map.of("email", 0, "telegram", 0));
        db.query(
                        "SELECT channel,interval_seconds FROM notification_preferences WHERE"
                                + " user_id=?",
                        row -> Map.entry(row.getString(1), row.getInt(2)),
                        user)
                .forEach(entry -> intervals.put(entry.getKey(), entry.getValue()));
        return intervals;
    }

    private static Map<String, Integer> preferenceChanges(
            JsonNode body, String emailKey, String telegramKey) {
        if (body == null || !body.isObject()) throw new IllegalArgumentException();
        var changes = new LinkedHashMap<String, Integer>();
        if (body.has(emailKey)) changes.put("email", integer(body, emailKey));
        if (body.has(telegramKey)) changes.put("telegram", integer(body, telegramKey));
        return changes;
    }

    private void savePreferences(UUID user, Map<String, Integer> changes) {
        changes.forEach(
                (channel, seconds) ->
                        db.update(
                                """
                                INSERT INTO notification_preferences(user_id, channel, interval_seconds)
                                VALUES (?, ?, ?)
                                ON CONFLICT(user_id, channel) DO UPDATE SET interval_seconds = EXCLUDED.interval_seconds
                                """,
                                user,
                                channel,
                                seconds));
    }

    private static JsonNode readBody(byte[] body) throws java.io.IOException {
        return body == null || body.length == 0 ? null : Json.MAPPER.readTree(body);
    }

    private ApiRouter.Response respond(Callable<Object> request) {
        try {
            Object result = request.call();
            return result == null
                    ? new ApiRouter.Response(204, null)
                    : new ApiRouter.Response(200, Json.MAPPER.writeValueAsBytes(result));
        } catch (Missing | DeviceNotFoundException | UserNotFoundException e) {
            return new ApiRouter.Response(404, null);
        } catch (IllegalArgumentException
                 | java.time.DateTimeException
                 | com.fasterxml.jackson.core.JsonProcessingException e) {
            return new ApiRouter.Response(400, null);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .error("Internal notification API failed", e);
            return new ApiRouter.Response(500, null);
        }
    }

    private static final class Missing extends RuntimeException {}

    private static JsonNode field(JsonNode n, String k) {
        if (n == null || !n.hasNonNull(k)) throw new IllegalArgumentException(k);
        return n.get(k);
    }

    private static String text(JsonNode n, String k) {
        var v = field(n, k);
        if (!v.isTextual()) throw new IllegalArgumentException(k);
        return v.asText();
    }

    private static String optional(JsonNode n, String k) {
        return n.hasNonNull(k) ? text(n, k) : null;
    }

    private static Instant instant(String value) {
        if (value == null) throw new IllegalArgumentException("Missing timestamp");
        return Instant.parse(value);
    }

    private static UUID uuid(String s) {
        if (s == null) throw new IllegalArgumentException();
        return UUID.fromString(s);
    }

    private static int integer(JsonNode n, String k) {
        var v = field(n, k);
        if (!v.isIntegralNumber() || !v.canConvertToInt() || v.intValue() < 0)
            throw new IllegalArgumentException(k);
        return v.intValue();
    }

    private static double number(JsonNode n, String k) {
        var v = field(n, k);
        if (!v.isNumber() || !Double.isFinite(v.doubleValue()))
            throw new IllegalArgumentException(k);
        return v.doubleValue();
    }

    private static String channel(String s) {
        if (!"email".equals(s) && !"telegram".equals(s)) throw new IllegalArgumentException();
        return s;
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) m.put((String) values[i], values[i + 1]);
        return m;
    }

    private static String time(ResultSet r, String k) throws SQLException {
        return r.getObject(k, OffsetDateTime.class).toInstant().toString();
    }

    private Object dispatch(String m, String[] p, Map<String, String> q, JsonNode n)
            throws Exception {
        if (p.length == 3 && m.equals("GET")) {
            if (p[1].equals("devices")) return devices.getById(uuid(p[2]));
            if (p[1].equals("users")) return users.getById(uuid(p[2]));
        }
        if (p.length == 4 && p[1].equals("telegram")) {
            if (p[2].equals("chats")) {
                long chat = Long.parseLong(p[3]);
                if (m.equals("GET"))
                    return db
                            .query(
                                    "SELECT user_id FROM telegram_chats WHERE chat_id=?",
                                    r -> Map.of("userId", r.getObject(1, UUID.class)),
                                    chat)
                            .stream()
                            .findFirst()
                            .orElseThrow(Missing::new);
                if (m.equals("DELETE")) {
                    db.update("DELETE FROM telegram_chats WHERE chat_id=?", chat);
                    return null;
                }
                if (m.equals("PUT")) {
                    UUID user = uuid(text(n, "userId"));
                    db.transaction(
                            () -> {
                                db.update("LOCK TABLE telegram_chats IN SHARE ROW EXCLUSIVE MODE");
                                db.update(
                                        "DELETE FROM telegram_chats WHERE user_id=? OR chat_id=?",
                                        user,
                                        chat);
                                db.update(
                                        "INSERT INTO telegram_chats(user_id,chat_id) VALUES (?,?)",
                                        user,
                                        chat);
                                return null;
                            });
                    return null;
                }
            }
            if (p[2].equals("users") && m.equals("GET"))
                return db
                        .query(
                                "SELECT chat_id FROM telegram_chats WHERE user_id=?",
                                r -> Map.of("chatId", r.getLong(1)),
                                uuid(p[3]))
                        .stream()
                        .findFirst()
                        .orElseThrow(Missing::new);
        }
        if (p.length == 2 && p[1].equals("settings")) {
            if (m.equals("GET"))
                return db.query(
                                "SELECT norm,deviation FROM notification_defaults WHERE id=1",
                                r -> map("norm", r.getDouble(1), "deviation", r.getDouble(2)))
                        .get(0);
            if (m.equals("PUT")) {
                double norm = number(n, "norm"), dev = number(n, "deviation");
                if (dev < 0) throw new IllegalArgumentException();
                db.update(
                        "UPDATE notification_defaults SET norm=?,deviation=? WHERE id=1",
                        norm,
                        dev);
                return null;
            }
        }
        if (p.length == 3 && p[1].equals("preferences")) {
            UUID user = uuid(p[2]);
            if (m.equals("GET")) return preferences(user);
            if (m.equals("PUT")) {
                var changes = preferenceChanges(n, "email", "telegram");
                return db.transaction(
                        () -> {
                            savePreferences(user, changes);
                            return null;
                        });
            }
        }
        if (p.length == 2 && p[1].equals("buffer")) {
            if (m.equals("POST")) {
                var details = field(n, "details");
                if (!details.isObject()) throw new IllegalArgumentException();
                for (var v : details) if (!v.isTextual()) throw new IllegalArgumentException();
                String device = optional(n, "deviceId");
                db.update(
                        "INSERT INTO"
                                + " notification_buffer(user_id,channel,type,device_id,device_name,event_time,description,details,telegram_text)"
                                + " VALUES (?,?,?,?,?,?,?,?,?)",
                        uuid(text(n, "userId")),
                        channel(text(n, "channel")),
                        text(n, "type"),
                        device == null ? null : uuid(device),
                        optional(n, "deviceName"),
                        Instant.parse(text(n, "time")),
                        optional(n, "description"),
                        Json.MAPPER.writeValueAsString(details),
                        text(n, "telegramText"));
                return null;
            }
            if (m.equals("GET")) {
                int limit = Integer.parseInt(q.getOrDefault("limit", "500"));
                if (limit < 1 || limit > 500) throw new IllegalArgumentException();
                return db.query(
                        "SELECT * FROM notification_buffer WHERE user_id=? AND channel=? ORDER BY"
                                + " created_at,id LIMIT ?",
                        r -> {
                            Object details;
                            try {
                                details = Json.MAPPER.readTree(r.getString("details"));
                            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                                throw new SQLException("Invalid notification details", e);
                            }
                            return map(
                                    "id",
                                    r.getLong("id"),
                                    "type",
                                    r.getString("type"),
                                    "deviceId",
                                    r.getObject("device_id"),
                                    "deviceName",
                                    r.getString("device_name"),
                                    "time",
                                    time(r, "event_time"),
                                    "description",
                                    r.getString("description"),
                                    "details",
                                    details,
                                    "telegramText",
                                    r.getString("telegram_text"));
                        },
                        uuid(q.get("userId")),
                        channel(q.get("channel")),
                        limit);
            }
            if (m.equals("DELETE")) {
                var ids = field(n, "ids");
                if (!ids.isArray() || ids.size() > 500) throw new IllegalArgumentException();
                List<Long> list = new ArrayList<>();
                for (var id : ids) {
                    if (!id.isIntegralNumber() || !id.canConvertToLong())
                        throw new IllegalArgumentException();
                    list.add(id.longValue());
                }
                if (!list.isEmpty())
                    db.update(
                            "DELETE FROM notification_buffer WHERE id IN ("
                                    + String.join(",", Collections.nCopies(list.size(), "?"))
                                    + ")",
                            list.toArray());
                return null;
            }
        }
        if (p.length == 3 && p[1].equals("buffer")) {
            if (p[2].equals("groups") && m.equals("GET"))
                return db.query(
                        "SELECT user_id,channel,MIN(created_at) oldest,COUNT(*) cnt FROM"
                                + " notification_buffer GROUP BY user_id,channel",
                        r ->
                                map(
                                        "userId",
                                        r.getObject(1),
                                        "channel",
                                        r.getString(2),
                                        "oldest",
                                        time(r, "oldest"),
                                        "count",
                                        r.getInt("cnt")));
            if (p[2].equals("expired") && m.equals("DELETE")) {
                db.update(
                        "DELETE FROM notification_buffer WHERE created_at<?",
                        instant(q.get("cutoff")));
                return null;
            }
        }
        if (p.length >= 4 && p[1].equals("devices")) {
            UUID device = uuid(p[2]);
            if (p.length == 4 && p[3].equals("limits")) {
                if (m.equals("GET")) return limits(device);
                if (m.equals("PUT")) return replaceLimits(device, n);
            }
            if (db.query("SELECT id FROM device WHERE id=?", r -> r.getObject(1), device).isEmpty())
                throw new Missing();
            if (p.length == 5
                    && p[3].equals("telemetry")
                    && p[4].equals("average")
                    && m.equals("GET")) {
                Instant since = instant(q.get("since"));
                String type = q.get("telemetryType");
                String sql =
                        "SELECT AVG(value) average,COUNT(*) cnt FROM telemetry WHERE device_id=?"
                                + " AND recorded_at>=?";
                var args = new ArrayList<Object>(List.of(device, since));
                if (type != null) {
                    sql += " AND telemetry_type=?";
                    args.add(Short.parseShort(type));
                }
                return db.query(
                                sql,
                                r ->
                                        map(
                                                "average",
                                                r.getObject("average"),
                                                "count",
                                                r.getLong("cnt")),
                                args.toArray())
                        .get(0);
            }
            if (p.length == 6 && p[3].equals("norms") && m.equals("PUT")) {
                short type = Short.parseShort(p[4]);
                String op = p[5];
                if (!Set.of("GE", "LE").contains(op)) throw new IllegalArgumentException();
                double value = number(n, "value");
                UUID id =
                        db.transaction(
                                () -> {
                                    lockDevice(device);
                                    db.update(
                                            "DELETE FROM device_to_rule d USING rule r WHERE"
                                                    + " d.rule_id=r.id AND d.device_id=? AND"
                                                    + " r.telemetry_type=? AND"
                                                    + " r.operator=?::rule_operator AND r.is_strict",
                                            device,
                                            type,
                                            op);
                                    return addRule(device, type, op, value, true);
                                });
                return map(
                        "id",
                        id,
                        "telemetryType",
                        type,
                        "operator",
                        op,
                        "value",
                        value,
                        "strict",
                        true);
            }
        }
        throw new Missing();
    }

    private void lockDevice(UUID id) {
        if (db.query("SELECT id FROM device WHERE id=? FOR UPDATE", row -> row.getObject(1), id)
                .isEmpty()) {
            throw new Missing();
        }
    }

    private UUID addRule(UUID device, short type, String operator, double value, boolean strict) {
        UUID id = UUID.randomUUID();
        db.update(
                "INSERT INTO rule(id,telemetry_type,operator,value,is_strict) VALUES"
                        + " (?,?,?::rule_operator,?,?)",
                id,
                type,
                operator,
                value,
                strict);
        db.update(
                "INSERT INTO device_to_rule(id,rule_id,device_id) VALUES (?,?,?)",
                UUID.randomUUID(),
                id,
                device);
        return id;
    }

    private Object replaceLimits(UUID device, JsonNode n) {
        if (n == null || !n.isObject()) throw new IllegalArgumentException();
        Integer battery = n.hasNonNull("batteryLimit") ? integer(n, "batteryLimit") : null;
        if (battery != null && battery > 100) throw new IllegalArgumentException();
        return db.transaction(
                () -> {
                    lockDevice(device);
                    db.update(
                            "UPDATE device SET notification_battery_limit=? WHERE id=?",
                            battery,
                            device);
                    if (n.has("telemetryLimits")) {
                        var list = field(n, "telemetryLimits");
                        if (!list.isArray()) throw new IllegalArgumentException();
                        db.update(
                                "DELETE FROM device_to_rule d USING rule r WHERE"
                                        + " d.rule_id=r.id AND d.device_id=? AND NOT"
                                        + " r.is_strict AND r.operator IN"
                                        + " ('GE','GT','LE','LT')",
                                device);
                        Set<Short> seen = new HashSet<>();
                        for (var item : list) {
                            var t = field(item, "telemetryType");
                            if (!t.isIntegralNumber()
                                    || !t.canConvertToInt()
                                    || t.intValue() < Short.MIN_VALUE
                                    || t.intValue() > Short.MAX_VALUE
                                    || !seen.add(t.shortValue()))
                                throw new IllegalArgumentException();
                            Double
                                    lo =
                                    item.hasNonNull("lowerBound")
                                            ? number(item, "lowerBound")
                                            : null,
                                    hi =
                                            item.hasNonNull("upperBound")
                                                    ? number(item, "upperBound")
                                                    : null;
                            if (lo != null && hi != null && lo > hi)
                                throw new IllegalArgumentException();
                            if (lo != null) addRule(device, t.shortValue(), "GE", lo, false);
                            if (hi != null) addRule(device, t.shortValue(), "LE", hi, false);
                        }
                    }
                    return limits(device);
                });
    }

    private Object limits(UUID device) {
        var battery =
                db
                        .query(
                                "SELECT notification_battery_limit FROM device WHERE id=?",
                                r -> Optional.ofNullable(r.getObject(1, Integer.class)),
                                device)
                        .stream()
                        .findFirst()
                        .orElseThrow(Missing::new)
                        .orElse(null);
        var rows =
                db.query(
                        "SELECT r.telemetry_type,MAX(r.value) FILTER (WHERE r.operator IN"
                                + " ('GE','GT')) lo,MIN(r.value) FILTER (WHERE r.operator IN"
                                + " ('LE','LT')) hi FROM rule r JOIN device_to_rule d ON d.rule_id=r.id"
                                + " WHERE d.device_id=? AND NOT r.is_strict AND r.operator IN"
                                + " ('GE','GT','LE','LT') GROUP BY r.telemetry_type ORDER BY"
                                + " r.telemetry_type",
                        r ->
                                map(
                                        "telemetryType",
                                        r.getShort(1),
                                        "lowerBound",
                                        r.getObject(2),
                                        "upperBound",
                                        r.getObject(3)),
                        device);
        return map("batteryLimit", battery, "telemetryLimits", rows);
    }
}