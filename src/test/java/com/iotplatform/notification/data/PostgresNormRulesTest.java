package com.iotplatform.notification.data;

import com.iotplatform.notification.db.Db;
import com.iotplatform.notification.model.NormOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * /norm без настоящей БД: поддельное соединение запоминает SQL, параметры, commit и rollback,
 * а на поиск правила отдаёт заданные строки. Сам SQL против PostgreSQL здесь не выполняется.
 */
class PostgresNormRulesTest {
    record Stmt(String sql, Map<Integer, Object> params, Map<Integer, Integer> types) {}

    private final List<Stmt> executed = new ArrayList<>();
    private List<Map<String, Object>> foundRows = new ArrayList<>();
    private boolean committed, rolledBack, autoCommitOff;
    private String failOn;   // SQL, на котором бросить ошибку

    private final UUID device = UUID.randomUUID();
    private PostgresNormRules norms;

    @BeforeEach
    void setUp() {
        Db db = new Db("jdbc:none", "u", "p") {
            @Override
            public Connection getConnection() {
                return connection();
            }
        };
        norms = new PostgresNormRules(db);
    }

    @Test
    void noRuleYetCreatesRuleAndLink() {
        NormRules.Result r = norms.setNorm(device, (short) 1, NormOperator.LE, 30.0);

        assertEquals(NormRules.Result.CREATED, r);
        assertEquals(3, executed.size());
        assertEquals(PostgresNormRules.FIND, executed.get(0).sql());
        assertEquals(device, executed.get(0).params().get(1));
        assertEquals((short) 1, executed.get(0).params().get(2));
        assertEquals("LE", executed.get(0).params().get(3));
        assertEquals(Types.OTHER, executed.get(0).types().get(3));   // уходит в тип rule_operator

        Stmt insertRule = executed.get(1);
        assertEquals(PostgresNormRules.INSERT_RULE, insertRule.sql());
        assertEquals(30.0, insertRule.params().get(4));
        assertEquals(false, insertRule.params().get(5));             // новое правило нестрогое

        Stmt link = executed.get(2);
        assertEquals(PostgresNormRules.INSERT_LINK, link.sql());
        assertEquals(insertRule.params().get(1), link.params().get(2)); // связь на только что созданное правило
        assertEquals(device, link.params().get(3));

        assertTrue(autoCommitOff);
        assertTrue(committed);
        assertFalse(rolledBack);
    }

    @Test
    void ruleOnlyOfThisDeviceIsUpdated() {
        UUID ruleId = UUID.randomUUID();
        foundRows = List.of(row(ruleId, true, 1));

        assertEquals(NormRules.Result.UPDATED, norms.setNorm(device, (short) 2, NormOperator.GE, 5.0));

        assertEquals(2, executed.size());
        assertEquals(PostgresNormRules.UPDATE_VALUE, executed.get(1).sql());
        assertEquals(5.0, executed.get(1).params().get(1));
        assertEquals(ruleId, executed.get(1).params().get(2));
        assertTrue(committed);
    }

    @Test
    void sharedRuleIsCopiedSoOtherDevicesKeepTheirNorm() {
        UUID shared = UUID.randomUUID();
        foundRows = List.of(row(shared, true, 3));   // правило привязано к трём устройствам

        norms.setNorm(device, (short) 2, NormOperator.LE, 7.0);

        assertEquals(3, executed.size());
        Stmt copy = executed.get(1);
        assertEquals(PostgresNormRules.INSERT_RULE, copy.sql());
        assertEquals(true, copy.params().get(5));        // строгость берём у старого правила
        Stmt relink = executed.get(2);
        assertEquals(PostgresNormRules.RELINK, relink.sql());
        assertEquals(copy.params().get(1), relink.params().get(1));
        assertEquals(device, relink.params().get(2));
        assertEquals(shared, relink.params().get(3));
        assertTrue(executed.stream().noneMatch(s -> s.sql().equals(PostgresNormRules.UPDATE_VALUE)));
    }

    @Test
    void errorRollsBackEverything() {
        failOn = PostgresNormRules.INSERT_LINK;
        assertThrows(IllegalStateException.class, () -> norms.setNorm(device, (short) 1, NormOperator.LE, 30.0));
        assertTrue(rolledBack);
        assertFalse(committed);
    }

    // ---------- поддельный JDBC ----------

    private static Map<String, Object> row(UUID id, boolean strict, long links) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", id);
        r.put("is_strict", strict);
        r.put("links", links);
        return r;
    }

    private Connection connection() {
        return proxy(Connection.class, (m, args) -> switch (m) {
            case "setAutoCommit" -> { autoCommitOff = !(Boolean) args[0]; yield null; }
            case "commit" -> { committed = true; yield null; }
            case "rollback" -> { rolledBack = true; yield null; }
            case "close" -> null;
            case "prepareStatement" -> statement((String) args[0]);
            default -> throw new UnsupportedOperationException(m);
        });
    }

    private PreparedStatement statement(String sql) {
        Stmt s = new Stmt(sql, new HashMap<>(), new HashMap<>());
        return proxy(PreparedStatement.class, (m, args) -> switch (m) {
            case "setObject" -> {
                s.params().put((Integer) args[0], args[1]);
                if (args.length == 3) s.types().put((Integer) args[0], (Integer) args[2]);
                yield null;
            }
            case "executeQuery" -> { executed.add(s); yield resultSet(); }
            case "executeUpdate" -> {
                executed.add(s);
                if (sql.equals(failOn)) throw new SQLException("тестовая ошибка");
                yield 1;
            }
            case "close" -> null;
            default -> throw new UnsupportedOperationException(m);
        });
    }

    private ResultSet resultSet() {
        int[] i = {-1};
        return proxy(ResultSet.class, (m, args) -> switch (m) {
            case "next" -> ++i[0] < foundRows.size();
            case "getObject" -> foundRows.get(i[0]).get((String) args[0]);
            case "getBoolean" -> foundRows.get(i[0]).get((String) args[0]);
            case "getLong" -> foundRows.get(i[0]).get((String) args[0]);
            case "close" -> null;
            default -> throw new UnsupportedOperationException(m);
        });
    }

    interface Handler { Object call(String method, Object[] args) throws Exception; }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler h) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (p, method, args) -> h.call(method.getName(), args));
    }
}