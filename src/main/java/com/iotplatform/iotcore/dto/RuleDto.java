package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.RuleOperator;

import java.util.LinkedHashMap;
import java.util.Map;

public record RuleDto(Short telemetryType, RuleOperator operator, Double value) {

    public void validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (telemetryType == null) errors.put("telemetryType", "обязательное поле");
        if (operator == null) errors.put("operator", "обязательное поле");
        if (value == null) errors.put("value", "обязательное поле");
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }
}