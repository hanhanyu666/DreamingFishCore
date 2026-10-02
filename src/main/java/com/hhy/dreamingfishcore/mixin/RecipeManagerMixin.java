package com.hhy.dreamingfishcore.mixin;

import com.google.gson.JsonElement;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.PlayerBlueprintData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 把「工作台配方 → 输出物品」交给蓝图系统。
 *
 * <p>只做收集：哪些物品需要蓝图、哪些免蓝图，全部由
 * {@code BlueprintConfig} 在 {@code PlayerBlueprintData} 构建抽取池时决定。
 * 这里**不应该**有过滤逻辑——早期版本会在这里把命中的物品塞进「默认放行」集合，
 * 结果是配方加载悄悄改了全局状态，而且默认放行名单写死在代码里、服主改不了。</p>
 */
@Mixin(RecipeManager.class)
public class RecipeManagerMixin {

    @Shadow
    private Map<ResourceLocation, RecipeHolder<?>> byName;

    /** 配方加载开始前清空索引（换数据包 / 重载都会走这里）。 */
    @Inject(method = "apply", at = @At("HEAD"))
    private void dreamingfishcore$clearWorkbenchRecipes(Map<ResourceLocation, JsonElement> recipeList,
                                                        ResourceManager resourceManager,
                                                        ProfilerFiller profiler,
                                                        CallbackInfo ci) {
        PlayerBlueprintData.clearWorkbenchRecipes();
    }

    /** 配方加载完成后收集工作台配方，并打一遍抽取池诊断。 */
    @Inject(method = "apply", at = @At("RETURN"))
    private void dreamingfishcore$collectWorkbenchRecipes(Map<ResourceLocation, JsonElement> recipeList,
                                                          ResourceManager resourceManager,
                                                          ProfilerFiller profiler,
                                                          CallbackInfo ci) {
        for (Map.Entry<ResourceLocation, RecipeHolder<?>> entry : this.byName.entrySet()) {
            Recipe<?> recipe = entry.getValue().value();
            if (recipe.getType() != RecipeType.CRAFTING) {
                continue;
            }
            // 特殊合成（盔甲染色、烟花、地图复制……）的输出与输入同物，放进蓝图池没有意义。
            if (entry.getKey().getPath().contains("crafting_special_")) {
                continue;
            }
            PlayerBlueprintData.addWorkbenchRecipe(entry.getKey(), recipe);
        }
        PlayerBlueprintData.logPoolDiagnostics();
    }
}
