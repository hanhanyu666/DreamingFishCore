package com.hhy.dreamingfishcore.client.ui.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationTest {
    @Test
    void negativeDurationCreatesPersistentNotification() {
        Notification notification = Notification.builder()
                .durationMs(-1L)
                .build();

        assertEquals(Long.MAX_VALUE, notification.durationMs());
    }

    @Test
    void zeroDurationStillUsesMinimumFiniteDuration() {
        Notification notification = Notification.builder()
                .durationMs(0L)
                .build();

        assertEquals(1L, notification.durationMs());
    }
}
