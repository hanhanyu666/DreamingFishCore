package com.hhy.dreamingfishcore.mixin.death;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.DeathCorpseManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让已经进入尸体捕获流程的旁观模式玩家也经过原版死亡掉落事件。
 *
 * ServerPlayer 自己覆盖了 die()，不会调用 Player.die()；因此 RespawnMixin
 * 中针对 Player.die() 的清理不能覆盖服务端玩家。这里补上服务端实际调用路径。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDeathMixin {

    /**
     * 原版 ServerPlayer.die() 会在旁观模式跳过 dropAllDeathLoot。
     * 只有本模组已经建立捕获上下文时才放行掉落，其他旁观死亡保持原版行为。
     */
    @Redirect(
            method = "die",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;isSpectator()Z"))
    private boolean dreamingFishCore$allowCapturedSpectatorDrops(ServerPlayer player) {
        if (DeathCorpseManager.isCapturing(player)) {
            return false;
        }
        return player.isSpectator();
    }

    /**
     * ServerPlayer.die() 不会调用 Player.die()，所以在实际服务端入口清理
     * 没有进入 LivingDropsEvent 的捕获上下文（例如事件被取消或掉落异常）。
     */
    @Inject(method = "die", at = @At("RETURN"))
    private void dreamingFishCore$finishCorpseCapture(DamageSource source, CallbackInfo ci) {
        DeathCorpseManager.finishCapture((ServerPlayer) (Object) this);
    }
}
