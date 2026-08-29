package com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 客户端事件处理器：处理UI状态恢复（如重生后）
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public class ServerScreenUI_ClientEventHandler {
    private static int tickCounter = 0;

    /**
     * 每秒检查一次UI状态，确保SHOW_UI状态与实际显示一致
     * 处理死亡重生后UI消失的问题
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // EconomySystem 等外部子界面可能直接关闭到游戏；下一 tick 立即返回梦屿终端。
        Minecraft current = Minecraft.getInstance();
        if (current.player != null && ServerScreenUI.isSubScreenActive() && current.screen == null) {
            ServerScreenUI.onSubScreenClosed();
            ServerScreenUI.setReturningFromSubScreen(true);
            current.setScreen(new ServerScreenUI_Screen());
            return;
        }
        // 每20 tick（1秒）检查一次
        tickCounter++;
        if (tickCounter < 20) return;
        tickCounter = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 检查是否在死亡界面或重生界面，如果是则不恢复UI
        if (mc.screen instanceof DeathScreen) {
            // 在死亡/重生界面，不显示TaskUI
            if (ServerScreenUI.isShowUI()) {
                DreamingFishCore.LOGGER.info("在死亡界面，保持UI开启状态但不显示");
            }
            return;
        }

        // 如果UI应该显示但没有显示（例如重生后）
        // 但如果子屏幕正在显示，则不需要恢复
        if (ServerScreenUI.isShowUI() && !(mc.screen instanceof ServerScreenUI_Screen) && !ServerScreenUI.isSubScreenActive()) {
            DreamingFishCore.LOGGER.info("检测到UI状态不一致，重新打开TaskUI");
            mc.setScreen(new ServerScreenUI_Screen());
        }
        // 如果UI不应该显示但还在显示
        else if (!ServerScreenUI.isShowUI() && mc.screen instanceof ServerScreenUI_Screen) {
            DreamingFishCore.LOGGER.info("检测到UI状态不一致，关闭TaskUI");
            mc.setScreen(null);
        }
    }
}
