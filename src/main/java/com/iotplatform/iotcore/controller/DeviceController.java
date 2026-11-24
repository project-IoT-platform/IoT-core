package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.service.AlertService;
import com.iotplatform.iotcore.service.DeviceService;
import com.iotplatform.iotcore.service.NotificationService;

import java.util.UUID;

/**
 * Контроллер устройств: связывает HTTP-слой с {@code DeviceService}, а также запускает уведомления
 * о регистрации и проверку заряда батареи.
 */
public class DeviceController {
    private final DeviceService service;
    private final NotificationService notifications;
    private final AlertService alerts;

    /**
     * Создаёт контроллер.
     *
     * @param service сервис устройств
     * @param notifications клиент сервиса уведомлений
     * @param alerts сервис проверок на норму
     */
    public DeviceController(DeviceService service, NotificationService notifications, AlertService alerts) {
        this.service = service;
        this.notifications = notifications;
        this.alerts = alerts;
    }

    /**
     * Возвращает устройство по id.
     *
     * @param id идентификатор устройства
     * @return найденное устройство
     * @throws com.iotplatform.iotcore.exception.DeviceNotFoundException если устройства с таким id нет
     */
    public DeviceDto getById(UUID id) {
        return service.findById(id);
    }

    /**
     * Регистрирует устройство (если id уже есть, запись обновляется), затем отправляет уведомление о регистрации
     * и проверяет заряд батареи. Если валидация или сохранение упали, уведомления не отправляются.
     *
     * @param dto данные устройства
     * @throws com.iotplatform.iotcore.exception.ValidationException если данные некорректны или указанного пользователя нет
     */
    public void register(DeviceDto dto) {
        service.register(dto);                          // проверка на валидность + сохранение; ошибка -> дальше не идём
        notifications.notifyDeviceRegistered(dto);      // письмо о регистрации устройства
        alerts.onBattery(dto.getDeviceId(), dto.getBatteryLevel());
    }

    /**
     * Обновляет устройство и проверяет заряд батареи по новым данным.
     *
     * @param id идентификатор обновляемого устройства (имеет приоритет над id в теле)
     * @param dto новые данные устройства
     * @return сохранённое устройство
     * @throws com.iotplatform.iotcore.exception.DeviceNotFoundException если устройства с таким id нет
     * @throws com.iotplatform.iotcore.exception.ValidationException если данные некорректны или указанного пользователя нет
     */
    public DeviceDto update(UUID id, DeviceDto dto) {
        DeviceDto updated = service.update(id, dto);
        alerts.onBattery(updated.getDeviceId(), updated.getBatteryLevel());
        return updated;
    }

    /**
     * Удаляет устройство.
     *
     * @param id идентификатор устройства
     * @throws com.iotplatform.iotcore.exception.DeviceNotFoundException если устройства с таким id нет
     */
    public void delete(UUID id) {
        service.delete(id);
    }
}