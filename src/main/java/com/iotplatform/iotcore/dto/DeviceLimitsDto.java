package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.exception.ValidationException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public record DeviceLimitsDto(Integer batteryLimit, List<TelemetryLimit> telemetryLimits) {
    public record TelemetryLimit(Short telemetryType, Double lowerBound, Double upperBound) {}

    public void validate() {
        if (batteryLimit != null && (batteryLimit < 0 || batteryLimit > 100)) {
            throw new ValidationException(Map.of("batteryLimit", "должно быть от 0 до 100"));
        }
        if (telemetryLimits == null) return;
        var types = new HashSet<Short>();
        for (TelemetryLimit limit : telemetryLimits) {
            if (limit == null || limit.telemetryType() == null || !types.add(limit.telemetryType())) {
                throw invalidLimits("каждый тип телеметрии должен быть указан один раз");
            }
            Double lower = limit.lowerBound();
            Double upper = limit.upperBound();
            if ((lower != null && !Double.isFinite(lower)) || (upper != null && !Double.isFinite(upper))) {
                throw invalidLimits("границы должны быть конечными числами");
            }
            if (lower != null && upper != null && lower > upper) {
                throw invalidLimits("нижняя граница больше верхней");
            }
        }
    }

    private static ValidationException invalidLimits(String message) {
        return new ValidationException(Map.of("telemetryLimits", message));
    }
}
