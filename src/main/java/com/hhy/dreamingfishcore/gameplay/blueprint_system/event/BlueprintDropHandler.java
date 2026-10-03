package com.hhy.dreamingfishcore.gameplay.blueprint_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.BlueprintConfig;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.PlayerBlueprintData;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ModZombieSpecies;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * 合成蓝图的丧尸掉落。
 *
 * <p>只有**模组自定义的丧尸**（实现 {@link ModZombieSpecies}：围攻僵尸、射手僵尸）会掉蓝图，
 * 并且必须是被玩家击杀——「按玩家去重」需要知道击杀者是谁。掉落走 {@code LivingDropsEvent}
 * 而不是战利品修饰器，因为修饰器拿不到「谁是击杀者」这个上下文。</p>
 *
 * <p>抽取时排除击杀者已经学会的蓝图：已经学过的东西再掉一张只会占背包。</p>
 *
 * <p>概率来自 {@code config/dreamingfishcore/blueprint.json} 的 {@code siegeZombieDropPercent}
 * （默认 5%）。宝箱那条渠道见 {@code BlueprintLootModifier}。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class BlueprintDropHandler {

    private BlueprintDropHandler() {
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity killed = event.getEntity();
        if (killed.level().isClientSide() || !(killed.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(killed instanceof ModZombieSpecies)) {
            return;
        }

        double dropPercent = BlueprintConfig.current().getSiegeZombieDropPercent();
        if (dropPercent <= 0.0D) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }

        RandomSource random = serverLevel.getRandom();
        if (random.nextDouble() * 100.0D >= dropPercent) {
            return;
        }

        ItemStack blueprint = PlayerBlueprintData.createRandomBlueprintFor(killer, random);
        if (blueprint.isEmpty()) {
            // 池为空，或这名玩家已经把池里的蓝图全学完了。
            return;
        }

        event.getDrops().add(new ItemEntity(
                serverLevel,
                killed.getX(),
                killed.getY(),
                killed.getZ(),
                blueprint));
    }
}
