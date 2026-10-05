package com.hhy.dreamingfishcore.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.block.DreamingFishCore_Blocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 方块渲染层注册（客户端）。
 *
 * <p>作物这类"贴图有大片透明区"的方块必须走 cutout 渲染层：默认的 solid 会把
 * alpha=0 的像素**画成不透明黑色**——游戏里看到的就是一株纯黑的植物。</p>
 *
 * <p>放在独立类里而不是塞进 ClientSetup，是为了让这条注册自解释、不依赖其它初始化顺序。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class RaidCropRenderLayers {

    private RaidCropRenderLayers() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(
                DreamingFishCore_Blocks.HERB_CROP.get(), RenderType.cutout()));
    }
}