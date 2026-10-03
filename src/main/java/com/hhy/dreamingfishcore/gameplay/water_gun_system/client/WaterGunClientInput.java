package com.hhy.dreamingfishcore.gameplay.water_gun_system.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.water_gun_system.Item_WaterGun;
import com.hhy.dreamingfishcore.gameplay.water_gun_system.WaterGunConfig;
import com.hhy.dreamingfishcore.gameplay.water_gun_system.network.Packet_WaterGunFire;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * 呲水枪的左键：拦截原版攻击，改成射水。
 *
 * <p>分两件事做，因为它们解决的不是同一个问题：</p>
 * <ul>
 *   <li>{@link InputEvent.InteractionKeyMappingTriggered}：<b>拦住原版行为</b>。原版左键会挖方块、
 *       会挥砍实体，这个事件在「点按」和「按住」两条路径上都会触发（{@code Minecraft#startAttack}
 *       与 {@code #continueAttack}），所以在这里取消一次，长按挖方块也一并被挡住。</li>
 *   <li>{@link ClientTickEvent.Post}：<b>决定什么时候射</b>。点击事件只在「有东西可打」时才走，
 *       对着天按左键根本不会触发；而且它也不适合做连射节奏。所以实际开火放在每 tick 的检查里，
 *       按住左键 + 手持水枪就按 {@code fireIntervalTicks} 的节奏连射。</li>
 * </ul>
 *
 * <p>开火只发一个请求包，消耗水量、生成水柱、冷却校验全在服务端（见
 * {@link Packet_WaterGunFire}），客户端只是「按下去了」这件事的信使。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class WaterGunClientInput {
    /** 客户端侧的连射节流；真正的冷却校验在服务端。 */
    private static int cooldownTicks;

    private WaterGunClientInput() {
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !isHoldingWaterGun(player)) {
            return;
        }
        // 手持水枪：左键既不挖方块也不挥砍。挥手指令由下面的 tick 逻辑发，避免重复播放动画。
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            cooldownTicks = 0;
            return;
        }
        if (!isHoldingWaterGun(player) || !minecraft.options.keyAttack.isDown()) {
            cooldownTicks = 0;
            return;
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        cooldownTicks = Math.max(1, WaterGunConfig.current().resolve().fireIntervalTicks());
        // 空枪也照常挥手：玩家需要看到"我扣了扳机"的反馈，没水的提示由服务端给。
        player.swing(InteractionHand.MAIN_HAND, true);
        DreamingFishCore_NetworkManager.sendToServer(new Packet_WaterGunFire());
    }

    private static boolean isHoldingWaterGun(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof Item_WaterGun;
    }
}
