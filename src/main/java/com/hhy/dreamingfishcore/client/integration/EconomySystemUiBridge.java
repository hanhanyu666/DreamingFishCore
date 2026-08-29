package com.hhy.dreamingfishcore.client.integration;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;

/**
 * Client-only launcher for EconomySystem's native screens.
 *
 * <p>EconomySystem Public API v1 intentionally only stabilizes server-side data APIs. Its client
 * screens are therefore accessed through a tiny reflection boundary instead of becoming a compile-time
 * dependency of DreamingFishCore. If EconomySystem changes its internal screen classes, failure stays
 * contained here and the terminal can fall back gracefully.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class EconomySystemUiBridge {
    private static final String ECONOMY_MOD_ID = "economy_system";
    private static final String HOME_SCREEN =
            "com.mo.economy_system.target.neoforge1211.client.NeoForge1211HomeScreen";
    private static final String SHOP_SCREEN =
            "com.mo.economy_system.target.neoforge1211.client.NeoForge1211ShopScreen";
    private static final String TERRITORY_SCREEN =
            "com.mo.economy_system.target.neoforge1211.client.NeoForge1211TerritoryListScreen";

    private EconomySystemUiBridge() {
    }

    public static boolean openHome(Screen parent) {
        return openNativeScreen(HOME_SCREEN, parent, "经济系统暂时无法打开");
    }

    public static boolean openShop(Screen parent) {
        return openNativeScreen(SHOP_SCREEN, parent, "服务器商店暂时无法打开");
    }

    public static boolean openTerritory(Screen parent) {
        return openNativeScreen(TERRITORY_SCREEN, parent, "领地界面暂时无法打开");
    }

    private static boolean openNativeScreen(String className, Screen parent, String playerMessage) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ModList.get().isLoaded(ECONOMY_MOD_ID)) {
            showUnavailable(minecraft, playerMessage);
            return false;
        }

        try {
            Class<?> screenClass = Class.forName(className);
            if (!Screen.class.isAssignableFrom(screenClass)) {
                throw new IllegalStateException("EconomySystem screen class does not extend Screen: " + className);
            }

            Screen screen;
            try {
                Constructor<?> constructor = screenClass.getConstructor(Screen.class);
                screen = (Screen) constructor.newInstance(parent);
            } catch (NoSuchMethodException ignored) {
                Constructor<?> constructor = screenClass.getConstructor();
                screen = (Screen) constructor.newInstance();
            }
            ServerScreenUI.openSubScreen(screen);
            return true;
        } catch (Throwable error) {
            DreamingFishCore.LOGGER.warn("无法打开 EconomySystem 原生界面: {}", className, error);
            showUnavailable(minecraft, playerMessage);
            return false;
        }
    }

    private static void showUnavailable(Minecraft minecraft, String message) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
