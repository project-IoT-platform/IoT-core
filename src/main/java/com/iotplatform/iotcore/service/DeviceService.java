package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.DeviceRequest;

public class DeviceService {
    private static final System.Logger LOG = System.getLogger(DeviceService.class.getName());

    public void register(DeviceRequest request) {
        LOG.log(System.Logger.Level.INFO, "Получена информация об устройстве: {0}", request);
    }
}