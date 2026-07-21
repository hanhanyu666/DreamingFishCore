package com.hhy.dreamingfishcore.client.input;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.server_ui_system.client.ServerInformationDisplay;
import com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeybindHandler {
    public static final KeyMapping INFORMATION_UI_KEY = new KeyMapping(
            "key.dreamingfishcore.open_screen_o",
            GLFW.GLFW_KEY_O,
            "key.categories.dreamingfishcore");

    public static final KeyMapping TERMINAL_UI_KEY = new KeyMapping(
            "key.dreamingfishcore.open_terminal_u",
            GLFW.GLFW_KEY_U,
            "key.categories.dreamingfishcore");

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(INFORMATION_UI_KEY);
        event.register(TERMINAL_UI_KEY);
    }

    @Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class KeyInputHandler {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (INFORMATION_UI_KEY.consumeClick()) {
                ServerInformationDisplay.toggleUI();
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(ServerInformationDisplay.isShowUI()
                            ? Component.literal("§a[DreamingfishCore]信息面板已开启，再次按下O关闭！")
                            : Component.literal("§c[DreamingfishCore]信息面板已关闭，再次按下O开启！"));
                }
            }
            if (TERMINAL_UI_KEY.consumeClick()) {
                ServerScreenUI.toggleUI();
            }
        }
    }
}
