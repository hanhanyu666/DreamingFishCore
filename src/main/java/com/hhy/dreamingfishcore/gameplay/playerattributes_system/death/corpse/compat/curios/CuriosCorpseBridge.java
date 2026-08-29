package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.curios;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.CorpseAccessoryBridge;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.compat.CorpseAccessoryEntry;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.DropRulesEvent;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.common.CuriosConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Curios 9.5.x 的死亡槽位桥接。 */
public final class CuriosCorpseBridge implements CorpseAccessoryBridge {
    private final Map<UUID, List<Tuple<Predicate<ItemStack>, ICurio.DropRule>>> dropRuleOverrides =
            new HashMap<>();

    public CuriosCorpseBridge() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onDropRules);
    }

    @Override
    public String providerId() {
        return CorpseAccessoryEntry.CURIOS;
    }

    @Override
    public List<CorpseAccessoryEntry> snapshot(net.minecraft.server.level.ServerPlayer player) {
        List<CorpseAccessoryEntry> entries = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.getCurios().forEach(
                (slotName, stacksHandler) -> {
                    snapshotHandler(entries, slotName, false, stacksHandler.getStacks());
                    snapshotHandler(entries, slotName, true, stacksHandler.getCosmeticStacks());
                }));
        return entries;
    }

    private static void snapshotHandler(List<CorpseAccessoryEntry> entries,
                                        String slotName,
                                        boolean cosmetic,
                                        IDynamicStackHandler stacks) {
        for (int index = 0; index < stacks.getSlots(); index++) {
            ItemStack stack = stacks.getStackInSlot(index);
            if (!stack.isEmpty()) {
                entries.add(new CorpseAccessoryEntry(
                        CorpseAccessoryEntry.CURIOS, slotName, index, cosmetic, stack));
            }
        }
    }

    @Override
    public void reconcile(net.minecraft.server.level.ServerPlayer player,
                          LivingDropsEvent event,
                          List<CorpseAccessoryEntry> entries) {
        List<Tuple<Predicate<ItemStack>, ICurio.DropRule>> overrides =
                dropRuleOverrides.remove(player.getUUID());
        if (overrides == null) {
            overrides = List.of();
        }

        for (CorpseAccessoryEntry entry : entries) {
            if (!providerId().equals(entry.provider()) || entry.isEmpty()) {
                continue;
            }

            ICurioStacksHandler slotHandler = CuriosApi.getCuriosInventory(player)
                    .flatMap(handler -> handler.getStacksHandler(entry.slotName()))
                    .orElse(null);
            IDynamicStackHandler stacks = slotHandler == null
                    ? null
                    : entry.cosmetic() ? slotHandler.getCosmeticStacks() : slotHandler.getStacks();
            ItemStack current = stacks != null && entry.slotIndex() < stacks.getSlots()
                    ? stacks.getStackInSlot(entry.slotIndex())
                    : ItemStack.EMPTY;

            if (current.isEmpty()) {
                ItemStack dropped = CorpseAccessoryBridge.takeMatchingDrop(event.getDrops(), entry.stack());
                entry.setStack(dropped);
                continue;
            }

            ICurio.DropRule rule = resolveDropRule(
                    player, event, entry, current, slotHandler, overrides);
            if (rule == ICurio.DropRule.ALWAYS_KEEP) {
                entry.setStack(ItemStack.EMPTY);
                continue;
            }
            if (rule == ICurio.DropRule.DESTROY
                    || EnchantmentHelper.has(current, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                stacks.setStackInSlot(entry.slotIndex(), ItemStack.EMPTY);
                entry.setStack(ItemStack.EMPTY);
                continue;
            }

            entry.setStack(current.copy());
            stacks.setStackInSlot(entry.slotIndex(), ItemStack.EMPTY);
        }
    }

    private static ICurio.DropRule resolveDropRule(
            net.minecraft.server.level.ServerPlayer player,
            LivingDropsEvent event,
            CorpseAccessoryEntry entry,
            ItemStack stack,
            ICurioStacksHandler slotHandler,
            List<Tuple<Predicate<ItemStack>, ICurio.DropRule>> overrides) {
        ICurio.DropRule result = null;
        for (Tuple<Predicate<ItemStack>, ICurio.DropRule> override : overrides) {
            if (override.getA().test(stack)) {
                result = override.getB();
            }
        }

        boolean render = slotHandler.getRenders().size() > entry.slotIndex()
                && slotHandler.getRenders().get(entry.slotIndex());
        SlotContext context = new SlotContext(
                entry.slotName(), player, entry.slotIndex(), entry.cosmetic(), render);
        if (result == null) {
            result = CuriosApi.getCurio(stack)
                    .map(curio -> curio.getDropRule(context, event.getSource(), event.isRecentlyHit()))
                    .orElse(ICurio.DropRule.DEFAULT);
        }
        if (result == ICurio.DropRule.DEFAULT) {
            result = CuriosApi.getSlot(entry.slotName(), player.level())
                    .map(slot -> slot.getDropRule())
                    .orElse(ICurio.DropRule.DEFAULT);
        }
        if (result == ICurio.DropRule.DEFAULT) {
            CuriosConfig.KeepCurios config = CuriosConfig.SERVER.keepCurios.get();
            if (config == CuriosConfig.KeepCurios.ON) {
                return ICurio.DropRule.ALWAYS_KEEP;
            }
            if (config == CuriosConfig.KeepCurios.OFF) {
                return ICurio.DropRule.ALWAYS_DROP;
            }
        }
        return result;
    }

    @Override
    public boolean restore(Player player, CorpseAccessoryEntry entry) {
        ICurioStacksHandler slotHandler = CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.getStacksHandler(entry.slotName()))
                .orElse(null);
        if (slotHandler == null) {
            return false;
        }
        IDynamicStackHandler stacks = entry.cosmetic()
                ? slotHandler.getCosmeticStacks()
                : slotHandler.getStacks();
        if (entry.slotIndex() < 0 || entry.slotIndex() >= stacks.getSlots()
                || !stacks.getStackInSlot(entry.slotIndex()).isEmpty()) {
            return false;
        }
        stacks.setStackInSlot(entry.slotIndex(), entry.stack().copy());
        entry.setStack(ItemStack.EMPTY);
        return true;
    }

    @Override
    public void rollbackRestore(Player player, CorpseAccessoryEntry restoredEntry) {
        ICurioStacksHandler slotHandler = CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.getStacksHandler(restoredEntry.slotName()))
                .orElse(null);
        if (slotHandler == null) {
            return;
        }
        IDynamicStackHandler stacks = restoredEntry.cosmetic()
                ? slotHandler.getCosmeticStacks()
                : slotHandler.getStacks();
        if (restoredEntry.slotIndex() >= 0 && restoredEntry.slotIndex() < stacks.getSlots()
                && ItemStack.matches(stacks.getStackInSlot(restoredEntry.slotIndex()), restoredEntry.stack())) {
            stacks.setStackInSlot(restoredEntry.slotIndex(), ItemStack.EMPTY);
        }
    }

    private void onDropRules(DropRulesEvent event) {
        List<Tuple<Predicate<ItemStack>, ICurio.DropRule>> copy = new ArrayList<>(event.getOverrides());
        dropRuleOverrides.put(event.getEntity().getUUID(), copy);
    }
}
