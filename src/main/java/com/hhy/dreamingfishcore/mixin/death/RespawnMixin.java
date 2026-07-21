package com.hhy.dreamingfishcore.mixin.death;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.DeathItemStorage;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.PendingDeathData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Respawn Mixin - 处理死亡物品存储
 * 当玩家存在待处理死亡记录时，刷新物品快照用于后续“普通复活”掉落。
 * 注意：keepInventory 已被强制开启，这里只保存副本，不干预原版物品栏。
 */
@Mixin(LivingEntity.class)
public class RespawnMixin {

    /**
     * 注入 dropAllDeathLoot 方法
     * 如果玩家存在待处理死亡记录，则在原版死亡掉落阶段刷新物品副本。
     */
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void dreamingFishCore$onDropAllDeathLoot(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;

        // 只处理玩家
        if (!(entity instanceof Player player)) {
            return;
        }

        // 检查是否有待处理的死亡（等待玩家选择）
        if (PendingDeathData.hasPendingRecord(player)) {
            // 在原版掉落阶段刷新最终快照；快照本身保存在玩家持久化 NBT 中。
            DeathItemStorage.storePlayerInventory(player);

            DreamingFishCore.LOGGER.info("玩家 {} 的物品副本已存储（keepInventory已开启，物品原样保留）", player.getScoreboardName());
        }
    }
}
