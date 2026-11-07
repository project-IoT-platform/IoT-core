package com.iotplatform.notification.data;

import com.iotplatform.notification.model.Rule;

import java.util.Optional;
import java.util.UUID;

/** Правила отдельного устройства (порог батареи, норма, отклонение): хранятся в таблице rute. */
public interface RuleStore {
    /** Строка устройства из таблицы rute; пусто, если для устройства строки нет. */
    Optional<Rule> find(UUID deviceId);

    /** Записывает правила устройства (обновляет строку, а если её нет, создаёт). */
    void save(UUID deviceId, Rule rule);
}