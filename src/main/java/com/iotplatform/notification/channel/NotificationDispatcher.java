package com.iotplatform.notification.channel;

import com.iotplatform.notification.channel.NotificationChannel.DeliveryStatus;
import com.iotplatform.notification.exception.NotificationDeliveryException;
import com.iotplatform.notification.model.NotificationEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Рассылает событие по всем каналам; сбой одного канала не влияет на остальные. */
public final class NotificationDispatcher implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(NotificationDispatcher.class.getName());

    public record DeliveryResult(String channel, DeliveryStatus status, String error) {}

    private final List<NotificationChannel> channels;
    private final ExecutorService executor;

    public NotificationDispatcher(List<NotificationChannel> channels) {
        this.channels = List.copyOf(channels);
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "notification-dispatch-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newFixedThreadPool(4, factory);
    }

    /** Отправка в фоновом потоке (SMTP блокирующий, нельзя тормозить приём телеметрии). */
    public void dispatchAsync(NotificationEvent event) {
        try {
            executor.execute(() -> dispatch(event));
        } catch (RejectedExecutionException e) {
            LOG.log(System.Logger.Level.WARNING, "Уведомление не отправлено: сервис останавливается");
        }
    }

    /** Синхронная отправка. Никогда не бросает исключений, результат по каждому каналу в списке. */
    public List<DeliveryResult> dispatch(NotificationEvent event) {
        List<DeliveryResult> results = new ArrayList<>();
        for (NotificationChannel channel : channels) {
            try {
                if (!channel.isEnabled()) {
                    LOG.log(System.Logger.Level.DEBUG, "Канал " + channel.name()
                            + " не настроен, уведомление " + event.type() + " по нему не отправлено");
                    results.add(new DeliveryResult(channel.name(), DeliveryStatus.DISABLED, null));
                    continue;
                }
                results.add(new DeliveryResult(channel.name(), channel.send(event), null));
            } catch (NotificationDeliveryException e) {
                LOG.log(System.Logger.Level.ERROR, "Не удалось отправить уведомление " + event.type()
                        + " по каналу " + channel.name() + " (" + e.getReason() + "): " + e.getMessage());
                results.add(new DeliveryResult(channel.name(), DeliveryStatus.FAILED, e.getMessage()));
            } catch (Exception e) {
                LOG.log(System.Logger.Level.ERROR, "Непредвиденная ошибка канала " + channel.name(), e);
                results.add(new DeliveryResult(channel.name(), DeliveryStatus.FAILED, String.valueOf(e)));
            }
        }
        return results;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}