package com.hhy.dreamingfishcore.gameplay.npc_system.client;

import com.hhy.dreamingfishcore.gameplay.npc_system.entity.StoryNpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端渲染器：把普通 Mob 实体画成原版 PlayerModel。宽臂和细臂各持有一个模型，
 * 每帧根据服务端同步的 model 字段选择；PlayerModel 本身包含帽子、袖子等皮肤第二层部件。
 */
public class StoryNpcRenderer extends MobRenderer<StoryNpcEntity, PlayerModel<StoryNpcEntity>> {
    private static final ThreadLocal<Integer> NAMEPLATE_SUPPRESSION_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private final PlayerModel<StoryNpcEntity> wideModel;
    private final PlayerModel<StoryNpcEntity> slimModel;

    public StoryNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        wideModel = this.model;
        slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public ResourceLocation getTextureLocation(StoryNpcEntity entity) {
        ResourceLocation configured = getAvailableConfiguredSkin(entity);
        if (configured != null) return configured;
        return DefaultPlayerSkin.get(entity.getUUID()).texture();
    }

    /**
     * ResourceLocation 只负责检查字符串语法，并不能证明纹理真的存在。这里再询问当前客户端
     * ResourceManager；资源包重载后它会自然反映最新结果，也不会把旧的“不存在”结论永久缓存。
     */
    private ResourceLocation getAvailableConfiguredSkin(StoryNpcEntity entity) {
        String skin = entity.getSkin();
        if (skin.isBlank()) return null;
        ResourceLocation location = ResourceLocation.tryParse(skin);
        if (location == null) return null;
        return Minecraft.getInstance().getResourceManager().getResource(location).isPresent() ? location : null;
    }

    /** GUI 人物预览复用世界实体时，禁止把实体名称牌一起画进预览框。 */
    public static void renderWithoutNameplate(Runnable renderAction) {
        int previousDepth = NAMEPLATE_SUPPRESSION_DEPTH.get();
        NAMEPLATE_SUPPRESSION_DEPTH.set(previousDepth + 1);
        try {
            renderAction.run();
        } finally {
            if (previousDepth == 0) {
                NAMEPLATE_SUPPRESSION_DEPTH.remove();
            } else {
                NAMEPLATE_SUPPRESSION_DEPTH.set(previousDepth);
            }
        }
    }

    @Override
    protected boolean shouldShowName(StoryNpcEntity entity) {
        return NAMEPLATE_SUPPRESSION_DEPTH.get() == 0 && super.shouldShowName(entity);
    }

    @Override
    protected void renderNameTag(StoryNpcEntity entity, Component displayName, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight, float partialTick) {
        if (NAMEPLATE_SUPPRESSION_DEPTH.get() == 0) {
            super.renderNameTag(entity, displayName, poseStack, bufferSource, packedLight, partialTick);
        }
    }

    @Override
    public void render(StoryNpcEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        boolean slim = getAvailableConfiguredSkin(entity) != null
                ? entity.isSlimModel()
                : DefaultPlayerSkin.get(entity.getUUID()).model() == PlayerSkin.Model.SLIM;
        this.model = slim ? slimModel : wideModel;
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }
}
