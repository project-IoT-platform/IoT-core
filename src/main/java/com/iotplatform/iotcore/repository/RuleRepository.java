package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.RuleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Хранилище правил проверки телеметрии и их привязок к устройствам.
 */
public interface RuleRepository {
    /**
     * Создаёт правило.
     *
     * @param rule сохраняемое правило
     * @return сохранённое правило
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    RuleEntity save(RuleEntity rule);                 // создаёт правило
    /**
     * Ищет правило по id.
     *
     * @param id идентификатор правила
     * @return правило или пустой {@code Optional}
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    Optional<RuleEntity> findById(UUID id);           // ищет правило по id
    /**
     * Удаляет правило.
     *
     * @param id идентификатор правила
     * @return {@code true}, если что-то было удалено
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    boolean deleteById(UUID id);                      // true, если что-то удалили
    /**
     * Привязывает правило к устройству. Повторная привязка ничего не меняет.
     *
     * @param ruleId идентификатор правила
     * @param deviceId идентификатор устройства
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    void attachToDevice(UUID ruleId, UUID deviceId);  // привязывает правило к устройству
    /**
     * Отвязывает правило от устройства.
     *
     * @param ruleId идентификатор правила
     * @param deviceId идентификатор устройства
     * @return {@code true}, если связь существовала
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    boolean detachFromDevice(UUID ruleId, UUID deviceId); // отвязывает, true если связь была
    /**
     * Возвращает все правила устройства.
     *
     * @param deviceId идентификатор устройства
     * @return список правил (может быть пустым)
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    List<RuleEntity> findByDeviceId(UUID deviceId);   // все правила устройства
}
