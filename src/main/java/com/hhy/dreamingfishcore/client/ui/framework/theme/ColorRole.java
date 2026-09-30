package com.hhy.dreamingfishcore.client.ui.framework.theme;

/**
 * 按用途命名的颜色角色。界面代码只引用角色，具体色值由 {@link Theme} 决定，
 * 并可以通过 {@code config/dreamingfishcore/ui_theme.json} 覆盖。
 */
public enum ColorRole {
    /** 界面打开时覆盖在游戏画面上的遮罩。 */
    SCRIM,
    /** 设备外壳 / 最底层背景。 */
    SURFACE,
    /** 卡片等抬升一级的表面。 */
    SURFACE_RAISED,
    /** 可交互表面的悬停状态。 */
    SURFACE_HOVER,
    /** 弹窗、菜单等浮层表面。 */
    SURFACE_OVERLAY,
    /** 输入框、槽位等下沉表面。 */
    SURFACE_SUNKEN,
    OUTLINE,
    OUTLINE_STRONG,
    TEXT,
    TEXT_SECONDARY,
    TEXT_MUTED,
    TEXT_ON_ACCENT,
    ACCENT,
    ACCENT_SOFT,
    SUCCESS,
    WARNING,
    DANGER,
    INFO,
    GOLD,
    INFECTION,
    EXPERIENCE,
    OXYGEN,
    TRACK
}
