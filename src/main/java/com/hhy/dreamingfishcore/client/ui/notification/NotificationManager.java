package com.hhy.dreamingfishcore.client.ui.notification;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class NotificationManager {
    private static final Map<NotificationPosition, Channel> CHANNELS = new EnumMap<>(NotificationPosition.class);

    static {
        for (NotificationPosition position : NotificationPosition.values()) {
            CHANNELS.put(position, new Channel());
        }
    }

    private NotificationManager() {
    }

    public static synchronized void show(Notification notification) {
        if (notification == null) {
            return;
        }

        Channel channel = CHANNELS.get(notification.position());
        removeReplaceKey(channel, notification.replaceKey());

        if (notification.queuePolicy() == NotificationQueuePolicy.REPLACE) {
            channel.active.clear();
            channel.pending.clear();
        }

        if (notification.queuePolicy() == NotificationQueuePolicy.QUEUE
                && !channel.active.isEmpty()) {
            channel.pending.addLast(notification);
        } else {
            channel.active.add(new ActiveNotification(notification, System.currentTimeMillis()));
        }
    }

    public static synchronized List<ActiveNotification> getActive(NotificationPosition position) {
        Channel channel = CHANNELS.get(position);
        long now = System.currentTimeMillis();
        removeExpired(channel, now);

        if (channel.active.isEmpty() && !channel.pending.isEmpty()) {
            channel.active.add(new ActiveNotification(channel.pending.removeFirst(), now));
        }
        return new ArrayList<>(channel.active);
    }

    public static synchronized void removeContaining(NotificationPosition position, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        Channel channel = CHANNELS.get(position);
        channel.active.removeIf(entry -> entry.notification().message().getString().contains(text));
        channel.pending.removeIf(notification -> notification.message().getString().contains(text));
    }

    public static synchronized void clear(NotificationPosition position) {
        Channel channel = CHANNELS.get(position);
        channel.active.clear();
        channel.pending.clear();
    }

    public static synchronized void clearAll() {
        for (NotificationPosition position : NotificationPosition.values()) {
            clear(position);
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    private static void removeReplaceKey(Channel channel, String replaceKey) {
        if (replaceKey == null || replaceKey.isBlank()) {
            return;
        }
        channel.active.removeIf(entry -> replaceKey.equals(entry.notification().replaceKey()));
        channel.pending.removeIf(notification -> replaceKey.equals(notification.replaceKey()));
    }

    private static void removeExpired(Channel channel, long now) {
        Iterator<ActiveNotification> iterator = channel.active.iterator();
        while (iterator.hasNext()) {
            ActiveNotification entry = iterator.next();
            if (entry.isExpired(now)) {
                iterator.remove();
            }
        }
    }

    private static final class Channel {
        private final List<ActiveNotification> active = new ArrayList<>();
        private final ArrayDeque<Notification> pending = new ArrayDeque<>();
    }

    public record ActiveNotification(Notification notification, long startedAtMs) {
        public long ageMs(long now) {
            return Math.max(0L, now - startedAtMs);
        }

        public boolean isExpired(long now) {
            return ageMs(now) >= notification.durationMs();
        }
    }
}
