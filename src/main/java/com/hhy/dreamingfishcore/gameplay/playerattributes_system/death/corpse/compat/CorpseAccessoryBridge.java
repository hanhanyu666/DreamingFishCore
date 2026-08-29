package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.Collection;
import java.util.List;

/** 可选饰品 API 与尸体物品模型之间的窄接口。 */
public interface CorpseAccessoryBridge {
    String providerId();

    List<CorpseAccessoryEntry> snapshot(ServerPlayer player);

    void reconcile(ServerPlayer player,
                   LivingDropsEvent event,
                   List<CorpseAccessoryEntry> entries);

    boolean restore(Player player, CorpseAccessoryEntry entry);

    void rollbackRestore(Player player, CorpseAccessoryEntry restoredEntry);

    static ItemStack takeMatchingDrop(Collection<ItemEntity> drops, ItemStack expected) {
        ItemEntity matched = findDrop(drops, expected, true);
        if (matched == null) {
            matched = findDrop(drops, expected, false);
        }
        if (matched == null) {
            ItemEntity uniqueSameItem = null;
            for (ItemEntity entity : drops) {
                if (entity != null && entity.getItem().is(expected.getItem())) {
                    if (uniqueSameItem != null) {
                        uniqueSameItem = null;
                        break;
                    }
                    uniqueSameItem = entity;
                }
            }
            matched = uniqueSameItem;
        }
        if (matched == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = matched.getItem().copy();
        drops.remove(matched);
        return result;
    }

    private static ItemEntity findDrop(Collection<ItemEntity> drops,
                                       ItemStack expected,
                                       boolean exactCount) {
        for (ItemEntity entity : drops) {
            if (entity == null) {
                continue;
            }
            boolean matches = exactCount
                    ? ItemStack.matches(entity.getItem(), expected)
                    : ItemStack.isSameItemSameComponents(entity.getItem(), expected);
            if (matches) {
                return entity;
            }
        }
        return null;
    }
}
