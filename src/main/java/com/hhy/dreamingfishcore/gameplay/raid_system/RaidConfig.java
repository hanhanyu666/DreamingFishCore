package com.hhy.dreamingfishcore.gameplay.raid_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/**
 * 搜打撤的服务器配置（{@code config/dreamingfishcore/raid.json}）。
 *
 * <p>与地图内容分开：物品表、区域模板、锚点、撤离点候选都随地图分发，
 * 而"读条几秒、半径几格、出口在哪、一局多久、掉线宽限多久"这类**服务器设定**放这里，
 * 服主改完 {@code /reload} 即可。</p>
 *
 * <p>所有数值都会夹取到合法区间，坏文件回退默认值并且**不覆盖**原文件（服主写坏了自己能看出来）。</p>
 */
public final class RaidConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * 配置路径**懒加载**：{@code FMLPaths} 需要 FML 已初始化，如果在静态字段里取，
     * 普通单测（没有 FML 环境）一碰这个类就会 ExceptionInInitializerError——
     * 而这个类里还有 {@link #shouldAutoEnd} 这种纯函数需要被单测覆盖。
     */
    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(DreamingFishCore.MODID).resolve("raid.json");
    }

    public static final double DEFAULT_EXTRACTION_RADIUS = 3.0D;
    public static final int DEFAULT_EXTRACTION_SECONDS = 5;
    public static final int DEFAULT_AUTO_END_SECONDS = 1800;
    public static final int DEFAULT_LOGOUT_GRACE_SECONDS = 300;

    private static volatile Settings settings = new Settings();

    private RaidConfig() {
    }

    /** 配置内容。坐标为 null 表示用主世界出生点。 */
    public static final class Settings {

        public double extractionRadius = DEFAULT_EXTRACTION_RADIUS;
        public int extractionSeconds = DEFAULT_EXTRACTION_SECONDS;
        public int autoEndSeconds = DEFAULT_AUTO_END_SECONDS;
        public boolean autoEndWhenAllOut = true;
        public int logoutGraceSeconds = DEFAULT_LOGOUT_GRACE_SECONDS;
        public String exitDimension = "minecraft:overworld";
        public double[] exitPosition;
    }

    public static double extractionRadius() {
        return settings.extractionRadius;
    }

    public static int extractionTicks() {
        return Math.max(1, settings.extractionSeconds * 20);
    }

    public static int autoEndSeconds() {
        return settings.autoEndSeconds;
    }

    /** 本局无人留在图里时是否自动结束（默认开：撤了、死了、掉线超时都算出去）。 */
    public static boolean autoEndWhenAllOut() {
        return settings.autoEndWhenAllOut;
    }

    /** 掉线宽限（毫秒）。 */
    public static long logoutGraceMillis() {
        return Math.max(0, settings.logoutGraceSeconds) * 1000L;
    }

    public static String exitDimension() {
        return settings.exitDimension;
    }

    /** 出口坐标；null 表示用该维度的出生点。 */
    public static double[] exitPosition() {
        double[] position = settings.exitPosition;
        return position == null ? null : position.clone();
    }

    public static synchronized void reload() {
        settings = load();
    }

    /**
     * 是否该因超时自动结束这一局（纯函数，单独测）。
     *
     * @param autoEndSeconds 0 或负数表示关闭自动结束
     */
    public static boolean shouldAutoEnd(long startedAtEpochMillis, long nowEpochMillis, int autoEndSeconds) {
        if (autoEndSeconds <= 0 || startedAtEpochMillis <= 0L) {
            return false;
        }
        return nowEpochMillis - startedAtEpochMillis >= autoEndSeconds * 1000L;
    }

    private static Settings load() {
        Settings fallback = defaults();
        if (!Files.isRegularFile(configPath())) {
            save(fallback);     // 第一次运行时把默认值写出来，服主照着改
            return fallback;
        }
        try (Reader reader = Files.newBufferedReader(configPath(), StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                DreamingFishCore.LOGGER.warn("[raid_config] 配置不是 JSON 对象，按默认值处理：{}", configPath());
                return fallback;
            }
            JsonObject json = element.getAsJsonObject();
            Settings loaded = new Settings();
            loaded.extractionRadius = clampDouble(json, "extraction_radius",
                    DEFAULT_EXTRACTION_RADIUS, 0.5D, 16.0D);
            loaded.extractionSeconds = clampInt(json, "extraction_seconds",
                    DEFAULT_EXTRACTION_SECONDS, 1, 60);
            loaded.autoEndSeconds = clampInt(json, "auto_end_seconds",
                    DEFAULT_AUTO_END_SECONDS, 0, 24 * 3600);
            loaded.autoEndWhenAllOut = readBoolean(json, "auto_end_when_all_out", true);
            loaded.logoutGraceSeconds = clampInt(json, "logout_grace_seconds",
                    DEFAULT_LOGOUT_GRACE_SECONDS, 0, 3600);
            JsonElement dimension = json.get("exit_dimension");
            if (dimension != null && dimension.isJsonPrimitive()) {
                String value = dimension.getAsString().trim();
                if (!value.isEmpty()) {
                    loaded.exitDimension = value;
                }
            }
            JsonElement position = json.get("exit_position");
            if (position != null && position.isJsonArray() && position.getAsJsonArray().size() >= 3) {
                JsonArray array = position.getAsJsonArray();
                loaded.exitPosition = new double[]{array.get(0).getAsDouble(),
                        array.get(1).getAsDouble(), array.get(2).getAsDouble()};
            }
            return loaded;
        } catch (IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.error("[raid_config] 配置读取失败，按默认值处理：{}", configPath(), exception);
            return fallback;
        }
    }

    private static Settings defaults() {
        return new Settings();
    }

    private static void save(Settings value) {
        JsonObject json = new JsonObject();
        json.addProperty("extraction_radius", value.extractionRadius);
        json.addProperty("extraction_seconds", value.extractionSeconds);
        json.addProperty("auto_end_seconds", value.autoEndSeconds);
        json.addProperty("auto_end_when_all_out", value.autoEndWhenAllOut);
        json.addProperty("logout_grace_seconds", value.logoutGraceSeconds);
        json.addProperty("exit_dimension", value.exitDimension);
        try {
            Files.createDirectories(configPath().getParent());
            try (Writer writer = Files.newBufferedWriter(configPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("[raid_config] 默认配置写入失败：{}", configPath(), exception);
        }
    }

    private static boolean readBoolean(JsonObject json, String key, boolean fallback) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static double clampDouble(JsonObject json, String key, double fallback,
                                      double minimum, double maximum) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clampInt(JsonObject json, String key, int fallback, int minimum, int maximum) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return Math.max(minimum, Math.min(maximum, element.getAsInt()));
    }
}
