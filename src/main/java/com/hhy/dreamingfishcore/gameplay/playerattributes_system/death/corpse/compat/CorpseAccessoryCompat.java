package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 只在对应模组存在时反射加载桥接类，保证未安装饰品模组时 JVM 不解析其 API 类型。
 */
public final class CorpseAccessoryCompat {
    private static final Map<String, CorpseAccessoryBridge> BRIDGES = loadBridges();

    private CorpseAccessoryCompat() {
    }

    /** 在模组构造阶段加载桥接器，使其能及时监听第三方死亡规则事件。 */
    public static void initialize() {
        if (!BRIDGES.isEmpty()) {
            DreamingFishCore.LOGGER.info("尸体系统已启用饰品兼容：{}", BRIDGES.keySet());
        }
    }

    public static List<CorpseAccessoryEntry> snapshot(ServerPlayer player) {
        if (BRIDGES.isEmpty()) {
            return new ArrayList<>();
        }
        List<CorpseAccessoryEntry> result = new ArrayList<>();
        for (CorpseAccessoryBridge bridge : BRIDGES.values()) {
            try {
                result.addAll(bridge.snapshot(player));
            } catch (RuntimeException | LinkageError exception) {
                DreamingFishCore.LOGGER.error("无法快照 {} 饰品栏，本次死亡跳过该兼容层",
                        bridge.providerId(), exception);
            }
        }
        return result;
    }

    public static void reconcile(ServerPlayer player,
                                 LivingDropsEvent event,
                                 List<CorpseAccessoryEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        for (CorpseAccessoryBridge bridge : BRIDGES.values()) {
            try {
                bridge.reconcile(player, event, entries);
            } catch (RuntimeException | LinkageError exception) {
                DreamingFishCore.LOGGER.error("无法结算 {} 饰品死亡掉落，保留该模组原始行为",
                        bridge.providerId(), exception);
                entries.removeIf(entry -> bridge.providerId().equals(entry.provider()));
            }
        }
        entries.removeIf(CorpseAccessoryEntry::isEmpty);
    }

    public static boolean restore(Player player, CorpseAccessoryEntry entry) {
        CorpseAccessoryBridge bridge = BRIDGES.get(entry.provider());
        if (bridge == null || entry.isEmpty()) {
            return false;
        }
        try {
            return bridge.restore(player, entry);
        } catch (RuntimeException | LinkageError exception) {
            DreamingFishCore.LOGGER.error("无法把 {} 饰品还原到原槽位，改放入玩家物品栏",
                    entry.provider(), exception);
            return false;
        }
    }

    public static void rollbackRestore(Player player, CorpseAccessoryEntry entry) {
        CorpseAccessoryBridge bridge = BRIDGES.get(entry.provider());
        if (bridge == null) {
            return;
        }
        try {
            bridge.rollbackRestore(player, entry);
        } catch (RuntimeException | LinkageError exception) {
            DreamingFishCore.LOGGER.error("回滚 {} 饰品槽失败", entry.provider(), exception);
        }
    }

    private static Map<String, CorpseAccessoryBridge> loadBridges() {
        Map<String, CorpseAccessoryBridge> bridges = new HashMap<>();
        ModList modList = ModList.get();
        boolean accessoriesLoaded = modList.isLoaded("accessories");
        boolean accessoriesCuriosLayer = modList.isLoaded("accessories_compat_layer")
                || modList.isLoaded("accessories_compat");

        // Accessories 的 Curios 兼容层会镜像同一批槽位，此时只读取底层 Accessories。
        if (modList.isLoaded("curios") && !accessoriesCuriosLayer) {
            load("com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.curios.CuriosCorpseBridge")
                    .ifPresent(bridge -> bridges.put(bridge.providerId(), bridge));
        }
        if (accessoriesLoaded) {
            load("com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.accessories.AccessoriesCorpseBridge")
                    .ifPresent(bridge -> bridges.put(bridge.providerId(), bridge));
        }
        return Collections.unmodifiableMap(bridges);
    }

    private static java.util.Optional<CorpseAccessoryBridge> load(String className) {
        try {
            Class<?> type = Class.forName(className);
            return java.util.Optional.of((CorpseAccessoryBridge) type.getConstructor().newInstance());
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                 | IllegalAccessException | InvocationTargetException | ClassCastException
                 | LinkageError exception) {
            DreamingFishCore.LOGGER.error("无法加载尸体饰品兼容桥 {}", className, exception);
            return java.util.Optional.empty();
        }
    }
}
