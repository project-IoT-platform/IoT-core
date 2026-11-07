package com.iotplatform.iotcore.http;

import com.iotplatform.iotcore.controller.*;
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

public class HttpApi {
    private static final Logger log = LoggerFactory.getLogger(HttpApi.class);

    private static final int MAX_BODY_BYTES = 1024 * 1024;

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
                   NotificationController notificationController,
                   RuleController ruleController) {
        this.port = port;
        this.router = new ApiRouter(deviceController, userController,
                telemetryController, notificationController, ruleController);
    }

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

    public void stop() {
        if (serverChannel != null) serverChannel.close();
        if (businessGroup != null) businessGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (bossGroup != null) bossGroup.shutdownGracefully();
    }
}