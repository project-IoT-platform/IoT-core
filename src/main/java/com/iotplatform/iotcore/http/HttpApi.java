package com.iotplatform.iotcore.http;

import com.iotplatform.iotcore.controller.DeviceController;
import com.iotplatform.iotcore.controller.NotificationController;
import com.iotplatform.iotcore.controller.TelemetryController;
import com.iotplatform.iotcore.controller.UserController;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

import java.util.Arrays;

public class HttpApi {
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final String DEFAULT_CORS_ORIGINS = "http://localhost:3000,http://127.0.0.1:3000";

    private final int port;
    private final ApiRouter router;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private EventExecutorGroup businessGroup;
    private Channel serverChannel;

    public HttpApi(int port,
                   DeviceController deviceController,
                   UserController userController,
                   TelemetryController telemetryController,
                   NotificationController notificationController) {
        this.port = port;
        this.router = new ApiRouter(deviceController, userController,
                telemetryController, notificationController);
    }

    /** Разрешённые origin фронта: переменная CORS_ORIGINS (через запятую) или localhost:3000. */
    private static CorsConfig corsConfig() {
        String[] origins = Arrays.stream(
                        System.getenv().getOrDefault("CORS_ORIGINS", DEFAULT_CORS_ORIGINS).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        return CorsConfigBuilder.forOrigins(origins)
                .allowedRequestMethods(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                        HttpMethod.DELETE, HttpMethod.OPTIONS)
                .allowedRequestHeaders("Content-Type")
                .maxAge(3600)
                .build();
    }

    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        businessGroup = new DefaultEventExecutorGroup(16);
        CorsConfig cors = corsConfig();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(MAX_BODY_BYTES));
                            ch.pipeline().addLast(new CorsHandler(cors));
                            ch.pipeline().addLast(businessGroup, new ApiHandler(router));
                        }
                    });
            serverChannel = bootstrap.bind(port).sync().channel();
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            stop();
            throw new IllegalStateException("Не удалось запустить HTTP-сервер на порту " + port, e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
        System.out.println("IoT Core запущен на порту " + port);
    }

    public void stop() {
        if (serverChannel != null) serverChannel.close();
        if (businessGroup != null) businessGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (bossGroup != null) bossGroup.shutdownGracefully();
    }
}