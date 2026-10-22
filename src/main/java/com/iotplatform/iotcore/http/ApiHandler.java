package com.iotplatform.iotcore.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;

final class ApiHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final System.Logger LOG = System.getLogger(ApiHandler.class.getName());

    private final ApiRouter router;

    ApiHandler(ApiRouter router) {
        this.router = router;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        // есл кл шлт поломанный http
        if (!request.decoderResult().isSuccess()) {
            send(ctx, request, new ApiRouter.Response(400, null), false);
            return;
        }

        byte[] body = new byte[request.content().readableBytes()];
        request.content().readBytes(body);

        ApiRouter.Response response = router.handle(request.method().name(), request.uri(), body);
        send(ctx, request, response, HttpUtil.isKeepAlive(request));
    }

    private void send(ChannelHandlerContext ctx, FullHttpRequest request,
                      ApiRouter.Response response, boolean keepAlive) {
        ByteBuf content = response.body() == null
                ? Unpooled.EMPTY_BUFFER
                : Unpooled.wrappedBuffer(response.body());
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(
                request.protocolVersion(), HttpResponseStatus.valueOf(response.status()), content);

        if (response.body() != null) {
            httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        }
        if (response.status() != 204) {
            httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        }
        if (keepAlive) {
            if (!request.protocolVersion().isKeepAliveDefault()) {
                httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
        } else {
            httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }

        var future = ctx.writeAndFlush(httpResponse);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.log(System.Logger.Level.ERROR, "Ошибка в соединении", cause);
        ctx.close();
    }
}