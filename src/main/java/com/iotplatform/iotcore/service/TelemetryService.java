package com.iotplatform.iotcore.service;

import com.iotplatform.iotcore.dto.TelemetryRequest;

public class TelemetryService {
    private static final System.Logger LOG = System.getLogger(TelemetryService.class.getName());

    public void process(TelemetryRequest request) {
        LOG.log(System.Logger.Level.INFO, "Получена телеметрия: {0}", request);
    }
}
