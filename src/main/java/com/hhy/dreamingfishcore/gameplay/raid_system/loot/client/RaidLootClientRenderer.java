package com.hhy.dreamingfishcore.gameplay.raid_system.loot.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.loot.network.Packet_RaidLootSync;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 在世界里画出"地上的战利品"：把同步下来的节点渲染成一个小幅旋转的漂浮物品。
 *
 * <p>设计稿 §8.1 明确不用 {@code ItemEntity}（会被水流推、受爆炸影响、自动堆叠、被漏斗吸、
 * 桌面物品会掉地上、区块重载难管理），所以这些物品**在世界里根本不存在实体**——
 * 只有服务端的一份节点数据，客户端按它画一个模型。因此：</p>
 * <ul>
 *   <li>不会堆叠、不会被推动、不会被漏斗吸走（没有实体就没有这些行为）；</li>
 *   <li>拾取完全由服务端判定（走到 1.5 格内自动拾取），客户端只负责显示；</li>
 *   <li>物品被拿走后服务端重新下发列表，这里就不再画了。</li>
 * </ul>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class RaidLootClientRenderer {

    /** 物品悬浮高度与缩放。 */
    private static final double HOVER_HEIGHT = 0.3D;
    private static final float SCALE = 0.55F;
    /** 自转速度（每 tick 的度数），让玩家一眼看出"这是可拾取的东西"。 */
    private static final float SPIN_PER_TICK = 1.5F;

    private RaidLootClientRenderer() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        var visible = RaidLootClientCache.visible();
        if (visible.isEmpty()) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        // 用游戏时间做自转：所有客户端看起来一致，也不需要额外网络同步
        float spin = (minecraft.level.getGameTime() % 360L) * SPIN_PER_TICK;

        for (Packet_RaidLootSync.Entry entry : visible) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.itemId()));
            if (item == null || item == Items.AIR) {
                continue;
            }
            pose.pushPose();
            pose.translate(entry.x() - camera.x, entry.y() - camera.y + HOVER_HEIGHT, entry.z() - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(entry.yaw() + spin));
            pose.scale(SCALE, SCALE, SCALE);
            minecraft.getItemRenderer().renderStatic(new ItemStack(item), ItemDisplayContext.GROUND,
                    0xF000F0, OverlayTexture.NO_OVERLAY, pose, buffers, minecraft.level, 0);
            pose.popPose();
        }
        buffers.endBatch();
    }
}
