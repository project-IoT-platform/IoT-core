package com.iotplatform.iotcore.http;

import com.iotplatform.iotcore.dto.DeviceRequest;
import com.iotplatform.iotcore.service.DeviceService;

public class DeviceHandler extends JsonPostHandler<DeviceRequest> {
    private final DeviceService service;

    public DeviceHandler(DeviceService service) {
        super(DeviceRequest.class);
        this.service = service;
    }

    @Override
    protected void process(DeviceRequest request) {
        request.validate();
        service.register(request);
    }
}
