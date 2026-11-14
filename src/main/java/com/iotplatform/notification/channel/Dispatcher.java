package com.iotplatform.notification.channel;

import com.iotplatform.notification.exception.DeliveryException;
import com.iotplatform.notification.model.Notification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Рассылает уведомление по всем каналам. Ошибка одного канала не мешает другим: она пишется в лог.
 * send() работает в фоне (SMTP медленный, нельзя задерживать ответ IoT Core).
 * Executor передаётся снаружи: в Main это пул потоков, в тестах Runnable::run (всё выполняется сразу).
 */
public class Dispatcher {
    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);

    public record Result(String channel, Channel.Status status, String error) {}

    private final List<Channel> channels;
    private final Executor executor;

    public Dispatcher(List<Channel> channels, Executor executor) {
        this.channels = List.copyOf(channels);
        this.executor = executor;
    }

    public void send(Notification n) {
        try {
            executor.execute(() -> deliver(n));
        } catch (RejectedExecutionException e) {
            log.warn("Notification not sent: service is shutting down");
        }
    }

    /** Синхронная доставка; никогда не бросает исключений, результат по каждому каналу в списке. */
    public List<Result> deliver(Notification n) {
        List<Result> results = new ArrayList<>();
        for (Channel channel : channels) {
            try {
                if (!channel.enabled()) {
                    results.add(new Result(channel.name(), Channel.Status.DISABLED, null));
                    continue;
                }
                results.add(new Result(channel.name(), channel.send(n), null));
            } catch (DeliveryException e) {
                log.error("Failed to send {} via channel {}: {}", n.type(), channel.name(), e.getMessage());
                results.add(new Result(channel.name(), Channel.Status.FAILED, e.getMessage()));
            } catch (Exception e) {
                log.error("Unexpected error in channel {}", channel.name(), e);
                results.add(new Result(channel.name(), Channel.Status.FAILED, String.valueOf(e)));
            }
        }
        StringBuilder line = new StringBuilder("Notification " + n.type() + " for user " + n.userId() + ":");
        for (Result r : results) line.append(' ').append(r.channel()).append('=').append(r.status());
        log.info(line.toString());
        return results;
    }
}