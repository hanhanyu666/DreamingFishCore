package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.client;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.DeathCorpseEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** 将玩家皮肤横向绘制在尸体碰撞箱内。 */
public final class DeathCorpseRenderer extends EntityRenderer<DeathCorpseEntity> {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);
    private final Map<DeathCorpseEntity, DummyCorpsePlayer> players = new WeakHashMap<>();

    public DeathCorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(DeathCorpseEntity corpse,
                       float entityYaw,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffers,
                       int packedLight) {
        DummyCorpsePlayer dummy = players.computeIfAbsent(corpse, this::createDummyPlayer);
        dummy.updateEquipment(corpse);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-corpse.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.translate(0.0D, -1.0D, 2.01D / 16.0D);

        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy)
                .render(dummy, 0.0F, partialTick, poseStack, buffers, packedLight);
        poseStack.popPose();
    }

    private DummyCorpsePlayer createDummyPlayer(DeathCorpseEntity corpse) {
        UUID uuid = corpse.getOwnerUuid().orElse(EMPTY_UUID);
        String name = corpse.getOwnerName().isBlank() ? "Unknown" : corpse.getOwnerName();
        return new DummyCorpsePlayer((ClientLevel) corpse.level(), new GameProfile(uuid, name));
    }

    @Override
    public ResourceLocation getTextureLocation(DeathCorpseEntity entity) {
        return null;
    }
}
