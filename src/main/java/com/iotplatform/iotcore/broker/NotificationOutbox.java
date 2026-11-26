package com.iotplatform.iotcore.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iotplatform.iotcore.db.DatabaseUtils;
import com.iotplatform.iotcore.http.Json;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NotificationOutbox {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutbox.class);
    private static final String EXCHANGE = "iot.events";
    private static final String QUEUE = "notification.events";
    private static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";

    private record Event(long id, UUID eventId, String type, String json) {}

    private final DatabaseUtils database;
    private final ConnectionFactory factory = new ConnectionFactory();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean returned = new AtomicBoolean();
    private Connection connection;
    private Channel channel;

    public NotificationOutbox(DatabaseUtils database, String brokerUri) {
        this.database = database;
        try {
            factory.setUri(brokerUri);
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректный RABBITMQ_URI", e);
        }
        factory.setAutomaticRecoveryEnabled(false);
        factory.setConnectionTimeout(3000);
    }

    public void enqueue(String type, Object payload) {
        UUID eventId = UUID.randomUUID();
        try {
            String envelope = Json.MAPPER.writeValueAsString(Map.of(
                    "version", 1, "eventId", eventId, "type", type, "payload", payload));
            database.update("INSERT INTO notification_outbox(event_id, event_type, envelope) VALUES (?, ?, ?::jsonb)",
                    eventId, type, envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось сериализовать событие " + type, e);
        }
    }

    public void start() {
        worker.scheduleWithFixedDelay(this::drain, 0, 1, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            worker.shutdownNow();
            try {
                if (worker.awaitTermination(6, TimeUnit.SECONDS)) closeConnection();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "notification-outbox-shutdown"));
    }

    private void drain() {
        try {
            for (int i = 0; i < 32; i++) {
                if (Thread.currentThread().isInterrupted() || !database.transaction(this::publishNext)) return;
            }
        } catch (RuntimeException e) {
            closeConnection();
            if (!worker.isShutdown()) log.warn("Публикация события отложена до следующей попытки", e);
        }
    }

    private boolean publishNext() {
        var pending = database.query("""
                SELECT id, event_id, event_type, envelope FROM notification_outbox
                ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, row -> new Event(row.getLong(1), row.getObject(2, UUID.class),
                row.getString(3), row.getString(4)));
        if (pending.isEmpty()) return false;

        Event event = pending.getFirst();
        try {
            connect();
            returned.set(false);
            var properties = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .deliveryMode(2)
                    .messageId(event.eventId().toString())
                    .build();
            channel.basicPublish(EXCHANGE, "notification." + event.type(), true,
                    properties, event.json().getBytes(StandardCharsets.UTF_8));
            // RabbitMQ sends basic.return before the confirm for an unroutable message.
            channel.waitForConfirmsOrDie(5000);
            if (returned.get()) throw new IOException("RabbitMQ не смог маршрутизировать событие");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Публикация прервана", e);
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException("RabbitMQ не подтвердил публикацию", e);
        }
        database.update("DELETE FROM notification_outbox WHERE id = ?", event.id());
        return true;
    }

    private void connect() throws IOException, TimeoutException {
        if (channel != null && channel.isOpen()) return;

        closeConnection();
        connection = factory.newConnection("iot-core-outbox");
        channel = connection.createChannel();
        channel.exchangeDeclare(EXCHANGE, "topic", true);
        channel.queueDeclare(DEAD_LETTER_QUEUE, true, false, false, null);
        channel.queueDeclare(QUEUE, true, false, false,
                Map.of("x-dead-letter-exchange", "", "x-dead-letter-routing-key", DEAD_LETTER_QUEUE));
        channel.queueBind(QUEUE, EXCHANGE, "notification.*");
        channel.addReturnListener(message -> returned.set(true));
        channel.confirmSelect();
    }

    private void closeConnection() {
        if (connection != null) {
            connection.abort(1000);
            connection = null;
            channel = null;
        }
    }
}