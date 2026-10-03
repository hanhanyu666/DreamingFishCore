package com.hhy.dreamingfishcore.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.framework.hud.HudCanvas;
import com.hhy.dreamingfishcore.item.client.model.CustomRendererBakedModel;
import com.hhy.dreamingfishcore.gameplay.npc_system.client.StoryNpcRenderer;
import com.hhy.dreamingfishcore.gameplay.npc_system.entity.StoryNpcEntities;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud.CustomHotbarGUI;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud.CustomStatueGUI;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationRenderer;
import com.hhy.dreamingfishcore.gameplay.task_location_system.client.TaskLocationHudRenderer;
import com.hhy.dreamingfishcore.gameplay.task_location_system.client.TaskLocationReminderHudRenderer;
import com.hhy.dreamingfishcore.server.server_ui_system.client.ServerInformationDisplay;
import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntities;
import com.hhy.dreamingfishcore.gameplay.zombie_system.client.SiegeZombieRenderer;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.DeathCorpseEntities;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.client.DeathCorpseRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public class ClientSetup {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSetup.class);

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 游戏内 HUD 统一走框架画布，每帧一次提交；顺序由各区域的 order 决定
        HudCanvas.register(CustomHotbarGUI.LAYER);
        HudCanvas.register(TaskLocationHudRenderer.LAYER);
        HudCanvas.register(TaskLocationReminderHudRenderer.LAYER);
        HudCanvas.register(CustomStatueGUI.LAYER);
        HudCanvas.register(ServerInformationDisplay.LAYER);
        HudCanvas.register(NotificationRenderer.CENTER_LAYER);
        HudCanvas.register(NotificationRenderer.TOP_LEFT_LAYER);
        HudCanvas.register(com.hhy.dreamingfishcore.gameplay.marker_system.client.render.MarkerRenderer.LAYER);
    }

    @SubscribeEvent
    public static void registerMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        // 研究桌的界面是自绘的（不是 AbstractContainerScreen），但仍然必须实现 MenuAccess：
        // 客户端就是靠这个接口把 containerMenu 指向菜单实例，槽位点击才会发给服务端。
        event.register(
                com.hhy.dreamingfishcore.gameplay.research_system.ResearchTableMenus.RESEARCH_TABLE.get(),
                com.hhy.dreamingfishcore.gameplay.research_system.client.Screen_ResearchTable::new);
    }

    @SubscribeEvent
    public static void registerHudCacheReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            CustomHotbarGUI.invalidateItemRenderCache();
            CustomStatueGUI.invalidateHudIconCache();
            ServerInformationDisplay.invalidateCompactRenderCache();
            NotificationRenderer.invalidateLayoutCaches();
            TaskLocationHudRenderer.invalidateLayoutCache();
            TaskLocationReminderHudRenderer.invalidateLayoutCache();
        });
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(StoryNpcEntities.STORY_NPC.get(), StoryNpcRenderer::new);
        event.registerEntityRenderer(DeathCorpseEntities.DEATH_CORPSE.get(), DeathCorpseRenderer::new);
        event.registerEntityRenderer(SiegeZombieEntities.SIEGE_ZOMBIE.get(), SiegeZombieRenderer::new);
        event.registerEntityRenderer(
                com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieEntities.ARCHER_ZOMBIE.get(),
                com.hhy.dreamingfishcore.gameplay.zombie_system.archer.client.ArcherZombieRenderer::new);
        event.registerEntityRenderer(
                com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieEntities.BONE_SPIKE.get(),
                com.hhy.dreamingfishcore.gameplay.zombie_system.archer.client.BoneSpikeRenderer::new);
        event.registerEntityRenderer(
                com.hhy.dreamingfishcore.gameplay.zombie_system.charred.CharredZombieEntities.CHARRED_ZOMBIE.get(),
                com.hhy.dreamingfishcore.gameplay.zombie_system.charred.client.CharredZombieRenderer::new);
        event.registerEntityRenderer(
                com.hhy.dreamingfishcore.gameplay.zombie_system.adamant.AdamantZombieEntities.ADAMANT_ZOMBIE.get(),
                com.hhy.dreamingfishcore.gameplay.zombie_system.adamant.client.AdamantZombieRenderer::new);
        event.registerEntityRenderer(
                com.hhy.dreamingfishcore.gameplay.water_gun_system.WaterGunEntities.WATER_JET.get(),
                com.hhy.dreamingfishcore.gameplay.water_gun_system.client.WaterJetRenderer::new);
    }

    // 修改模型烘焙结果
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        CustomHotbarGUI.invalidateItemRenderCache();
        LOGGER.info("[Blueprint] ModifyBakingResult event fired!");

        try {
            // 获取蓝图模型的资源位置
            ModelResourceLocation blueprintModel = new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "blueprint"),
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
