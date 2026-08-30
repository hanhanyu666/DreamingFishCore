package com.hhy.dreamingfishcore.mixin.death;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.DeathCorpseManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自定义尸体流程必须在原版死亡掉落规则开关下都能捕获物品。
 * 这里只改写 Player.dropEquipment 内部的那一次规则读取，让正在捕获的玩家物品进入真实的
 * LivingDropsEvent；未被自定义流程接管的实体仍遵循世界自身的 keepInventory 规则。
 */
@Mixin(Player.class)
public abstract class RespawnMixin {

    @Redirect(
            method = "dropEquipment",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean dreamingFishCore$dropInventoryIntoCorpse(GameRules gameRules,
                                                              GameRules.Key<GameRules.BooleanValue> rule) {
        Player player = (Player) (Object) this;
        if (rule == GameRules.RULE_KEEPINVENTORY && DeathCorpseManager.isCapturing(player)) {
            return false;
        }
        return gameRules.getBoolean(rule);
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void dreamingFishCore$finishCorpseCapture(DamageSource source, CallbackInfo ci) {
        DeathCorpseManager.finishCapture((Player) (Object) this);
    }
}
