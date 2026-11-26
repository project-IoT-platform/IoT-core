package com.iotplatform.iotcore.repository;

import com.iotplatform.iotcore.entity.DeviceEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Хранилище устройств.
 */
public interface DeviceRepository {
    List<DeviceEntity> findByOwner(UUID owner, String search, Integer limit, int offset);

    /**
     * Ищет устройство по id.
     *
     * @param id идентификатор устройства
     * @return устройство или пустой {@code Optional}, если его нет
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    Optional<DeviceEntity> findById(UUID id);   // ищет устройство в бае по id
    /**
     * Проверяет, есть ли устройство с таким id.
     *
     * @param id идентификатор устройства
     * @return {@code true}, если устройство существует
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    boolean existsById(UUID id); // существует ли устройство с таким id?
    /**
     * Сохраняет устройство: создаёт запись, а если такой id уже есть, обновляет её.
     *
     * @param device сохраняемое устройство
     * @return сохранённое устройство
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    DeviceEntity save(DeviceEntity device);     // создаёт, а если id уже есть, обновляет
    /**
     * Удаляет устройство.
     *
     * @param id идентификатор устройства
     * @return {@code true}, если что-то было удалено
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    boolean deleteById(UUID id);                // true, если что-то удалили
    /**
     * Обновляет только время последних данных устройства.
     *
     * @param id идентификатор устройства
     * @param lastTrans время последнего показания
     * @return количество обновлённых строк (0, если устройства нет или записанное время не старше нового)
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    int updateLastDataTime(UUID id, Instant lastTrans); // обновляет только время последних данных
    // флаг battery_notify_allowed: можно ли отправить уведомление о низком заряде
    /**
     * Запрещает дальнейшие уведомления о низком заряде (флаг {@code battery_notify_allowed} = false).
     *
     * @param id идентификатор устройства
     * @return {@code true}, если флаг был включён и теперь сброшен (то есть уведомление нужно отправить именно сейчас)
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    boolean disableBatteryNotification(UUID id);
    /**
     * Снова разрешает уведомления о низком заряде (флаг {@code battery_notify_allowed} = true).
     *
     * @param id идентификатор устройства
     * @return {@code true}, если флаг был выключен и теперь включён
     * @throws com.iotplatform.iotcore.exception.DatabaseException если запрос к БД не выполнился
     */
    boolean enableBatteryNotification(UUID id);
}