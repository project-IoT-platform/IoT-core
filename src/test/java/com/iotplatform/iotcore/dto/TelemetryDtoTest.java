package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelemetryDtoTest {
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.now();

    private Set<String> missing(TelemetryDto dto) {
        return assertThrows(ValidationException.class, dto::validate).getErrors().keySet();
    }

    @Test
    void completeDtoIsValid() {
        assertDoesNotThrow(() -> new TelemetryDto(id, now, (short) 1, 25.5).validate());
    }

    @Test
    void emptyDtoReportsAllFourFields() {
        assertEquals(Set.of("deviceId", "time", "telemetryType", "value"),
                missing(new TelemetryDto(null, null, null, null)));
    }

    @Test
    void missingDeviceIdIsReportedAlone() {
        assertEquals(Set.of("deviceId"), missing(new TelemetryDto(null, now, (short) 1, 1.0)));
    }

    @Test
    void missingTimeIsReportedAlone() {
        assertEquals(Set.of("time"), missing(new TelemetryDto(id, null, (short) 1, 1.0)));
    }

    @Test
    void missingTypeIsReportedAlone() {
        assertEquals(Set.of("telemetryType"), missing(new TelemetryDto(id, now, null, 1.0)));
    }

    @Test
    void missingValueIsReportedAlone() {
        assertEquals(Set.of("value"), missing(new TelemetryDto(id, now, (short) 1, null)));
    }

    @Test
    void zeroAndNegativeValuesAreValid() {
        assertDoesNotThrow(() -> new TelemetryDto(id, now, (short) 1, 0.0).validate());
        assertDoesNotThrow(() -> new TelemetryDto(id, now, (short) 1, -40.0).validate());
    }
}