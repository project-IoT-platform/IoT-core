package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.RuleDto;
import com.iotplatform.iotcore.entity.RuleEntity;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.RuleNotFoundException;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.RuleRepository;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Бизнес-логика правил: создание, поиск, удаление и привязка к устройствам.
 */
public class RuleService {
    private static final Logger log = LoggerFactory.getLogger(RuleService.class);

    private final RuleRepository rules;
    private final DeviceRepository devices;

    /**
     * Создаёт сервис.
     *
     * @param rules хранилище правил
     * @param devices хранилище устройств (проверка существования)
     */
    public RuleService(RuleRepository rules, DeviceRepository devices) {
        this.rules = rules;
        this.devices = devices;
    }

    /**
     * Создаёт правило с новым id. Если {@code strict} не указан, правило строгое.
     *
     * @param dto описание правила
     * @return созданное правило
     * @throws com.iotplatform.iotcore.exception.ValidationException если тип телеметрии, оператор или значение не заданы
     */
    public RuleEntity create(RuleDto dto) {
        dto.validate();
        boolean strict = dto.strict() == null || dto.strict();   // по умолчанию правило строгое
        RuleEntity rule = rules.save(new RuleEntity(UUID.randomUUID(),
                dto.telemetryType(), dto.operator(), dto.value(), strict));
        log.info("Rule {} created: type {}, {} {}, strict={}",
                rule.id(), rule.telemetryType(), rule.operator(), rule.value(), rule.strict());
        return rule;
    }

    /**
     * Ищет правило по id.
     *
     * @param id идентификатор правила
     * @return найденное правило
     * @throws RuleNotFoundException если правила нет
     */
    public RuleEntity findById(UUID id) {
        return rules.findById(id).orElseThrow(() -> new RuleNotFoundException(id));
    }

    /**
     * Удаляет правило.
     *
     * @param id идентификатор правила
     * @throws RuleNotFoundException если правила нет
     */
    public void delete(UUID id) {
        if (!rules.deleteById(id)) throw new RuleNotFoundException(id);
        log.info("Rule {} deleted", id);
    }

    /**
     * Привязывает правило к устройству. Повторная привязка ничего не меняет.
     *
     * @param deviceId идентификатор устройства
     * @param ruleId идентификатор правила
     * @throws DeviceNotFoundException если устройства нет
     * @throws RuleNotFoundException если правила нет
     */
    public void attach(UUID deviceId, UUID ruleId) {
        if (!devices.existsById(deviceId)) throw new DeviceNotFoundException(deviceId);
        if (rules.findById(ruleId).isEmpty()) throw new RuleNotFoundException(ruleId);
        rules.attachToDevice(ruleId, deviceId);
        log.info("Rule {} attached to device {}", ruleId, deviceId);
    }

    /**
     * Отвязывает правило от устройства.
     *
     * @param deviceId идентификатор устройства
     * @param ruleId идентификатор правила
     * @throws RuleNotFoundException если такой связи не было
     */
    public void detach(UUID deviceId, UUID ruleId) {
        if (!rules.detachFromDevice(ruleId, deviceId)) throw new RuleNotFoundException(ruleId);
        log.info("Rule {} detached from device {}", ruleId, deviceId);
    }

    /**
     * Возвращает все правила устройства.
     *
     * @param deviceId идентификатор устройства
     * @return список правил (может быть пустым)
     * @throws DeviceNotFoundException если устройства нет
     */
    public List<RuleEntity> findByDevice(UUID deviceId) {
        if (!devices.existsById(deviceId)) throw new DeviceNotFoundException(deviceId);
        return rules.findByDeviceId(deviceId);
    }
}