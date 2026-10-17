package com.iotplatform.iotcore.http;

import com.iotplatform.iotcore.controller.DeviceController;
import com.iotplatform.iotcore.controller.TelemetryController;
import com.iotplatform.iotcore.controller.UserController;

// ===== ДЛЯ АШУРА =====

public class HttpApi {
    private final int port;
    private final DeviceController deviceController;
    private final UserController userController;
    private final TelemetryController telemetryController;

    public HttpApi(int port,
                   DeviceController deviceController,
                   UserController userController,
                   TelemetryController telemetryController) {
        this.port = port;
        this.deviceController = deviceController;
        this.userController = userController;
        this.telemetryController = telemetryController;
    }

    // TODO Ашур:
    public void start() {
        System.out.println("TODO Ашур: HTTP-сервер ещё не написан (порт будет " + port + ")");
    }
}