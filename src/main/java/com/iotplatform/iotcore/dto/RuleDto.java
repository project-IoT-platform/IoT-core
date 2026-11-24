package com.iotplatform.iotcore.dto;

import com.iotplatform.iotcore.exception.ValidationException;
import com.iotplatform.iotcore.model.RuleOperator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Описание правила для проверки телеметрии (тело запроса на создание).
 *
 * @param telemetryType тип телеметрии, к которому относится правило
 * @param operator оператор сравнения
 * @param value граничное значение
 * @param strict {@code true} или {@code null}: строгое правило, при нарушении показание отклоняется; {@code false}: нестрогое, данные сохраняются
 */
public record RuleDto(Short telemetryType, RuleOperator operator, Double value, Boolean strict) {

    /**
     * Проверяет, что тип телеметрии, оператор и значение заполнены.
     *
     * @throws com.iotplatform.iotcore.exception.ValidationException если какое-то обязательное поле не задано
     */
    public void validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (telemetryType == null) errors.put("telemetryType", "обязательное поле");
        if (operator == null) errors.put("operator", "обязательное поле");
        if (value == null) errors.put("value", "обязательное поле");
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }
}