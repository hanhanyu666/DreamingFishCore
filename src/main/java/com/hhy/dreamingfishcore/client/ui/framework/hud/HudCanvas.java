package com.hhy.dreamingfishcore.client.ui.framework.hud;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * HUD 的统一绘制通道：每个时机只开一次画布，按顺序让可见的 {@link HudLayer} 绘制。
 * 某个区域出错只会跳过它本身，不影响其他区域。
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class HudCanvas {
    private static final List<HudLayer> LAYERS = new ArrayList<>();
    private static final List<HudLayer> VISIBLE = new ArrayList<>();
    private static final UiCanvas CANVAS = new UiCanvas();
    private static final UiCanvas ADHOC = new UiCanvas();
    private static final HudFrame FRAME = new HudFrame();
    private static long lastFrameMillis;
    /** 正在绘制中的画布；嵌套调用 {@link #paintNow} 时直接复用。 */
    private static UiCanvas current;

    private HudCanvas() {
    }

    /** 注册一块 HUD 区域（客户端初始化时调用）。 */
    public static synchronized void register(HudLayer layer) {
        if (!LAYERS.contains(layer)) {
            LAYERS.add(layer);
            LAYERS.sort(Comparator.comparingInt(HudLayer::order));
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        paint(HudLayer.Pass.MAIN, event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(true));
    }

    /** 在原版 HUD 的最末尾调用（见 GuiNotificationOverlayMixin）。 */
    public static void paintOverlay(GuiGraphics graphics) {
        paint(HudLayer.Pass.OVERLAY, graphics, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
    }

    /** 不属于固定区域的零散绘制（如聊天窗）也可以借用同一套画布。 */
    public static void paintNow(GuiGraphics graphics, Consumer<UiCanvas> painter) {
        if (current != null) {
            painter.accept(current);
            return;
        }
        ADHOC.begin(graphics);
        current = ADHOC;
        try {
            painter.accept(ADHOC);
        } finally {
            current = null;
            ADHOC.end();
        }
    }

    private static void paint(HudLayer.Pass pass, GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        VISIBLE.clear();
        for (HudLayer layer : LAYERS) {
            if (layer.pass() == pass && safeVisible(layer, minecraft)) {
                VISIBLE.add(layer);
            }
        }
        if (VISIBLE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        float delta = lastFrameMillis == 0L ? 0.0F : Math.min(0.1F, Math.max(0.0F, (now - lastFrameMillis) / 1000.0F));
        if (pass == HudLayer.Pass.MAIN) {
            lastFrameMillis = now;
        }
        FRAME.set(CANVAS, graphics, minecraft, now, delta, partialTick);
        CANVAS.begin(graphics);
        current = CANVAS;
        try {
            for (HudLayer layer : VISIBLE) {
                try {
                    layer.paint(FRAME);
                } catch (RuntimeException exception) {
                    DreamingFishCore.LOGGER.error("HUD 区域绘制失败: {}", layer.getClass().getName(), exception);
                    LAYERS.remove(layer);
                }
            }
        } finally {
            current = null;
            CANVAS.end();
        }
    }

    private static boolean safeVisible(HudLayer layer, Minecraft minecraft) {
        try {
            return layer.visible(minecraft);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error("HUD 区域状态检查失败: {}", layer.getClass().getName(), exception);
            return false;
        }
    }
}
