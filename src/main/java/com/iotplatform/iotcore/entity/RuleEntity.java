package com.iotplatform.iotcore.entity;

import com.iotplatform.iotcore.model.RuleOperator;

import java.util.UUID;

/**
 * Правило проверки телеметрии, как оно хранится в БД: показание должно удовлетворять условию
 * {@code значение <operator> value}.
 *
 * @param id идентификатор правила
 * @param telemetryType тип телеметрии, к которому относится правило
 * @param operator оператор сравнения
 * @param value граничное значение
 * @param strict {@code true}: при нарушении показание отклоняется; {@code false}: данные сохраняются, нарушение только фиксируется
 */
public record RuleEntity(
        UUID id,
        Short telemetryType,
        RuleOperator operator,
        Double value,
        boolean strict) {
}