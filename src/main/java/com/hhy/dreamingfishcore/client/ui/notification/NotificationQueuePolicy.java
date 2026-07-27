package com.hhy.dreamingfishcore.client.ui.notification;

public enum NotificationQueuePolicy {
    /** Multiple notifications can be visible at the same time. */
    STACK,
    /** Show one notification at a time and queue the rest. */
    QUEUE,
    /** Replace all current and queued notifications at this position. */
    REPLACE
}
