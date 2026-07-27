package com.hhy.dreamingfishcore.client.ui.notification;

public enum NotificationTheme {
    DEFAULT(0xD8181C24, 0xAA8AB4FF, 0xFF74A9FF, 0xFFEAF3FF, 0xFFB9CFFF, 0x30456BA8),
    WARNING(0xDA241214, 0xD8FF5555, 0xFFFF3030, 0xFFFF5555, 0xFFFFB0B0, 0x55FF3030),
    SUCCESS(0xD812241B, 0xD870D89A, 0xFF63D68B, 0xFFE8FFF0, 0xFFB8F5CB, 0x4050D080),
    GOLD(0xD011141D, 0xE0CFA766, 0xFFD8B66E, 0xFFF2E7CF, 0xFFD8B66E, 0x3ACFA766),
    SYSTEM(0xD8151A22, 0xCC666666, 0xFF666666, 0xFFFFFFFF, 0xFFD8D8D8, 0x38666666);

    private final int backgroundColor;
    private final int borderColor;
    private final int accentColor;
    private final int textColor;
    private final int secondaryTextColor;
    private final int glowColor;

    NotificationTheme(int backgroundColor, int borderColor, int accentColor, int textColor,
                      int secondaryTextColor, int glowColor) {
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        this.accentColor = accentColor;
        this.textColor = textColor;
        this.secondaryTextColor = secondaryTextColor;
        this.glowColor = glowColor;
    }

    public int backgroundColor() {
        return backgroundColor;
    }

    public int borderColor() {
        return borderColor;
    }

    public int accentColor() {
        return accentColor;
    }

    public int textColor() {
        return textColor;
    }

    public int secondaryTextColor() {
        return secondaryTextColor;
    }

    public int glowColor() {
        return glowColor;
    }
}
