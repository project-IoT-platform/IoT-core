package com.iotplatform.iotcore;

import com.iotplatform.iotcore.http.DeviceHandler;
import com.iotplatform.iotcore.http.TelemetryHandler;
import com.iotplatform.iotcore.service.DeviceService;
import com.iotplatform.iotcore.service.TelemetryService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/telemetry", new TelemetryHandler(new TelemetryService()));
        server.createContext("/api/devices", new DeviceHandler(new DeviceService()));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        System.out.println("IoT Core запущен на порту " + port);
    }
}
