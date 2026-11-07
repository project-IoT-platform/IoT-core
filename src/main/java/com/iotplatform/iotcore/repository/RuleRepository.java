package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.RuleEntity;

import java.util.List;
import java.util.UUID;

public interface RuleRepository {
    /** Все правила устройства (через таблицу device_to_rule). По типу телеметрии фильтрует RuleEngine. */
    List<RuleEntity> findByDeviceId(UUID deviceId);
}