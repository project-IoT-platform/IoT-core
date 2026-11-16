package com.iotplatform.iotcore;

import com.iotplatform.iotcore.check.StubBatteryChecker;
import com.iotplatform.iotcore.check.StubTelemetryNormChecker;
import com.iotplatform.iotcore.check.RuleEngineTelemetryChecker;
import com.iotplatform.iotcore.controller.DeviceController;
import com.iotplatform.iotcore.controller.NotificationController;
import com.iotplatform.iotcore.controller.RuleController;
import com.iotplatform.iotcore.controller.TelemetryController;
import com.iotplatform.iotcore.controller.UserController;
import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.http.HttpApi;
import com.iotplatform.iotcore.repository.DeviceRepository;
import com.iotplatform.iotcore.repository.PostgresDeviceRepository;
import com.iotplatform.iotcore.repository.PostgresRuleRepository;
import com.iotplatform.iotcore.repository.PostgresTelemetryRepository;
import com.iotplatform.iotcore.repository.PostgresUserRepository;
import com.iotplatform.iotcore.repository.RuleRepository;
import com.iotplatform.iotcore.repository.TelemetryRepository;
import com.iotplatform.iotcore.repository.UserRepository;
import com.iotplatform.iotcore.service.AlertService;
import com.iotplatform.iotcore.service.DeviceService;
import com.iotplatform.iotcore.service.NotificationService;
import com.iotplatform.iotcore.service.RuleEngine;
import com.iotplatform.iotcore.service.RuleService;
import com.iotplatform.iotcore.service.TelemetryService;
import com.iotplatform.iotcore.service.UserService;

public class Main {
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8082"));
        String notificationUrl = System.getenv().getOrDefault("NOTIFICATION_URL", "http://localhost:8081");

        DatabaseUtils databaseUtils = DatabaseUtils.fromProperties("/db.properties");
        databaseUtils.initSchema();
        DeviceRepository deviceRepository = new PostgresDeviceRepository(databaseUtils);
        UserRepository userRepository = new PostgresUserRepository(databaseUtils);
        TelemetryRepository telemetryRepository = new PostgresTelemetryRepository(databaseUtils);
        RuleRepository ruleRepository = new PostgresRuleRepository(databaseUtils);

        NotificationService notificationService = new NotificationService(notificationUrl);

        DeviceService deviceService = new DeviceService(deviceRepository, userRepository);
        UserService userService = new UserService(userRepository);
        TelemetryService telemetryService = new TelemetryService(deviceRepository, telemetryRepository,
                new RuleEngine(ruleRepository));
        RuleService ruleService = new RuleService(ruleRepository, deviceRepository);
        AlertService alertService = new AlertService(new RuleEngineTelemetryChecker(ruleRepository), new StubBatteryChecker(),
                deviceRepository, notificationService);

        HttpApi api = new HttpApi(port,
                new DeviceController(deviceService, notificationService, alertService),
                new UserController(userService, notificationService),
                new TelemetryController(telemetryService, alertService),
                new NotificationController(notificationService),
                new RuleController(ruleService));
        api.start();
    }
}