package com.hhy.dreamingfishcore.client.ui.util;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.PngInfo;
import net.neoforged.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class UiBackgroundRenderer {
    /** Naming a PNG this way always places it first in the background cycle. */
    public static final String FIRST_BACKGROUND_FILE_NAME = "first.png";
    /** This optional PNG remains fixed while a world/server is loading. */
    public static final String WORLD_LOADING_FILE_NAME = "world_loading.png";

    private static final String BACKGROUND_DIRECTORY_NAME = "loading_backgrounds";
    private static final String INSTRUCTIONS_FILE_NAME = "README.txt";
    private static final String INSTRUCTIONS = """
            梦屿加载背景目录

            1. 仅读取本目录第一层的 PNG 图片，不扫描子目录。
            2. first.png 永远是第一张和启动默认背景。
            3. world_loading.png 会优先作为进入世界/服务器时的固定背景；缺失时使用当前轮播图。
            4. 其他图片按文件名排序，每 5 秒轮换，并带有原有渐变动画。
            5. 游戏运行期间可以增删或替换图片，通常会在 2 秒内自动重新读取。
            """;

    private static final Path BACKGROUND_DIRECTORY = FMLPaths.CONFIGDIR.get()
            .resolve(DreamingFishCore.MODID)
            .resolve(BACKGROUND_DIRECTORY_NAME)
            .toAbsolutePath()
            .normalize();
    private static final ResourceLocation RADIO_ICON =
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID,
                    "textures/gui/loading/radio.png");

    private static final long BG_SWITCH_INTERVAL_MS = 5_000L;
    private static final long BG_CROSSFADE_DURATION_MS = 1_000L;
    private static final long DIRECTORY_SCAN_INTERVAL_MS = 2_000L;
    private static final long MAX_PNG_FILE_SIZE = 128L * 1024L * 1024L;
    private static final int MAX_PNG_DIMENSION = 8_192;
    private static final long MAX_PNG_PIXELS = 40_000_000L;
    private static final int FALLBACK_TOP_COLOR = 0xFF1B2026;
    private static final int FALLBACK_BOTTOM_COLOR = 0xFF090C10;

    private static final Comparator<Path> BACKGROUND_ORDER = Comparator
            .comparingInt(UiBackgroundRenderer::backgroundPriority)
            .thenComparing(path -> fileName(path).toLowerCase(Locale.ROOT))
            .thenComparing(UiBackgroundRenderer::fileName);

    private static List<ExternalBackground> backgrounds = List.of();
    private static int currentBgIndex;
    private static int prevBgIndex;
    private static int textureSerial;
    private static long lastBgSwitchTime;
    private static long lastDirectoryScanTime = Long.MIN_VALUE;
    private static boolean catalogInitialized;
    private static boolean directoryWarningLogged;

    private UiBackgroundRenderer() {
    }

    public static Path backgroundDirectory() {
        return BACKGROUND_DIRECTORY;
    }

    /** 5秒轮换外置背景，供主菜单和普通菜单界面共用。 */
    public static void renderCyclingBackground(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        long now = Util.getMillis();
        ensureBackgroundCatalog(now);
        updateCycle(now);
        renderBackgroundOrFallback(guiGraphics, currentBgIndex, screenWidth, screenHeight, 1.0F);
    }

    /** 加载界面优先固定使用 world_loading.png，避免加载期间背景随机跳动。 */
    public static void renderLoadingBackground(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        renderLoadingBackground(guiGraphics, screenWidth, screenHeight, 1.0F);
    }

    /** 加载完成后让整张加载背景作为一个图层淡出到已经渲染好的世界。 */
    public static void renderLoadingBackground(GuiGraphics guiGraphics, int screenWidth, int screenHeight,
                                               float opacity) {
        ensureBackgroundCatalog(Util.getMillis());
        int loadingIndex = indexOfFile(WORLD_LOADING_FILE_NAME);
        int requestedIndex = loadingIndex >= 0 ? loadingIndex : currentBgIndex;
        renderBackgroundOrFallback(guiGraphics, requestedIndex, screenWidth, screenHeight, opacity);
    }

    /** 游戏首次启动进入标题界面时固定显示 first.png。 */
    public static void renderStartupBackground(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        renderStartupBackground(guiGraphics, screenWidth, screenHeight, 1.0F);
    }

    public static void renderStartupBackground(GuiGraphics guiGraphics, int screenWidth, int screenHeight,
                                               float opacity) {
        ensureBackgroundCatalog(Util.getMillis());
        renderBackgroundOrFallback(guiGraphics, 0, screenWidth, screenHeight, opacity);
    }

    public static void renderRadioIcon(GuiGraphics guiGraphics, int x, int y, int size) {
        renderRadioIcon(guiGraphics, x, y, size, 1.0F);
    }

    public static void renderRadioIcon(GuiGraphics guiGraphics, int x, int y, int size, float opacity) {
        if (size <= 0) {
            return;
        }
        float alpha = clampOpacity(opacity);
        RenderSystem.enableBlend();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        guiGraphics.blit(RADIO_ICON, x, y, size, size,
                0.0F, 0.0F, 32, 32, 32, 32);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 带渐变的5秒轮换背景，供标题界面使用。
     * 所有界面共享同一个 cycle timer，保证切换同步。
     *
     * @param fadeAlpha 屏幕淡入 alpha（0~1），仅在非渐变阶段应用
     */
    public static void renderCyclingBackgroundCrossfade(GuiGraphics guiGraphics, int screenWidth,
                                                         int screenHeight, float fadeAlpha) {
        long now = Util.getMillis();
        ensureBackgroundCatalog(now);
        updateCycle(now);

        ExternalBackground current = findUsableBackground(currentBgIndex);
        if (current == null) {
            renderFallback(guiGraphics, screenWidth, screenHeight, fadeAlpha);
            return;
        }

        long elapsed = Math.max(0L, now - lastBgSwitchTime);
        if (fadeAlpha >= 1.0F && elapsed < BG_CROSSFADE_DURATION_MS && prevBgIndex != currentBgIndex) {
            ExternalBackground previous = findUsableBackground(prevBgIndex);
            if (previous != null && previous != current) {
                float progress = elapsed / (float) BG_CROSSFADE_DURATION_MS;
                float eased = progress * progress * (3.0F - 2.0F * progress);
                renderExternalBackground(guiGraphics, previous, screenWidth, screenHeight, 1.0F - eased);
                renderExternalBackground(guiGraphics, current, screenWidth, screenHeight, eased);
                return;
            }
        }

        renderExternalBackground(guiGraphics, current, screenWidth, screenHeight, fadeAlpha);
    }

    private static void updateCycle(long now) {
        int count = backgrounds.size();
        if (count <= 1) {
            currentBgIndex = 0;
            prevBgIndex = 0;
            if (lastBgSwitchTime == 0L) {
                lastBgSwitchTime = now;
            }
            return;
        }
        if (lastBgSwitchTime == 0L) {
            lastBgSwitchTime = now;
            return;
        }
        if (now - lastBgSwitchTime >= BG_SWITCH_INTERVAL_MS) {
            prevBgIndex = Math.floorMod(currentBgIndex, count);
            currentBgIndex = (prevBgIndex + 1) % count;
            lastBgSwitchTime = now;
        }
    }

    private static void ensureBackgroundCatalog(long now) {
        if (catalogInitialized && now - lastDirectoryScanTime < DIRECTORY_SCAN_INTERVAL_MS) {
            return;
        }
        catalogInitialized = true;
        lastDirectoryScanTime = now;

        try {
            Files.createDirectories(BACKGROUND_DIRECTORY);
            writeInstructionsIfMissing();
            refreshCatalog(scanBackgroundFiles(), now);
            directoryWarningLogged = false;
        } catch (IOException exception) {
            if (!directoryWarningLogged) {
                DreamingFishCore.LOGGER.warn("无法读取外置加载背景目录：{}", BACKGROUND_DIRECTORY, exception);
                directoryWarningLogged = true;
            }
        }
    }

    private static List<FileSnapshot> scanBackgroundFiles() throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(BACKGROUND_DIRECTORY)) {
            files = stream
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(UiBackgroundRenderer::isPngFile)
                    .sorted(BACKGROUND_ORDER)
                    .toList();
        }

        List<FileSnapshot> snapshots = new ArrayList<>(files.size());
        for (Path file : files) {
            try {
                long size = Files.size(file);
                if (size <= 0L || size > MAX_PNG_FILE_SIZE) {
                    DreamingFishCore.LOGGER.warn("忽略大小异常的加载背景：{}（{} bytes）", file, size);
                    continue;
                }
                long modified = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis();
                snapshots.add(new FileSnapshot(file.toAbsolutePath().normalize(), size, modified));
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("无法读取加载背景文件信息：{}", file, exception);
            }
        }
        return snapshots;
    }

    private static void refreshCatalog(List<FileSnapshot> snapshots, long now) {
        List<FileSnapshot> previousSnapshots = backgrounds.stream()
                .map(ExternalBackground::snapshot)
                .toList();
        if (previousSnapshots.equals(snapshots)) {
            return;
        }

        Path currentPath = pathAt(currentBgIndex);
        Path previousPath = pathAt(prevBgIndex);
        Map<FileSnapshot, ExternalBackground> reusable = new HashMap<>();
        for (ExternalBackground background : backgrounds) {
            reusable.put(background.snapshot(), background);
        }

        List<ExternalBackground> next = new ArrayList<>(snapshots.size());
        for (FileSnapshot snapshot : snapshots) {
            ExternalBackground background = reusable.remove(snapshot);
            next.add(background == null ? new ExternalBackground(snapshot) : background);
        }
        for (ExternalBackground removed : reusable.values()) {
            removed.release();
        }

        backgrounds = List.copyOf(next);
        currentBgIndex = findPathIndex(currentPath);
        if (currentBgIndex < 0) {
            currentBgIndex = 0;
        }
        prevBgIndex = findPathIndex(previousPath);
        if (prevBgIndex < 0) {
            prevBgIndex = currentBgIndex;
        }
        lastBgSwitchTime = now;
        DreamingFishCore.LOGGER.info("已发现 {} 张外置加载背景：{}", backgrounds.size(), BACKGROUND_DIRECTORY);
    }

    private static void writeInstructionsIfMissing() throws IOException {
        Path instructionsPath = BACKGROUND_DIRECTORY.resolve(INSTRUCTIONS_FILE_NAME);
        if (Files.exists(instructionsPath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.writeString(instructionsPath, INSTRUCTIONS, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ignored) {
            // Another client/render pass created it first.
        }
    }

    private static void renderBackgroundOrFallback(GuiGraphics guiGraphics, int requestedIndex,
                                                   int screenWidth, int screenHeight, float opacity) {
        ExternalBackground background = findUsableBackground(requestedIndex);
        if (background == null) {
            renderFallback(guiGraphics, screenWidth, screenHeight, opacity);
            return;
        }
        renderExternalBackground(guiGraphics, background, screenWidth, screenHeight, opacity);
    }

    @Nullable
    private static ExternalBackground findUsableBackground(int requestedIndex) {
        int count = backgrounds.size();
        if (count == 0) {
            return null;
        }
        int start = Math.floorMod(requestedIndex, count);
        for (int offset = 0; offset < count; offset++) {
            ExternalBackground candidate = backgrounds.get((start + offset) % count);
            if (candidate.ensureLoaded()) {
                return candidate;
            }
        }
        return null;
    }

    private static void renderExternalBackground(GuiGraphics guiGraphics, ExternalBackground background,
                                                 int screenWidth, int screenHeight, float opacity) {
        if (screenWidth <= 0 || screenHeight <= 0 || background.texture() == null) {
            return;
        }

        float scale = Math.max(
                screenWidth / (float) background.width(),
                screenHeight / (float) background.height());
        int drawWidth = Math.round(background.width() * scale);
        int drawHeight = Math.round(background.height() * scale);
        int drawX = (screenWidth - drawWidth) / 2;
        int drawY = (screenHeight - drawHeight) / 2;

        RenderSystem.enableBlend();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, clampOpacity(opacity));
        guiGraphics.blit(background.texture(),
                drawX, drawY, drawWidth, drawHeight,
                0.0F, 0.0F, background.width(), background.height(),
                background.width(), background.height());
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderFallback(GuiGraphics guiGraphics, int width, int height, float opacity) {
        if (width <= 0 || height <= 0) {
            return;
        }
        guiGraphics.fillGradient(0, 0, width, height,
                withOpacity(FALLBACK_TOP_COLOR, opacity), withOpacity(FALLBACK_BOTTOM_COLOR, opacity));
    }

    private static int indexOfFile(String name) {
        for (int index = 0; index < backgrounds.size(); index++) {
            if (fileName(backgrounds.get(index).snapshot().path()).equalsIgnoreCase(name)) {
                return index;
            }
        }
        return -1;
    }

    @Nullable
    private static Path pathAt(int index) {
        return index >= 0 && index < backgrounds.size()
                ? backgrounds.get(index).snapshot().path()
                : null;
    }

    private static int findPathIndex(@Nullable Path path) {
        if (path == null) {
            return -1;
        }
        for (int index = 0; index < backgrounds.size(); index++) {
            if (backgrounds.get(index).snapshot().path().equals(path)) {
                return index;
            }
        }
        return -1;
    }

    private static int backgroundPriority(Path path) {
        return fileName(path).equalsIgnoreCase(FIRST_BACKGROUND_FILE_NAME) ? 0 : 1;
    }

    private static boolean isPngFile(Path path) {
        return fileName(path).toLowerCase(Locale.ROOT).endsWith(".png");
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name == null ? "" : name.toString();
    }

    private static float clampOpacity(float opacity) {
        return Math.max(0.0F, Math.min(1.0F, opacity));
    }

    private static int withOpacity(int color, float opacity) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * clampOpacity(opacity));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private record FileSnapshot(Path path, long size, long modifiedTime) {
    }

    private static final class ExternalBackground {
        private final FileSnapshot snapshot;
        @Nullable
        private ResourceLocation texture;
        private int width;
        private int height;
        private boolean loadFailed;

        private ExternalBackground(FileSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        private FileSnapshot snapshot() {
            return snapshot;
        }

        @Nullable
        private ResourceLocation texture() {
            return texture;
        }

        private int width() {
            return width;
        }

        private int height() {
            return height;
        }

        private boolean ensureLoaded() {
            if (texture != null) {
                return true;
            }
            if (loadFailed) {
                return false;
            }

            NativeImage image = null;
            DynamicTexture dynamicTexture = null;
            try {
                PngInfo pngInfo;
                try (InputStream stream = Files.newInputStream(snapshot.path())) {
                    pngInfo = PngInfo.fromStream(stream);
                }
                validateDimensions(pngInfo.width(), pngInfo.height());

                try (InputStream stream = Files.newInputStream(snapshot.path())) {
                    image = NativeImage.read(stream);
                }
                validateDimensions(image.getWidth(), image.getHeight());

                int loadedWidth = image.getWidth();
                int loadedHeight = image.getHeight();
                dynamicTexture = new DynamicTexture(image);
                image = null;

                ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                        DreamingFishCore.MODID, "dynamic/loading_background/" + textureSerial++);
                Minecraft.getInstance().getTextureManager().register(location, dynamicTexture);
                dynamicTexture = null;

                texture = location;
                width = loadedWidth;
                height = loadedHeight;
                return true;
            } catch (Exception exception) {
                loadFailed = true;
                DreamingFishCore.LOGGER.warn("无法加载外置背景图片：{}", snapshot.path(), exception);
                return false;
            } finally {
                if (image != null) {
                    image.close();
                }
                if (dynamicTexture != null) {
                    dynamicTexture.close();
                }
            }
        }

        private void release() {
            if (texture == null) {
                return;
            }
            try {
                Minecraft.getInstance().getTextureManager().release(texture);
            } catch (RuntimeException exception) {
                DreamingFishCore.LOGGER.debug("释放外置背景贴图失败：{}", texture, exception);
            } finally {
                texture = null;
            }
        }

        private static void validateDimensions(int width, int height) throws IOException {
            long pixels = (long) width * height;
            if (width <= 0 || height <= 0 || width > MAX_PNG_DIMENSION || height > MAX_PNG_DIMENSION
                    || pixels > MAX_PNG_PIXELS) {
                throw new IOException("PNG dimensions are outside the safe range: " + width + "x" + height);
            }
        }
    }
}
