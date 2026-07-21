package com.hhy.dreamingfishcore.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.item.client.model.CustomRendererBakedModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSetup.class);

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 这里可以放一些客户端设置
    }

    // 修改模型烘焙结果
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        LOGGER.info("[Blueprint] ModifyBakingResult event fired!");

        try {
            // 获取蓝图模型的资源位置
            ModelResourceLocation blueprintModel = new ModelResourceLocation(
                    new ResourceLocation(DreamingFishCore.MODID, "blueprint"),
                    "inventory"
            );

            // 获取当前模型
            BakedModel originalModel = event.getModels().get(blueprintModel);

            if (originalModel != null) {
                // 创建包装器模型，启用自定义渲染
                CustomRendererBakedModel customModel = new CustomRendererBakedModel(originalModel);

                // 使用事件提供的 Map 来替换模型
                event.getModels().put(blueprintModel, customModel);
                LOGGER.info("[Blueprint] Successfully replaced blueprint model in ModifyBakingResult!");
            } else {
                LOGGER.error("[Blueprint] Could not find blueprint model!");
            }
        } catch (Exception e) {
            LOGGER.error("[Blueprint] Failed to modify blueprint model in ModifyBakingResult!", e);
        }
    }
}
