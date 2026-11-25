package com.hhy.dreamingfishcore.integration.xaero;

import xaero.map.highlight.HighlighterRegistry;

/**
 * 真正接触 Xaero 类型的那一层。
 *
 * <p>**只允许在"确认装了 Xaero"之后被调用**（由 {@link XaeroIntegration} 把关）。
 * 这样没装 Xaero 的玩家永远不会加载这个类，也就不会出现 {@code NoClassDefFoundError}。</p>
 */
public final class XaeroInstaller {

    private XaeroInstaller() {
    }

    /** 把本模组的高亮器挂到 Xaero 世界地图的高亮注册表上。 */
    public static void install(Object registry) {
        if (XaeroRegistryCapture.isInstalledFor(registry)) {
            return;
        }
        if (!(registry instanceof HighlighterRegistry highlighterRegistry)) {
            throw new IllegalStateException("高亮注册表类型不符：" + registry.getClass().getName());
        }
        highlighterRegistry.register(new DreamingFishHighlighter());
        XaeroRegistryCapture.markInstalled(registry);
    }
}
