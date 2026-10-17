package com.iotplatform.iotcore;

import com.iotplatform.iotcore.controller.DeviceController;
import com.iotplatform.iotcore.controller.TelemetryController;
import com.iotplatform.iotcore.controller.UserController;
import com.iotplatform.iotcore.db.Database;
import com.iotplatform.iotcore.http.HttpApi;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.PostgresDeviceRepository;
import com.iotplatform.iotcore.repository.PostgresTelemetryRepository;
import com.iotplatform.iotcore.repository.PostgresUserRepository;
import com.iotplatform.iotcore.repository.TelemetryRepository;
import com.iotplatform.iotcore.repository.UserRepository;
import com.iotplatform.iotcore.service.DeviceService;
import com.iotplatform.iotcore.service.TelemetryService;
import com.iotplatform.iotcore.service.UserService;

public class Main {
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        // 1. База данных и репозитории (Лёша)
        Database database = new Database();
        database.initSchema();
        DeviceRepository deviceRepository = new PostgresDeviceRepository(database);
        UserRepository userRepository = new PostgresUserRepository(database);
        TelemetryRepository telemetryRepository = new PostgresTelemetryRepository(database);

        // 2. Сервисы (Маша)
        DeviceService deviceService = new DeviceService(deviceRepository, userRepository);
        UserService userService = new UserService(userRepository);
        TelemetryService telemetryService = new TelemetryService(deviceRepository, telemetryRepository);

        // 3. Контроллеры (Маша)
        DeviceController deviceController = new DeviceController(deviceService);
        UserController userController = new UserController(userService);
        TelemetryController telemetryController = new TelemetryController(telemetryService);

        // 4. HTTP-сервер (Ашур)
        HttpApi api = new HttpApi(port, deviceController, userController, telemetryController);
        api.start();
    }
}