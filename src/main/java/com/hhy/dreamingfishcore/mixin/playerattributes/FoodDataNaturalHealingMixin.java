package com.hhy.dreamingfishcore.mixin.playerattributes;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.first_stage.FirstStageSurvivalManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 只拦截 FoodData 内部的饱食度自然回血。
 * 其他来源调用 Player#heal（药水、金苹果、状态效果、医疗物品）不会经过这里。
 */
@Mixin(FoodData.class)
public abstract class FoodDataNaturalHealingMixin {
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;isHurt()Z"))
    private boolean dreamingfishcore$canUseFoodNaturalHealing(Player player) {
        return FirstStageSurvivalManager.canFoodNaturalHealingRun(player);
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void dreamingfishcore$limitFoodNaturalHealing(Player player, float amount) {
        float allowed = FirstStageSurvivalManager.limitFoodNaturalHealing(player, amount);
        if (allowed > 0.0F) {
            player.heal(allowed);
        }
    }
}
