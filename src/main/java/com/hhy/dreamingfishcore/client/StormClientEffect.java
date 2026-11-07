package com.hhy.dreamingfishcore.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule;
import com.hhy.dreamingfishcore.gameplay.disaster_system.client.DisasterClientCache;
import com.hhy.dreamingfishcore.gameplay.disaster_system.network.Packet_DisasterSnapshotResponse;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 风暴天 / 沙尘暴的客户端表现：屏幕雨滴（或沙尘）+ 视野收缩。
 *
 * <p><b>判断依据是"今天的日程"，不是"现在有没有下雨"</b>：早先靠 `level.isRaining()` 判断，
 * 结果只要天气被清掉、或者当天没切成雷雨，效果就整个不显示（服主实测报"没看到雨滴"）。
 * 现在服务端在**跨天与登录时**把天灾快照推给客户端，这里查 {@link DisasterClientCache}：
 * 今天有 {@code STORM_DAY} 就下雨滴，有 {@code SANDSTORM} 就下沙尘。</p>
 *
 * <p>仍然要求**能看见天**：躲在屋里的人不该屏幕下雨（与风力、屏幕表现同一套"暴露"口径）。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class StormClientEffect {

    /** 风暴天的视野收缩（0.92 = 缩小 8%）。 */
    private static final double STORM_FOV_SCALE = 0.92D;
    /** 沙尘暴的视野收缩更狠一点（0.88 = 缩小 12%）。 */
    private static final double SANDSTORM_FOV_SCALE = 0.88D;
    /** 水珠数量（少而大，像贴着镜头的雨点）。 */
    private static final int WET_DROPS = 46;
    /** 每颗水珠向下淌的长度范围（像素）。 */
    private static final int WET_MIN_TRAIL = 4;
    private static final int WET_MAX_TRAIL = 16;
    /** 水珠主体：很淡的冷白；高光更亮一点；拖尾几乎透明。 */
    private static final int WET_HEAD_COLOR = 0x40E6F2FF;
    private static final int WET_HEAD_CORE = 0x6EFFFFFF;
    private static final int WET_TRAIL_COLOR = 0x16C8DCEE;
    /** 失焦的大水珠（贴死在镜头上不动的模糊光斑）。 */
    private static final int WET_BLUR_BLOBS = 12;
    private static final int WET_BLUR_COLOR = 0x12E8F4FF;
    /** 沙尘：短、密、黄褐，横向漂移明显。 */
    private static final int DUST_MOTES = 70;
    private static final int DUST_MIN_LENGTH = 4;
    private static final int DUST_MAX_LENGTH = 9;
    private static final int DUST_TOP_COLOR = 0x00C9A96B;
    private static final int DUST_BOTTOM_COLOR = 0x77C9A96B;

    private StormClientEffect() {
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!isExposed()) {
            return;
        }
        if (stormToday()) {
            event.setFOV(event.getFOV() * STORM_FOV_SCALE);
        } else if (sandstormToday()) {
            event.setFOV(event.getFOV() * SANDSTORM_FOV_SCALE);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!isExposed()) {
            return;
        }
        boolean storm = stormToday();
        boolean sandstorm = !storm && sandstormToday();
        if (!storm && !sandstorm) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        long time = minecraft.level.getGameTime();
        if (storm) {
            drawWetGlass(graphics, width, height, time);
            return;
        }
        // 沙尘暴：横飘的短颗粒
        for (int index = 0; index < DUST_MOTES; index++) {
            long seed = time * 31L + index * 977L;
            int x = (int) Math.floorMod(seed * 2654435761L, (long) width);
            int length = DUST_MIN_LENGTH + (int) Math.floorMod(seed >> 7, (long) (DUST_MAX_LENGTH - DUST_MIN_LENGTH + 1));
            int speed = 22 + (int) Math.floorMod(seed >> 11, 14L);
            int y = (int) Math.floorMod(time * speed + index * 53L, (long) (height + length)) - length;
            int drift = (int) Math.round(Math.sin((time + index * 7L) / 18.0D) * 7.0D) - 3;
            graphics.fillGradient(x + drift, y, x + 1 + drift, y + length, DUST_TOP_COLOR, DUST_BOTTOM_COLOR);
        }
    }

    /** 今天是不是风暴天（来自服务端推的日程快照）。 */
    static boolean stormToday() {
        return todayHas(DisasterSchedule.EventType.STORM_DAY);
    }

    /** 今天是不是沙尘暴。 */
    static boolean sandstormToday() {
        return todayHas(DisasterSchedule.EventType.SANDSTORM);
    }

    private static boolean todayHas(DisasterSchedule.EventType type) {
        var entry = todayEntry();
        return entry != null && entry.events().contains(type);
    }

    /**
     * 画"被打湿的镜头"：贴在镜头上的水珠 + 缓慢向下淌出的拖尾 + 高光，再加几团失焦光斑。
     *
     * <p>不是天上的雨丝——玩家要的是"我正淋着雨、镜头糊了"的观感。水珠横坐标固定，
     * 只有纵坐标随时间缓慢下滑，所以看起来是"挂在玻璃上慢慢淌"，而不是一片往下掉的线。</p>
     */
    private static void drawWetGlass(net.minecraft.client.gui.GuiGraphics graphics,
                                     int width, int height, long time) {
        for (int index = 0; index < WET_DROPS; index++) {
            long seed = index * 977L;
            int x = (int) Math.floorMod(seed * 2654435761L, (long) width);
            int trail = WET_MIN_TRAIL
                    + (int) Math.floorMod(seed >> 7, (long) (WET_MAX_TRAIL - WET_MIN_TRAIL + 1));
            // 下滑速度 3~9 像素/秒（time 是 tick，1 秒 20 tick）
            int pixelsPerSecond = 3 + (int) Math.floorMod(seed >> 11, 7L);
            int y = (int) Math.floorMod(time * pixelsPerSecond / 20L + index * 71L,
                    (long) (height + trail)) - trail;
            // 拖尾：上端几乎透明 → 下端略实，像刚淌过的水痕
            graphics.fillGradient(x, y - trail, x + 1, y, 0x00C8DCEE, WET_TRAIL_COLOR);
            // 水珠主体（2×2）+ 左上角高光（1×1）
            graphics.fill(x, y, x + 2, y + 2, WET_HEAD_COLOR);
            graphics.fill(x, y, x + 1, y + 1, WET_HEAD_CORE);
        }
        // 贴在镜头上不动的失焦光斑
        for (int index = 0; index < WET_BLUR_BLOBS; index++) {
            long seed = index * 4099L;
            int x = (int) Math.floorMod(seed * 2654435761L, (long) width);
            int y = (int) Math.floorMod(seed * 40503L, (long) height);
            int size = 3 + (int) Math.floorMod(seed >> 5, 4L);
            graphics.fill(x, y, x + size, y + size, WET_BLUR_COLOR);
        }
    }

    private static Packet_DisasterSnapshotResponse.DayEntry todayEntry() {
        var snapshot = DisasterClientCache.snapshot();
        if (snapshot == null) {
            return null;
        }
        for (var entry : snapshot.days()) {
            if (entry.day() == snapshot.today()) {
                return entry;
            }
        }
        return null;
    }

    /** 屏幕表现的总开关：有对应天灾 + 能看见天 + 非旁观 + 没按 F1。 */
    private static boolean isExposed() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return false;
        }
        if (minecraft.player.isSpectator()) {
            return false;
        }
        if (!stormToday() && !sandstormToday()) {
            return false;
        }
        return minecraft.level.canSeeSky(minecraft.player.blockPosition());
    }
}
