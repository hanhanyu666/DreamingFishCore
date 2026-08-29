package com.hhy.dreamingfishcore.client.ui.loading;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import javax.annotation.Nullable;

/**
 * Turns the several vanilla world-loading screens into one continuous visual sequence.
 *
 * <p>The entry transition first covers the world/server list, then reveals the loading surface. When the
 * world is ready, a lightweight copy of the last loading frame remains above the rendered world and fades
 * away. This is deliberately screen-to-screen fading rather than an unrelated dark overlay drawn after the
 * game has already appeared.</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class LoadingTransitionController {
    private static final long ENTRY_COVER_MS = 280L;
    private static final long LOADING_REVEAL_MS = 520L;
    private static final long LOADING_EXIT_MS = 920L;

    private static boolean loadingSequenceActive;
    private static long loadingRevealStartedAt = -1L;
    @Nullable private static LoadingFrame lastFrame;
    @Nullable private static Screen entrySource;
    @Nullable private static Screen entryDestination;
    private static long entryCoverStartedAt = -1L;
    private static boolean committingEntryDestination;

    private LoadingTransitionController() {
    }

    /**
     * Called at the start of Minecraft#setScreen. Returning true means this controller supplied or retained
     * an intermediate transition screen and the original screen change must be cancelled.
     */
    public static boolean interceptScreenChange(Minecraft minecraft, @Nullable Screen previous,
                                                @Nullable Screen next) {
        if (entrySource != null && previous == entrySource) {
            if (committingEntryDestination) {
                return false;
            }
            if (isLoadingSurface(next)) {
                entryDestination = next;
                return true;
            }
            clearEntryTransition();
            return false;
        }

        if (isEntrySource(previous) && isLoadingSurface(next)) {
            entrySource = previous;
            entryDestination = next;
            entryCoverStartedAt = System.currentTimeMillis();
            return true;
        }

        if (next == null && isLoadingSurface(previous) && minecraft.level != null) {
            LoadingFrame frame = lastFrame != null
                    ? lastFrame
                    : LoadingFrame.text("", "正在进入梦屿");
            minecraft.setScreen(new LoadingExitTransitionScreen(frame));
            return true;
        }

        return false;
    }

    /** Keeps the real selection screen alive until the dark midpoint fully covers it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderEntryCover(ScreenEvent.Render.Post event) {
        if (entrySource == null || entryDestination == null || event.getScreen() != entrySource) {
            return;
        }

        long elapsed = Math.max(0L, System.currentTimeMillis() - entryCoverStartedAt);
        float progress = smoothStep(elapsed / (float) ENTRY_COVER_MS);
        GuiGraphics guiGraphics = event.getGuiGraphics();
        renderDarkGradient(guiGraphics, guiGraphics.guiWidth(), guiGraphics.guiHeight(), progress);

        if (progress >= 0.999F) {
            Minecraft minecraft = Minecraft.getInstance();
            Screen destination = entryDestination;
            committingEntryDestination = true;
            minecraft.setScreen(destination);
            committingEntryDestination = false;
            clearEntryTransition();
        }
    }

    public static void onScreenChanged(@Nullable Screen previous, @Nullable Screen current) {
        boolean currentIsLoading = isLoadingSurface(current);
        if (currentIsLoading) {
            if (!loadingSequenceActive) {
                loadingSequenceActive = true;
                loadingRevealStartedAt = -1L;
                lastFrame = null;
            }
            return;
        }

        if (isLoadingSurface(previous)) {
            loadingSequenceActive = false;
            loadingRevealStartedAt = -1L;
        }

        if (isTerminalScreen(current)) {
            loadingSequenceActive = false;
            loadingRevealStartedAt = -1L;
            lastFrame = null;
            if (current != entrySource) {
                clearEntryTransition();
            }
        }
    }

    public static void rememberProgressFrame(String tip, String status, int progress, boolean cancelHint) {
        lastFrame = LoadingFrame.progress(tip, status, LoadingScreenUi.clampProgress(progress), cancelHint);
    }

    public static void rememberTextFrame(String tip, String status) {
        lastFrame = LoadingFrame.text(tip, status);
    }

    /** Drawn last on a loading screen, revealing that screen from the dark midpoint of the entry fade. */
    public static void renderLoadingEntry(GuiGraphics guiGraphics, int width, int height) {
        if (!loadingSequenceActive) {
            loadingSequenceActive = true;
            loadingRevealStartedAt = -1L;
        }

        long now = System.currentTimeMillis();
        if (loadingRevealStartedAt < 0L) {
            loadingRevealStartedAt = now;
        }

        float progress = smoothStep((now - loadingRevealStartedAt) / (float) LOADING_REVEAL_MS);
        float remaining = 1.0F - progress;
        if (remaining > 0.001F) {
            renderDarkGradient(guiGraphics, width, height, remaining);
        }
    }

    private static boolean isEntrySource(@Nullable Screen screen) {
        return screen instanceof SelectWorldScreen || screen instanceof JoinMultiplayerScreen;
    }

    private static boolean isLoadingSurface(@Nullable Screen screen) {
        return screen instanceof ConnectScreen
                || screen instanceof ReceivingLevelScreen
                || screen instanceof LevelLoadingScreen
                || screen instanceof ProgressScreen
                || screen instanceof GenericWaitingScreen
                || screen instanceof GenericMessageScreen;
    }

    private static boolean isTerminalScreen(@Nullable Screen screen) {
        return screen instanceof DisconnectedScreen
                || screen instanceof TitleScreen
                || screen instanceof JoinMultiplayerScreen
                || screen instanceof SelectWorldScreen;
    }

    private static void renderDarkGradient(GuiGraphics guiGraphics, int width, int height, float amount) {
        int alpha = Math.round(255.0F * clamp01(amount));
        guiGraphics.fillGradient(0, 0, width, height,
                withAlpha(0xFF080D13, alpha), withAlpha(0xFF030507, alpha));

        int lowerGlowAlpha = Math.round(36.0F * clamp01(amount));
        guiGraphics.fillGradient(0, height / 2, width, height,
                0x00000000, withAlpha(0xFF3A2512, lowerGlowAlpha));
    }

    private static float smoothStep(float value) {
        float t = clamp01(value);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static void clearEntryTransition() {
        entrySource = null;
        entryDestination = null;
        entryCoverStartedAt = -1L;
        committingEntryDestination = false;
    }

    private record LoadingFrame(String tip, String status, int progress,
                                boolean progressVisible, boolean cancelHint) {
        private static LoadingFrame progress(String tip, String status, int progress, boolean cancelHint) {
            return new LoadingFrame(safe(tip), safe(status), progress, true, cancelHint);
        }

        private static LoadingFrame text(String tip, String status) {
            return new LoadingFrame(safe(tip), safe(status), 0, false, false);
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }

    /** Re-renders the last loading frame with decreasing alpha while the world is already visible beneath it. */
    private static final class LoadingExitTransitionScreen extends Screen {
        private final LoadingFrame frame;
        private final VirtualCoordinateHelper.VirtualSizeResult virtualSize =
                new VirtualCoordinateHelper.VirtualSizeResult();
        private long startedAt = -1L;
        private boolean finished;

        private LoadingExitTransitionScreen(LoadingFrame frame) {
            super(Component.empty());
            this.frame = frame;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            long now = System.currentTimeMillis();
            if (startedAt < 0L) {
                startedAt = now;
            }

            float progress = smoothStep((now - startedAt) / (float) LOADING_EXIT_MS);
            float opacity = 1.0F - progress;
            if (opacity > 0.001F) {
                renderLoadingFrame(guiGraphics, now, opacity);
            } else {
                finish();
            }
        }

        private void renderLoadingFrame(GuiGraphics guiGraphics, long now, float opacity) {
            VirtualCoordinateHelper.calculateVirtualSize(this, virtualSize);
            LoadingScreenUi.renderBackground(guiGraphics, width, height, opacity);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(virtualSize.uiScale, virtualSize.uiScale, 1.0F);
            LoadingScreenUi.renderTip(guiGraphics, font, frame.tip,
                    Math.min(250, virtualSize.virtualWidth - 52), opacity);
            if (frame.progressVisible) {
                LoadingScreenUi.renderStatusWaveform(guiGraphics, font,
                        virtualSize.virtualWidth, virtualSize.virtualHeight,
                        frame.status, frame.progress, now, opacity);
            } else {
                LoadingScreenUi.renderBottomStatusText(guiGraphics, font,
                        virtualSize.virtualWidth, virtualSize.virtualHeight, frame.status, opacity);
            }
            if (frame.cancelHint) {
                LoadingScreenUi.renderActionHint(guiGraphics, font,
                        virtualSize.virtualWidth, virtualSize.virtualHeight, " 中断连接", opacity);
            }
            guiGraphics.pose().popPose();
        }

        private void finish() {
            if (!finished && minecraft != null) {
                finished = true;
                minecraft.setScreen(null);
            }
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
