package com.hhy.dreamingfishcore.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 风暴天的客户端表现：屏幕雨滴 + 视野收缩。
 *
 * <p><b>不需要任何网络包</b>：风暴天在服务端就是把主世界天气切成雷雨，
 * 客户端本来就知道"正在打雷下雨"，所以这里直接用本地天气状态判断，不动协议版本。</p>
 *
 * <p>判定条件都要求"能看见天"：躲在屋里的人不该出现屏幕雨滴或视野收缩
 * （否则玩家会觉得自己在屋里也被淋，很出戏）。旁观模式也跳过。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class StormClientEffect {

    /** 视野收缩比例：0.92 = 缩小 8%，够明显但不至于让人晕。 */
    private static final double FOV_SCALE = 0.92D;
    /** 屏幕上的雨滴条数。 */
    private static final int RAIN_DROPS = 110;
    /** 单条雨滴的长度（像素）。 */
    private static final int DROP_LENGTH = 12;
    /** 雨滴颜色（半透明冷色）。 */
    private static final int DROP_COLOR = 0x8C9FC3E0;

    private StormClientEffect() {
    }

    /** 视野收缩：只影响风暴、且玩家暴露在天空下时。 */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!isStormVisible()) {
            return;
        }
        event.setFOV(event.getFOV() * FOV_SCALE);
    }

    /** 屏幕雨滴：用游戏时间 + 下标驱动的伪随机，每帧位置不同但不需要维护状态。 */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!isStormVisible()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        long time = minecraft.level.getGameTime();

        for (int index = 0; index < RAIN_DROPS; index++) {
            long seed = time * 31L + index * 977L;
            int x = (int) Math.floorMod(seed * 2654435761L, (long) width);
            int speed = 18 + (int) Math.floorMod(seed, 10L);
            int y = (int) Math.floorMod(time * speed + index * 37L, (long) (height + DROP_LENGTH))
                    - DROP_LENGTH;
            graphics.fill(x, y, x + 1, y + DROP_LENGTH, DROP_COLOR);
        }
    }

    /** 是否应该表现出风暴（打雷下雨 + 能看见天 + 非旁观 + 没隐藏 HUD）。 */
    static boolean isStormVisible() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return false;
        }
        if (minecraft.player.isSpectator()) {
            return false;
        }
        if (!minecraft.level.isRaining() || !minecraft.level.isThundering()) {
            return false;
        }
        return minecraft.level.canSeeSky(minecraft.player.blockPosition());
    }
}
