package com.iotplatform.iotcore.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iotplatform.iotcore.exception.DeviceNotFoundException;
import com.iotplatform.iotcore.exception.ValidationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public abstract class JsonPostHandler<T> implements HttpHandler {
    private static final System.Logger LOG = System.getLogger(JsonPostHandler.class.getName());

    private final Class<T> requestType;

    protected JsonPostHandler(Class<T> requestType) {
        this.requestType = requestType;
    }

    /** Здесь конкретный эндпоинт проверяет данные и вызывает сервис. */
    protected abstract void process(T request);

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                send(exchange, 405, Map.of("error", "Поддерживается только POST"));
                return;
            }
            byte[] body = exchange.getRequestBody().readAllBytes();
            T request = Json.MAPPER.readValue(body, requestType);
            if (request == null) {
                send(exchange, 400, Map.of("error", "Пустое тело запроса"));
                return;
            }
            process(request);
            exchange.sendResponseHeaders(202, -1); // 202 Accepted, тела ответа нет
        } catch (JsonProcessingException e) {
            send(exchange, 400, Map.of("error", "Некорректный JSON: " + e.getOriginalMessage()));
        } catch (ValidationException e) {
            send(exchange, 400, e.getErrors());
        } catch (DeviceNotFoundException e) {
            send(exchange, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Внутренняя ошибка", e);
            send(exchange, 500, Map.of("error", "Внутренняя ошибка сервера"));
        } finally {
            exchange.close();
        }
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
