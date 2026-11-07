package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.RuleOperator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleDtoTest {

    private Set<String> missing(RuleDto dto) {
        return assertThrows(ValidationException.class, dto::validate).getErrors().keySet();
    }

    @Test
    void completeDtoIsValid() {
        assertDoesNotThrow(() -> new RuleDto((short) 2, RuleOperator.LE, 100.0, true).validate());
    }

    @Test
    void strictFlagIsOptional() {
        assertDoesNotThrow(() -> new RuleDto((short) 2, RuleOperator.LE, 100.0, null).validate());
    }

    @Test
    void emptyDtoReportsThreeRequiredFieldsButNotStrict() {
        assertEquals(Set.of("telemetryType", "operator", "value"),
                missing(new RuleDto(null, null, null, null)));
    }

    @Test
    void missingTypeIsReportedAlone() {
        assertEquals(Set.of("telemetryType"), missing(new RuleDto(null, RuleOperator.GE, 0.0, true)));
    }

    @Test
    void missingOperatorIsReportedAlone() {
        assertEquals(Set.of("operator"), missing(new RuleDto((short) 2, null, 0.0, true)));
    }

    @Test
    void missingValueIsReportedAlone() {
        assertEquals(Set.of("value"), missing(new RuleDto((short) 2, RuleOperator.GE, null, true)));
    }

    @Test
    void zeroLimitIsValid() {
        assertDoesNotThrow(() -> new RuleDto((short) 2, RuleOperator.GE, 0.0, false).validate());
    }
}