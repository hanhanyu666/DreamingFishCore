package com.hhy.dreamingfishcore.loot;

import com.hhy.dreamingfishcore.gameplay.blueprint_system.BlueprintConfig;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.PlayerBlueprintData;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * 宝箱掉落蓝图。
 *
 * <p>只作用于**容器战利品**：判定依据是上下文里带不带 {@code BLOCK_ENTITY}
 * （箱子、木桶之类在开箱时才把战利品填进去）。生物掉落与方块破坏掉落都不带这个参数，
 * 所以不会被这条规则误伤。</p>
 *
 * <p>概率与抽取池都来自 {@code config/dreamingfishcore/blueprint.json}
 * （{@code chestDropPercent} 默认 25%，白/黑名单与丧尸渠道共用一套）。这里不再自带
 * {@code chance} 字段，避免概率散落在两个地方。</p>
 *
 * <p>宝箱战利品拿不到击杀者，所以无法按玩家去重；丧尸那条渠道走
 * {@code BlueprintDropHandler}，是能去重的。</p>
 */
public class BlueprintLootModifier extends LootModifier {

    public static final MapCodec<BlueprintLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, BlueprintLootModifier::new));

    public BlueprintLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        double dropPercent = BlueprintConfig.current().getChestDropPercent();
        if (dropPercent <= 0.0D) {
            return generatedLoot;
        }

        // 只有「容器战利品」会带 BLOCK_ENTITY；生物掉落带 THIS_ENTITY、方块破坏带 BLOCK_STATE。
        if (context.getParamOrNull(LootContextParams.BLOCK_ENTITY) == null) {
            return generatedLoot;
        }

        if (context.getRandom().nextDouble() * 100.0D >= dropPercent) {
            return generatedLoot;
        }

        ItemStack blueprint = PlayerBlueprintData.createRandomBlueprint(context.getRandom());
        if (!blueprint.isEmpty()) {
            generatedLoot.add(blueprint);
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends LootModifier> codec() {
        return CODEC;
    }
}
