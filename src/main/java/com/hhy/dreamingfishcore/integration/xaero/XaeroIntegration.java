package com.hhy.dreamingfishcore.integration.xaero;

import com.hhy.dreamingfishcore.DreamingFishCore;
import java.util.concurrent.atomic.AtomicBoolean;
import net.neoforged.fml.ModList;

/**
 * Xaero 联动的总开关（**不含任何 Xaero 类型**，所以永远能安全加载）。
 *
 * <p>三道保险：</p>
 * <ol>
 *   <li>没装 Xaero → {@link #modLoaded()} 为 false，直接返回（不接触 Xaero 类）；</li>
 *   <li>装了但注册表还没被 mixin 捕获 → 每 tick 检查一次，自己就绪再装；</li>
 *   <li>安装过程中任何异常（版本不符、签名变了）→ **记一次日志后永久放弃**，
 *       绝不让它变成每帧抛异常。</li>
 * </ol>
 */
public final class XaeroIntegration {

    /** Xaero 世界地图的 modid（区域高亮画在它上面，小地图会读取它）。 */
    public static final String WORLD_MAP_MOD_ID = "xaeroworldmap";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private XaeroIntegration() {
    }

    public static boolean modLoaded() {
        return ModList.get().isLoaded(WORLD_MAP_MOD_ID);
    }

    /** 已成功挂上（用于日志/调试）。 */
    public static boolean installed() {
        return INSTALLED.get();
    }

    /** 客户端每 tick 调一次；绝大多数 tick 在第一行就返回。 */
    public static void tick() {
        if (INSTALLED.get() || XaeroRegistryCapture.isInstalled() || XaeroRegistryCapture.hasFailed()) {
            return;
        }
        if (!modLoaded() || !XaeroRegistryCapture.hasRegistry()) {
            return;
        }
        try {
            XaeroInstaller.install(XaeroRegistryCapture.registry());
            INSTALLED.set(true);
            DreamingFishCore.LOGGER.info("[map] 已把区域图层挂到 Xaero 世界地图（小地图会读取同一份数据）");
        } catch (Throwable throwable) {
            XaeroRegistryCapture.markFailed();
            DreamingFishCore.LOGGER.warn("[map] 挂载 Xaero 区域图层失败，本次游戏期间不再尝试：{}",
                    throwable.toString());
        }
    }
}
