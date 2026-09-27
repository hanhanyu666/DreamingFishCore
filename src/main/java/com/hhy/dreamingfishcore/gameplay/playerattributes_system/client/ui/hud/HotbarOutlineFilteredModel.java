package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

/** Removes Enchantment Glows' subpixel outline shell only in our hotbar. */
final class HotbarOutlineFilteredModel extends BakedModelWrapper<BakedModel> {
    private static final ResourceLocation OUTLINE_SPRITE =
            ResourceLocation.withDefaultNamespace("item/enchant_glint");

    private HotbarOutlineFilteredModel(BakedModel model) {
        super(model);
    }

    static BakedModel forHotbar(BakedModel model) {
        return model.isCustomRenderer() || model instanceof HotbarOutlineFilteredModel
                ? model : new HotbarOutlineFilteredModel(model);
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
        BakedModel transformed = originalModel.applyTransform(context, pose, leftHand);
        return transformed == originalModel ? this : forHotbar(transformed);
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        List<BakedModel> passes = originalModel.getRenderPasses(stack, fabulous);
        List<BakedModel> filtered = new ArrayList<>(passes.size());
        for (BakedModel pass : passes) {
            filtered.add(pass == originalModel ? this : forHotbar(pass));
        }
        return filtered;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return withoutOutline(originalModel.getQuads(state, side, random));
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                                   ModelData data, RenderType renderType) {
        return withoutOutline(originalModel.getQuads(state, side, random, data, renderType));
    }

    private static List<BakedQuad> withoutOutline(List<BakedQuad> quads) {
        // Match the pack's dedicated shell texture, not vanilla enchanted-glint
        // render types or the weapon's body texture. Unrelated packs pass through.
        List<BakedQuad> filtered = null;
        for (int index = 0; index < quads.size(); index++) {
            BakedQuad quad = quads.get(index);
            if (OUTLINE_SPRITE.equals(quad.getSprite().contents().name())) {
                if (filtered == null) {
                    filtered = new ArrayList<>(quads.size());
                    filtered.addAll(quads.subList(0, index));
                }
            } else if (filtered != null) {
                filtered.add(quad);
            }
        }
        return filtered == null ? quads : filtered;
    }
}
