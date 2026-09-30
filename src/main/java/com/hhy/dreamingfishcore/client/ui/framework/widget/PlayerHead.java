package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.function.Supplier;

/** 玩家头像（脸 + 帽子层），圆角，可加描边环。 */
public class PlayerHead extends UiNode<PlayerHead> {
    private Supplier<ResourceLocation> skin;
    private float headSize = 16.0F;
    private float cornerRadius = 3.0F;
    private float ringWidth;
    private int ringColor;

    public PlayerHead() {
        pointerEvents(false);
    }

    public static PlayerHead local() {
        PlayerHead head = new PlayerHead();
        head.skin = () -> {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.player != null ? minecraft.player.getSkin().texture()
                    : DefaultPlayerSkin.getDefaultTexture();
        };
        return head;
    }

    public static PlayerHead of(UUID uuid) {
        PlayerHead head = new PlayerHead();
        head.skin = () -> skinOf(uuid);
        return head;
    }

    public static PlayerHead of(Supplier<ResourceLocation> skin) {
        PlayerHead head = new PlayerHead();
        head.skin = skin;
        return head;
    }

    public static ResourceLocation skinOf(UUID uuid) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (uuid != null && connection != null) {
            PlayerInfo info = connection.getPlayerInfo(uuid);
            if (info != null) {
                return info.getSkin().texture();
            }
        }
        return uuid == null ? DefaultPlayerSkin.getDefaultTexture() : DefaultPlayerSkin.get(uuid).texture();
    }

    public PlayerHead headSize(float value) {
        headSize = value;
        markDirty();
        return this;
    }

    public PlayerHead cornerRadius(float value) {
        cornerRadius = value;
        return this;
    }

    public PlayerHead ring(float widthValue, int color) {
        ringWidth = widthValue;
        ringColor = color;
        return this;
    }

    @Override
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        out.set(headSize, headSize);
    }

    @Override
    protected void paintContent(UiCanvas canvas) {
        float size = Math.min(innerWidth(), innerHeight());
        float x = padLeft() + (innerWidth() - size) * 0.5F;
        float y = padTop() + (innerHeight() - size) * 0.5F;
        if (ringWidth > 0.0F && UiColor.alpha(ringColor) > 0) {
            canvas.shape(x - ringWidth - 1.0F, y - ringWidth - 1.0F, size + ringWidth * 2.0F + 2.0F, size + ringWidth * 2.0F + 2.0F)
                    .radius(cornerRadius + ringWidth + 1.0F).fill(0).border(ringWidth, ringColor).draw();
        }
        canvas.playerFace(skin.get(), x, y, size, cornerRadius, 0xFFFFFFFF);
    }
}
