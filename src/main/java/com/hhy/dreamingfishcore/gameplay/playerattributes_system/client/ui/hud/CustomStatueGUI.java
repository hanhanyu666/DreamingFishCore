package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.input.KeybindHandler;
import net.minecraft.client.Minecraft;
import com.hhy.dreamingfishcore.client.ui.framework.hud.HudFrame;
import com.hhy.dreamingfishcore.client.ui.framework.hud.HudLayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 生存体征 HUD 的入口：左下角「体征回传」与快捷栏上方的行动条。
 *
 * <p>常态保持安静，只有变化或异常时才浮现读数；按住查看键（与右下角任务展开共用）
 * 时两处同时展开全部数值。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public class CustomStatueGUI {
    /** 体征 HUD 在统一 HUD 画布中的区域：快捷栏与任务卡片之后绘制。 */
    public static final HudLayer LAYER = new HudLayer() {
        @Override
        public int order() {
            return 40;
        }

        @Override
        public boolean visible(Minecraft minecraft) {
            boolean visible = shouldRenderHud(minecraft);
            if (!visible) {
                lastFrameMillis = 0L;
            }
            return visible;
        }

        @Override
        public void paint(HudFrame frame) {
            CustomStatueGUI.paint(frame);
        }
    };

    private static final float DETAIL_OPEN_SPEED = 6.5F;
    private static final float DETAIL_CLOSE_SPEED = 5.0F;

    private static long lastFrameMillis;
    private static float detailProgress;

    public static boolean shouldRenderHud(Minecraft mc) {
        Player player = mc.player;
        return player != null && CustomHotbarGUI.isHudVisibleScreen(mc) && !player.isDeadOrDying()
                && !mc.options.hideGui && !mc.getDebugOverlay().showDebugScreen()
                && !player.isSpectator()
                && (mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.CREATIVE);
    }

    public static void invalidateHudIconCache() {
        HudIconBatch.invalidate();
    }

    private static void paint(HudFrame frame) {
        Minecraft mc = frame.minecraft();
        long now = frame.now();
        float deltaSeconds = lastFrameMillis == 0L ? 0.0F : Math.min(0.1F, (now - lastFrameMillis) / 1000.0F);
        lastFrameMillis = now;

        boolean detailHeld = KeybindHandler.GUIDANCE_SCROLL_KEY.isDown();
        detailProgress = HudPalette.approach(detailProgress, detailHeld ? 1.0F : 0.0F,
                detailHeld ? DETAIL_OPEN_SPEED : DETAIL_CLOSE_SPEED, deltaSeconds);
        float detail = HudPalette.easeOutCubic(detailProgress);

        Player player = mc.player;
        HudVitals vitals = HudVitals.capture(player);
        HudActionBars.render(frame.canvas(), mc, vitals, detailHeld, detail, now, deltaSeconds);
        HudVitalsPanel.render(frame.canvas(), mc, player, vitals, detailHeld, detail, now, deltaSeconds);
    }

    //拦截原版UI：在UI渲染前取消原版血量的渲染事件
    @SubscribeEvent
    public static void interceptVanillaHealthAndFoodUI(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) {
            return;
        }
        //拦截原版玩家血量UI
        if (event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) {
            event.setCanceled(true);
        }
    }
}
