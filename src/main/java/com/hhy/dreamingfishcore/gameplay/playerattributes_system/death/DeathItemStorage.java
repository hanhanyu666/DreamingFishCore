package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 处理待结算死亡记录中的物品快照。
 *
 * <p>物品快照现在保存在玩家持久化 NBT 中；本类不再持有服务器重启即丢失的静态 Map。</p>
 */
public final class DeathItemStorage {
    private DeathItemStorage() {
    }

    public static void storePlayerInventory(Player player) {
        if (PendingDeathData.captureInventory(player)) {
            DreamingFishCore.LOGGER.debug("已刷新玩家 {} 的持久化死亡物品快照", player.getScoreboardName());
        }
    }

    /**
     * 清空玩家当前物品栏，并把死亡快照投放到原死亡维度和坐标。
     *
     * @return 快照及目标维度有效、结算已经执行时返回 true
     */
    public static boolean dropStoredItems(ServerPlayer player) {
        if (!PendingDeathData.ensureInventorySnapshot(player)) {
            return false;
        }

        PendingDeathData.DeathLocation location = PendingDeathData.getDeathLocation(player);
        ResourceLocation dimensionId = ResourceLocation.tryParse(location.dimension());
        if (dimensionId == null) {
            DreamingFishCore.LOGGER.error("玩家 {} 的死亡维度无效：{}",
                    player.getScoreboardName(), location.dimension());
            return false;
        }

        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel targetLevel = player.server.getLevel(dimensionKey);
        if (targetLevel == null) {
            DreamingFishCore.LOGGER.error("玩家 {} 的死亡维度当前不存在：{}",
                    player.getScoreboardName(), location.dimension());
            return false;
        }

        List<ItemStack> stacks = decodeSnapshot(player);
        if (stacks == null) {
            return false;
        }

        // keepInventory 始终开启，因此普通复活需要显式清除玩家身上的实际物品。
        player.getInventory().clearContent();
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        int failedDrops = 0;
        for (ItemStack stack : stacks) {
            if (!spawnItemEntity(targetLevel, location.x(), location.y(), location.z(), stack)) {
                failedDrops++;
            }
        }

        if (failedDrops > 0) {
            DreamingFishCore.LOGGER.error("玩家 {} 的死亡掉落中有 {} 个物品实体未能加入世界",
                    player.getScoreboardName(), failedDrops);
        }
        DreamingFishCore.LOGGER.info("玩家 {} 选择普通复活，物品已投放到 {} ({}, {}, {})",
                player.getScoreboardName(), location.dimension(),
                (int) location.x(), (int) location.y(), (int) location.z());
        return true;
    }

    /**
     * keepInventory 已强制开启，保留物品只需确认持久化快照存在；实际物品无需搬运。
     */
    public static boolean keepStoredItems(ServerPlayer player) {
        if (!PendingDeathData.ensureInventorySnapshot(player)) {
            return false;
        }
        DreamingFishCore.LOGGER.info("玩家 {} 选择付费保留物品", player.getScoreboardName());
        return true;
    }

    private static List<ItemStack> decodeSnapshot(ServerPlayer player) {
        try {
            ListTag inventory = PendingDeathData.getInventorySnapshot(player);
            List<ItemStack> stacks = new ArrayList<>(inventory.size());
            for (int i = 0; i < inventory.size(); i++) {
                CompoundTag itemTag = inventory.getCompound(i);
                ItemStack stack = ItemStack.of(itemTag);
                if (!stack.isEmpty()) {
                    stacks.add(stack.copy());
                }
            }
            return stacks;
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error("无法解析玩家 {} 的死亡物品快照",
                    player.getScoreboardName(), exception);
            return null;
        }
    }

    private static boolean spawnItemEntity(ServerLevel level, double x, double y, double z, ItemStack stack) {
        ItemEntity itemEntity = new ItemEntity(level, x, y - 0.3D, z, stack.copy());
        itemEntity.setPickUpDelay(40);

        float speed = level.random.nextFloat() * 0.5F;
        float angle = level.random.nextFloat() * ((float) Math.PI * 2F);
        itemEntity.setDeltaMovement(
                -Mth.sin(angle) * speed,
                0.2D,
                Mth.cos(angle) * speed);
        return level.addFreshEntity(itemEntity);
    }
}
