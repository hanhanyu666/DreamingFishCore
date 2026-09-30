package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.input.KeybindHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

    /** Draws into the caller's managed HUD pass. */
    public static void renderBatched(GuiGraphics guiGraphics, Minecraft mc) {
        if (!shouldRenderHud(mc)) {
            lastFrameMillis = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        float deltaSeconds = lastFrameMillis == 0L ? 0.0F : Math.min(0.1F, (now - lastFrameMillis) / 1000.0F);
        lastFrameMillis = now;

        boolean detailHeld = KeybindHandler.GUIDANCE_SCROLL_KEY.isDown();
        detailProgress = HudPalette.approach(detailProgress, detailHeld ? 1.0F : 0.0F,
                detailHeld ? DETAIL_OPEN_SPEED : DETAIL_CLOSE_SPEED, deltaSeconds);
        float detail = HudPalette.easeOutCubic(detailProgress);

        Player player = mc.player;
        HudVitals vitals = HudVitals.capture(player);
        HudActionBars.render(guiGraphics, mc, vitals, detailHeld, detail, now, deltaSeconds);
        HudVitalsPanel.render(guiGraphics, mc, player, vitals, detailHeld, detail, now, deltaSeconds);
        // This explicit boundary submits the shared panel/text batch before
        // the icon-atlas mesh. The coordinator puts both task cards in the
        // same batch ahead of this renderer.
        HudIconBatch.flush(guiGraphics);
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
