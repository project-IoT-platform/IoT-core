package com.iotplatform.iotcore.controller;

import com.iotplatform.iotcore.dto.DeviceDto;
import com.iotplatform.iotcore.service.DeviceService;

import java.util.List;
import java.util.UUID;

public class DeviceController {
    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    public List<DeviceDto> getAll() {
        return service.findAll();
    }

    public DeviceDto getById(UUID id) {
        return service.findById(id);
    }

    public void register(DeviceDto dto) {
        service.register(dto);
    }

    public DeviceDto update(UUID id, DeviceDto dto) {
        return service.update(id, dto);
    }

    public void delete(UUID id) {
        service.delete(id);
    }
}