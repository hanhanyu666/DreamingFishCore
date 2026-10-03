package com.hhy.dreamingfishcore.client.ui.notification;

import net.minecraft.network.chat.Component;

/** Immutable client notification data. It contains no rendering or gameplay logic. */
public final class Notification {
    private final Component title;
    private final Component message;
    private final NotificationPosition position;
    private final NotificationTheme theme;
    private final NotificationQueuePolicy queuePolicy;
    private final long durationMs;
    private final String replaceKey;
    private final int accentColor;
    private final String biomeId;
    private final SystemEvent event;

    private Notification(Builder builder) {
        this.title = builder.title;
        this.message = builder.message;
        this.position = builder.position;
        this.theme = builder.theme;
        this.queuePolicy = builder.queuePolicy != null
                ? builder.queuePolicy
                : position == NotificationPosition.CENTER_TOP
                ? NotificationQueuePolicy.QUEUE
                : NotificationQueuePolicy.STACK;
        this.durationMs = builder.durationMs < 0L ? Long.MAX_VALUE : Math.max(1L, builder.durationMs);
        this.replaceKey = builder.replaceKey;
        this.accentColor = builder.accentColor;
        this.biomeId = builder.biomeId;
        this.event = builder.event;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Component title() {
        return title;
    }

    public Component message() {
        return message;
    }

    public NotificationPosition position() {
        return position;
    }

    public NotificationTheme theme() {
        return theme;
    }

    public NotificationQueuePolicy queuePolicy() {
        return queuePolicy;
    }

    public long durationMs() {
        return durationMs;
    }

    public String replaceKey() {
        return replaceKey;
    }

    public int accentColor() {
        return accentColor;
    }

    /** 区域提示对应的生物群系 id（如 {@code minecraft:cherry_grove}），其他通知为 null。 */
    public String biomeId() {
        return biomeId;
    }

    /** 右上角系统消息的结构化内容（谁、发生了什么），旧消息为 null。 */
    public SystemEvent event() {
        return event;
    }

    public int effectiveAccentColor() {
        return accentColor >= 0 ? 0xFF000000 | accentColor : theme.accentColor();
    }

    public static final class Builder {
        private Component title = Component.empty();
        private Component message = Component.empty();
        private NotificationPosition position = NotificationPosition.TOP_LEFT;
        private NotificationTheme theme = NotificationTheme.DEFAULT;
        private NotificationQueuePolicy queuePolicy;
        private long durationMs = 5000L;
        private String replaceKey;
        private int accentColor = -1;
        private String biomeId;
        private SystemEvent event;

        public Builder title(Component title) {
            this.title = title == null ? Component.empty() : title;
            return this;
        }

        public Builder message(Component message) {
            this.message = message == null ? Component.empty() : message;
            return this;
        }

        public Builder position(NotificationPosition position) {
            this.position = position == null ? NotificationPosition.TOP_LEFT : position;
            return this;
        }

        public Builder theme(NotificationTheme theme) {
            this.theme = theme == null ? NotificationTheme.DEFAULT : theme;
            return this;
        }

        public Builder queuePolicy(NotificationQueuePolicy queuePolicy) {
            this.queuePolicy = queuePolicy;
            return this;
        }

        /** A negative duration creates a persistent notification that requires explicit removal. */
        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder replaceKey(String replaceKey) {
            this.replaceKey = replaceKey;
            return this;
        }

        /** RGB color override for the border/accent, or -1 for the theme color. */
        public Builder accentColor(int accentColor) {
            this.accentColor = accentColor;
            return this;
        }

        /** 区域提示对应的生物群系，上方横幅据此选择风景与配色。 */
        public Builder biome(String biomeId) {
            this.biomeId = biomeId == null || biomeId.isBlank() ? null : biomeId;
            return this;
        }

        public Builder event(SystemEvent event) {
            this.event = event;
            return this;
        }

        public Notification build() {
            return new Notification(this);
        }
    }
}
