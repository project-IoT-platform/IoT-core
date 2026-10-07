package com.iotplatform.iotcore.http;

import com.iotplatform.iotcore.dto.TelemetryRequest;
import com.iotplatform.iotcore.service.TelemetryService;

public class TelemetryHandler extends JsonPostHandler<TelemetryRequest> {
    private final TelemetryService service;

    public TelemetryHandler(TelemetryService service) {
        super(TelemetryRequest.class);
        this.service = service;
    }

    @Override
    protected void process(TelemetryRequest request) {
        request.validate();
        service.process(request);
    }
}
