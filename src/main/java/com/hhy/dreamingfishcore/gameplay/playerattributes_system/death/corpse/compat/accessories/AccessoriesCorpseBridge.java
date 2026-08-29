package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.accessories;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.CorpseAccessoryBridge;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.CorpseAccessoryEntry;
import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.DropRule;
import io.wispforest.accessories.api.events.OnDropCallback;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.impl.ExpandedSimpleContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.List;

/** Accessories 1.1.x 的死亡槽位桥接。 */
public final class AccessoriesCorpseBridge implements CorpseAccessoryBridge {
    public AccessoriesCorpseBridge() {
    }

    @Override
    public String providerId() {
        return CorpseAccessoryEntry.ACCESSORIES;
    }

    @Override
    public List<CorpseAccessoryEntry> snapshot(ServerPlayer player) {
        List<CorpseAccessoryEntry> entries = new ArrayList<>();
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) {
            return entries;
        }
        capability.getContainers().forEach((slotName, container) -> {
            snapshotContainer(entries, slotName, false, container.getAccessories());
            snapshotContainer(entries, slotName, true, container.getCosmeticAccessories());
        });
        return entries;
    }

    private static void snapshotContainer(List<CorpseAccessoryEntry> entries,
                                          String slotName,
                                          boolean cosmetic,
                                          ExpandedSimpleContainer stacks) {
        for (int index = 0; index < stacks.getContainerSize(); index++) {
            ItemStack stack = stacks.getItem(index);
            if (!stack.isEmpty()) {
                entries.add(new CorpseAccessoryEntry(
                        CorpseAccessoryEntry.ACCESSORIES, slotName, index, cosmetic, stack));
            }
        }
    }

    @Override
    public void reconcile(ServerPlayer player,
                          LivingDropsEvent event,
                          List<CorpseAccessoryEntry> entries) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        for (CorpseAccessoryEntry entry : entries) {
            if (!providerId().equals(entry.provider()) || entry.isEmpty()) {
                continue;
            }
            AccessoriesContainer container = capability == null
                    ? null
                    : capability.getContainers().get(entry.slotName());
            ExpandedSimpleContainer stacks = container == null
                    ? null
                    : entry.cosmetic() ? container.getCosmeticAccessories() : container.getAccessories();
            ItemStack current = stacks != null
                    && entry.slotIndex() >= 0
                    && entry.slotIndex() < stacks.getContainerSize()
                    ? stacks.getItem(entry.slotIndex())
                    : ItemStack.EMPTY;

            if (current.isEmpty()) {
                ItemStack dropped = CorpseAccessoryBridge.takeMatchingDrop(event.getDrops(), entry.stack());
                entry.setStack(dropped);
                continue;
            }
            DropRule rule = resolveDropRule(player, event, entry, current, container);
            if (rule == DropRule.KEEP) {
                entry.setStack(ItemStack.EMPTY);
                continue;
            }
            if (rule == DropRule.DESTROY
                    || rule == DropRule.DEFAULT
                    && EnchantmentHelper.has(current, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                stacks.setItem(entry.slotIndex(), ItemStack.EMPTY);
                container.markChanged();
                entry.setStack(ItemStack.EMPTY);
                continue;
            }

            entry.setStack(current.copy());
            stacks.setItem(entry.slotIndex(), ItemStack.EMPTY);
            container.markChanged();
        }
    }

    private static DropRule resolveDropRule(ServerPlayer player,
                                            LivingDropsEvent event,
                                            CorpseAccessoryEntry entry,
                                            ItemStack stack,
                                            AccessoriesContainer container) {
        DropRule rule = container.slotType() == null
                ? DropRule.DEFAULT
                : container.slotType().dropRule();
        SlotReference reference = SlotReference.of(
                player, entry.slotName(), entry.slotIndex());
        if (rule == DropRule.DEFAULT) {
            rule = AccessoriesAPI.getOrDefaultAccessory(stack)
                    .getDropRule(stack, reference, event.getSource());
        }
        rule = OnDropCallback.getAlternativeRule(rule, stack, reference, event.getSource());
        if (rule == DropRule.DEFAULT
                && Accessories.RULE_KEEP_ACCESSORY_INVENTORY != null
                && player.level().getGameRules().getRule(Accessories.RULE_KEEP_ACCESSORY_INVENTORY).get()) {
            return DropRule.KEEP;
        }
        return rule;
    }

    @Override
    public boolean restore(Player player, CorpseAccessoryEntry entry) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) {
            return false;
        }
        AccessoriesContainer container = capability.getContainers().get(entry.slotName());
        if (container == null) {
            return false;
        }
        ExpandedSimpleContainer stacks = entry.cosmetic()
                ? container.getCosmeticAccessories()
                : container.getAccessories();
        if (entry.slotIndex() < 0 || entry.slotIndex() >= stacks.getContainerSize()
                || !stacks.getItem(entry.slotIndex()).isEmpty()) {
            return false;
        }
        stacks.setItem(entry.slotIndex(), entry.stack().copy());
        container.markChanged();
        capability.updateContainers();
        entry.setStack(ItemStack.EMPTY);
        return true;
    }

    @Override
    public void rollbackRestore(Player player, CorpseAccessoryEntry restoredEntry) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) {
            return;
        }
        AccessoriesContainer container = capability.getContainers().get(restoredEntry.slotName());
        if (container == null) {
            return;
        }
        ExpandedSimpleContainer stacks = restoredEntry.cosmetic()
                ? container.getCosmeticAccessories()
                : container.getAccessories();
        if (restoredEntry.slotIndex() >= 0
                && restoredEntry.slotIndex() < stacks.getContainerSize()
                && ItemStack.matches(stacks.getItem(restoredEntry.slotIndex()), restoredEntry.stack())) {
            stacks.setItem(restoredEntry.slotIndex(), ItemStack.EMPTY);
            container.markChanged();
            capability.updateContainers();
        }
    }
}
