package com.hhy.dreamingfishcore.gameplay.zombie_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.adamant.AdamantZombieEntities;
import com.hhy.dreamingfishcore.gameplay.zombie_system.charred.CharredZombieEntities;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * 焦尸与金刚僵尸的生成位置规则。
 *
 * <p>这两个实体原先**故意没有注册生成位置**（注释写着"不接入刷怪池，注册了不会被任何逻辑读到"）。
 * 现在它们要进自然生成，生成位置规则就是必需的一环：没有它，引擎在判定生成位置时一律不通过，
 * 光把权重加进刷怪池也不会真的刷出来。</p>
 *
 * <p>规则用原版敌对生物那一套（{@code Monster.checkMonsterSpawnRules}）：跟普通僵尸一样，
 * 要求光照与地面条件合适，不再额外限制高度或时间——服主要的就是"小概率自然遇到"。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ZombieSpawnPlacements {

    private ZombieSpawnPlacements() {
    }

    @SubscribeEvent
    public static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        register(event, CharredZombieEntities.CHARRED_ZOMBIE.get());
        register(event, AdamantZombieEntities.ADAMANT_ZOMBIE.get());
    }

    private static <T extends net.minecraft.world.entity.monster.Monster> void register(RegisterSpawnPlacementsEvent event, EntityType<T> type) {
        event.register(
                type,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, level, spawnType, pos, random) ->
                        net.minecraft.world.entity.monster.Monster
                                .checkMonsterSpawnRules(entityType, level, spawnType, pos, random),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}