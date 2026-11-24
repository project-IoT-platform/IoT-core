package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.RuleDto;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.service.RuleService;

import java.util.List;
import java.util.UUID;

/**
 * Контроллер правил проверки телеметрии: создание, получение, удаление и привязка к устройствам.
 */
public class RuleController {
    private final RuleService service;

    /**
     * Создаёт контроллер.
     *
     * @param service сервис правил
     */
    public RuleController(RuleService service) {
        this.service = service;
    }

    /**
     * Создаёт правило.
     *
     * @param dto описание правила
     * @return созданное правило с новым id
     * @throws com.iotplatform.iotcore.exception.ValidationException если не заполнены обязательные поля
     */
    public RuleEntity create(RuleDto dto) { return service.create(dto); }
    /**
     * Возвращает правило по id.
     *
     * @param id идентификатор правила
     * @return найденное правило
     * @throws com.iotplatform.iotcore.exception.RuleNotFoundException если правила нет
     */
    public RuleEntity getById(UUID id) { return service.findById(id); }
    /**
     * Удаляет правило.
     *
     * @param id идентификатор правила
     * @throws com.iotplatform.iotcore.exception.RuleNotFoundException если правила нет
     */
    public void delete(UUID id) { service.delete(id); }
    /**
     * Привязывает правило к устройству.
     *
     * @param deviceId идентификатор устройства
     * @param ruleId идентификатор правила
     * @throws com.iotplatform.iotcore.exception.DeviceNotFoundException если устройства нет
     * @throws com.iotplatform.iotcore.exception.RuleNotFoundException если правила нет
     */
    public void attach(UUID deviceId, UUID ruleId) { service.attach(deviceId, ruleId); }
    /**
     * Отвязывает правило от устройства.
     *
     * @param deviceId идентификатор устройства
     * @param ruleId идентификатор правила
     * @throws com.iotplatform.iotcore.exception.RuleNotFoundException если такой связи не было
     */
    public void detach(UUID deviceId, UUID ruleId) { service.detach(deviceId, ruleId); }
    /**
     * Возвращает все правила, привязанные к устройству.
     *
     * @param deviceId идентификатор устройства
     * @return список правил (может быть пустым)
     * @throws com.iotplatform.iotcore.exception.DeviceNotFoundException если устройства нет
     */
    public List<RuleEntity> getByDevice(UUID deviceId) { return service.findByDevice(deviceId); }
}