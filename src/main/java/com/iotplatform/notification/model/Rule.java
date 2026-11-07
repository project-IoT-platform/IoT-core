package com.iotplatform.notification.model;

/**
 * Правила одного устройства из таблицы rute: минимальный заряд батареи (%), нормальное значение
 * температуры и допустимое отклонение. null означает «в таблице не задано», тогда берутся общие значения.
 */
public record Rule(Integer batteryLimit, Double norm, Double deviation) {}
