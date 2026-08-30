package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 只为一次付费复活复制物品栏。
 *
 * <p>自定义死亡系统不再修改全局 {@code keepInventory} 游戏规则；复活时通过 Clone
 * 事件按 UUID 复制物品栏，避免一个玩家的死亡流程改变整个世界的规则。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class CustomRespawnInventoryManager {
    /**
     * 保存请求时的快照，而不是等 Clone 事件再读取旧实体。这样即使复活前发生了
     * 菜单同步、第三方模组修改或短暂的实体替换，也只会复制本次已结算的物品。
     */
    private static final Map<UUID, ListTag> PENDING_TRANSFERS = new ConcurrentHashMap<>();

    private CustomRespawnInventoryManager() {
    }

    /** 在服务端确认付费复活物品已还原后调用。 */
    public static void request(ServerPlayer player) {
        if (player != null) {
            PENDING_TRANSFERS.put(player.getUUID(), player.getInventory().save(new ListTag()).copy());
        }
    }

    /** 玩家退出前清理未执行的单次复制请求，避免 UUID 被后续实体复用。 */
    public static void cancel(ServerPlayer player) {
        if (player != null) {
            PENDING_TRANSFERS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()
                || !(event.getEntity() instanceof ServerPlayer newPlayer)
                || !(event.getOriginal() instanceof ServerPlayer oldPlayer)) {
            return;
        }

        ListTag snapshot = PENDING_TRANSFERS.remove(newPlayer.getUUID());
        if (snapshot == null) {
            return;
        }

        Inventory destination = newPlayer.getInventory();
        ListTag destinationBackup = destination.save(new ListTag());
        try {
            destination.clearContent();
            destination.load(snapshot);
        } catch (RuntimeException exception) {
            // 快照解析异常时尽量回退到原实体的当前栏位，至少不覆盖原版已有数据。
            try {
                destination.clearContent();
                destination.load(oldPlayer.getInventory().save(new ListTag()));
            } catch (RuntimeException fallbackException) {
                try {
                    destination.clearContent();
                    destination.load(destinationBackup);
                } catch (RuntimeException rollbackException) {
                    exception.addSuppressed(fallbackException);
                    exception.addSuppressed(rollbackException);
                }
            }
            DreamingFishCore.LOGGER.error("玩家 {} 复活物品快照复制失败",
                    newPlayer.getScoreboardName(), exception);
            return;
        }
        destination.setChanged();
        newPlayer.inventoryMenu.broadcastChanges();
        DreamingFishCore.LOGGER.debug("已为玩家 {} 执行一次性自定义复活物品复制",
                newPlayer.getScoreboardName());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_TRANSFERS.clear();
    }
}
