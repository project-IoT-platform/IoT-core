package com.iotplatform.iotcore.entity;

import com.iotplatform.iotcore.model.RuleOperator;

import java.util.UUID;

/**
 * Строка таблицы rule. strict = колонка is_strict.
 * telemetryType — Short (объект), потому что RuleEngine сравнивает его через equals с TelemetryDto.telemetryType().
 */
public record RuleEntity(UUID id, Short telemetryType, RuleOperator operator, double value, boolean strict) {}