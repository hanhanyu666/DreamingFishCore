package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Supplier;

/** 实体模型（如玩家），朝向跟随鼠标。尺寸由布局决定，模型按高度缩放。 */
public class EntityView extends UiNode<EntityView> {
    private final Supplier<? extends LivingEntity> entity;
    private boolean followMouse = true;
    private float modelScale = 0.9F;

    public EntityView(Supplier<? extends LivingEntity> entity) {
        this.entity = entity;
        pointerEvents(false);
    }

    public static EntityView of(Supplier<? extends LivingEntity> entity) {
        return new EntityView(entity);
    }

    public EntityView followMouse(boolean value) {
        followMouse = value;
        return this;
    }

    /** 模型高度占节点高度的比例。 */
    public EntityView modelScale(float value) {
        modelScale = value;
        return this;
    }

    @Override
    protected void paintContent(UiCanvas canvas) {
        LivingEntity target = entity.get();
        if (target == null) {
            return;
        }
        // 原版函数使用 GUI 坐标并自行设置裁剪，这里换算为绝对 GUI 坐标后以单位变换绘制
        float x0 = guiLeft();
        float y0 = guiTop();
        float x1 = guiRight();
        float y1 = guiBottom();
        float guiHeight = y1 - y0;
        int size = Math.max(4, Math.round(guiHeight * modelScale / 2.0F));
        double mouseX = root() != null ? root().mouseX() : (x0 + x1) * 0.5;
        double mouseY = root() != null ? root().mouseY() : y0 + guiHeight * 0.3;
        if (!followMouse) {
            mouseX = (x0 + x1) * 0.5;
            mouseY = y0 + guiHeight * 0.35;
        }
        float mx = (float) mouseX;
        float my = (float) mouseY;
        canvas.custom(0.0F, 0.0F, width(), height(), g -> {
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.last().pose().identity();
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, Math.round(x0), Math.round(y0),
                        Math.round(x1), Math.round(y1), size, 0.0625F, mx, my, target);
            } finally {
                pose.popPose();
            }
        });
    }
}
