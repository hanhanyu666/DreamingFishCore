package com.hhy.dreamingfishcore.client.util;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UiBackgroundRenderer {

    private static final ResourceLocation LOADING_BACKGROUND =
            new ResourceLocation(DreamingFishCore.MODID, "background_1.png");
    private static final ResourceLocation[] MENU_BG_TEXTURES = {
            LOADING_BACKGROUND,
            new ResourceLocation(DreamingFishCore.MODID, "background_2.png"),
            new ResourceLocation(DreamingFishCore.MODID, "background_3.png"),
            new ResourceLocation(DreamingFishCore.MODID, "background_4.png"),
            new ResourceLocation(DreamingFishCore.MODID, "background_5.png"),
            new ResourceLocation(DreamingFishCore.MODID, "background_6.png"),
            new ResourceLocation(DreamingFishCore.MODID, "background_7.png"),
            new ResourceLocation(DreamingFishCore.MODID, "background_8.png"),
            new ResourceLocation(DreamingFishCore.MODID, "background_14.png"),
            new ResourceLocation(DreamingFishCore.MODID, "background_16.png")
    };

    private static final long BG_SWITCH_INTERVAL = 5_000L;
    private static final long BG_CROSSFADE_DURATION = 1_000L;
    private static final Map<ResourceLocation, ImageSize> SIZE_CACHE = new ConcurrentHashMap<>();

    private static int currentBgIndex;
    private static int prevBgIndex;
    private static long lastBgSwitchTime;

    private UiBackgroundRenderer() {
    }

    /** 5 秒轮换背景，供主菜单和普通菜单界面共用。 */
    public static void renderCyclingBackground(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        updateCycle();
        renderCover(guiGraphics, MENU_BG_TEXTURES[currentBgIndex], screenWidth, screenHeight);
    }

    /** 加载界面固定使用大合照，避免加载时背景随机跳动。 */
    public static void renderLoadingBackground(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        renderCover(guiGraphics, LOADING_BACKGROUND, screenWidth, screenHeight);
    }

    /** 带渐变的 5 秒轮换背景，所有界面共享同一计时器。 */
    public static void renderCyclingBackgroundCrossfade(GuiGraphics guiGraphics, int screenWidth, int screenHeight,
                                                        float fadeAlpha) {
        long now = System.currentTimeMillis();
        if (lastBgSwitchTime == 0L) {
            lastBgSwitchTime = now;
        }

        long elapsed = now - lastBgSwitchTime;
        if (elapsed >= BG_SWITCH_INTERVAL) {
            prevBgIndex = currentBgIndex;
            currentBgIndex = (currentBgIndex + 1) % MENU_BG_TEXTURES.length;
            lastBgSwitchTime = now;
            elapsed = 0L;
        }

        RenderSystem.enableBlend();
        if (fadeAlpha >= 1.0F && elapsed < BG_CROSSFADE_DURATION && prevBgIndex != currentBgIndex) {
            float progress = (float) elapsed / BG_CROSSFADE_DURATION;
            float eased = progress * progress * (3.0F - 2.0F * progress);

            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F - eased);
            renderCover(guiGraphics, MENU_BG_TEXTURES[prevBgIndex], screenWidth, screenHeight);
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, eased);
            renderCover(guiGraphics, MENU_BG_TEXTURES[currentBgIndex], screenWidth, screenHeight);
        } else {
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, fadeAlpha);
            renderCover(guiGraphics, MENU_BG_TEXTURES[currentBgIndex], screenWidth, screenHeight);
        }
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void updateCycle() {
        long now = System.currentTimeMillis();
        if (lastBgSwitchTime == 0L) {
            lastBgSwitchTime = now;
        }
        if (now - lastBgSwitchTime >= BG_SWITCH_INTERVAL) {
            prevBgIndex = currentBgIndex;
            currentBgIndex = (currentBgIndex + 1) % MENU_BG_TEXTURES.length;
            lastBgSwitchTime = now;
        }
    }

    /** 根据贴图真实尺寸做 cover 裁切，铺满屏幕并保持原始宽高比。 */
    public static void renderCover(GuiGraphics guiGraphics, ResourceLocation texture, int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        ImageSize size = getImageSize(texture);
        float scale = Math.max(screenWidth / (float) size.width, screenHeight / (float) size.height);
        int drawWidth = Math.round(size.width * scale);
        int drawHeight = Math.round(size.height * scale);
        int drawX = (screenWidth - drawWidth) / 2;
        int drawY = (screenHeight - drawHeight) / 2;

        guiGraphics.blit(texture, drawX, drawY, drawWidth, drawHeight,
                0, 0, size.width, size.height, size.width, size.height);
    }

    private static ImageSize getImageSize(ResourceLocation texture) {
        return SIZE_CACHE.computeIfAbsent(texture, location -> {
            try {
                var resource = Minecraft.getInstance().getResourceManager().getResource(location);
                if (resource.isPresent()) {
                    try (NativeImage image = NativeImage.read(resource.get().open())) {
                        return new ImageSize(image.getWidth(), image.getHeight());
                    }
                }
            } catch (Exception exception) {
                DreamingFishCore.LOGGER.warn("Failed to read dimensions for texture: {}", location, exception);
            }
            return new ImageSize(1920, 1080);
        });
    }

    private record ImageSize(int width, int height) {
    }
}
