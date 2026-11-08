package com.iotplatform.iotcore.model;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

class RuleOperatorTest {

    @Test
    void operatorsCompareCorrectly() {
        assertTrue(RuleOperator.GE.test(5, 5));
        assertFalse(RuleOperator.GE.test(4.9, 5));
        assertTrue(RuleOperator.LE.test(5, 5));
        assertFalse(RuleOperator.LE.test(5.1, 5));
        assertFalse(RuleOperator.GT.test(5, 5));
        assertTrue(RuleOperator.GT.test(5.1, 5));
        assertFalse(RuleOperator.LT.test(5, 5));
        assertTrue(RuleOperator.LT.test(4.9, 5));
        assertTrue(RuleOperator.EQ.test(5, 5));
        assertFalse(RuleOperator.EQ.test(5.1, 5));
        assertTrue(RuleOperator.NE.test(5.1, 5));
        assertFalse(RuleOperator.NE.test(5, 5));
    }

    @Test
    void namesMatchDatabaseEnum() {
        // в schema.sql: CREATE TYPE rule_operator AS ENUM ('GE', 'LE', 'GT', 'LT', 'EQ', 'NE')
        for (String name : new String[]{"GE", "LE", "GT", "LT", "EQ", "NE"}) {
            assertNotNull(RuleOperator.valueOf(name));
        }
        assertEquals(6, RuleOperator.values().length);
    }

    @Test
    void symbolsAsInTeammateLog() {
        assertEquals("≤", RuleOperator.LE.getSymbol());
        assertEquals("≥", RuleOperator.GE.getSymbol());
    }
}