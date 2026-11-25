package com.iotplatform.iotcore.http;

import com.iotplatform.iotcore.controller.*;
import com.iotplatform.iotcore.repository.PostgresDeviceLimitsRepository;
import com.iotplatform.iotcore.repository.PostgresTelemetryRepository;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP-сервер IoT Core на Netty: принимает запросы на порту, разбирает JSON и передаёт запрос роутеру,
 * который вызывает нужный контроллер. Размер тела запроса ограничен 1 МБ.
 */
public class HttpApi {
    private static final Logger log = LoggerFactory.getLogger(HttpApi.class);

    private static final int MAX_BODY_BYTES = 1024 * 1024;

    private final int port;
    private final ApiRouter router;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private EventExecutorGroup businessGroup;
    private Channel serverChannel;

    /**
     * Создаёт сервер (без запуска).
     *
     * @param port порт, который будет слушать сервер
     * @param deviceController контроллер устройств
     * @param userController контроллер пользователей
     * @param telemetryController контроллер телеметрии
     * @param notificationController контроллер уведомлений
     * @param ruleController контроллер правил
     */
    public HttpApi(int port,
                   DeviceController deviceController,
                   UserController userController,
                   TelemetryController telemetryController,
                   NotificationController notificationController,
                   RuleController ruleController) {
        this(port, new ApiRouter(deviceController, userController, telemetryController,
                notificationController, ruleController));
    }

    public HttpApi(int port, DeviceController deviceController, UserController userController,
                   TelemetryController telemetryController, NotificationController notificationController,
                   RuleController ruleController, String notificationToken,
                   PostgresTelemetryRepository telemetry, PostgresDeviceLimitsRepository limits) {
        this(port, new ApiRouter(deviceController, userController, telemetryController, notificationController,
                ruleController, new NotificationDataApi(notificationToken, telemetry, limits)));
    }

    private HttpApi(int port, ApiRouter router) {
        this.port = port;
        this.router = router;
    }

    /**
     * Запускает сервер и регистрирует остановку при завершении JVM.
     *
     * @throws IllegalStateException если порт занят или сервер не удалось запустить (ресурсы при этом освобождаются)
     */
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        businessGroup = new DefaultEventExecutorGroup(16);
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(MAX_BODY_BYTES));
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
        log.info("IoT Core started on port {}", port);
    }

    /**
     * Останавливает сервер: закрывает канал и завершает пулы потоков. Можно вызывать, даже если сервер не запускался.
     */
    public void stop() {
        if (serverChannel != null) serverChannel.close();
        if (businessGroup != null) businessGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (bossGroup != null) bossGroup.shutdownGracefully();
    }
}