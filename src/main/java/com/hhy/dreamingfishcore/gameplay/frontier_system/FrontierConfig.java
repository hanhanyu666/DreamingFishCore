package com.hhy.dreamingfishcore.gameplay.frontier_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
 * 外缘（frontier）规则开关：{@code config/dreamingfishcore/frontier.json}。
 *
 * <p>目的是"把危险赶到外缘去"：感染增长、重生代价这类惩罚，只在外缘生效；
 * 主城的日常生存、建设、生电不受影响（这是城邦/危机方向的前提）。</p>
 *
 * <p><b>默认关闭</b>：开关关着时行为与现在完全一致，避免在还没铺开外缘地图时误伤现有玩法。</p>
 *
 * <p>路径**懒加载**：{@code FMLPaths} 需要 FML 初始化，放静态字段里会让普通单测
 * 一碰这个类就 ExceptionInInitializerError（这个坑本项目踩过）。</p>
 */
public final class FrontierConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "frontier.json";

    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(DreamingFishCore.MODID).resolve(FILE_NAME);
    }

    private static volatile Boolean cached;

    private FrontierConfig() {
    }

    /** 惩罚是否只在外缘生效（默认 false = 维持现状）。 */
    public static boolean penaltiesOnlyInFrontier() {
        Boolean value = cached;
        if (value == null) {
            value = load();
            cached = value;
        }
        return value;
    }

    /** 供命令/重载使用。 */
    public static synchronized void reload() {
        cached = load();
    }

    private static boolean load() {
        Path path = configPath();
        if (!Files.isRegularFile(path)) {
            saveDefault(path);
            return false;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                return false;
            }
            JsonObject json = element.getAsJsonObject();
            JsonElement flag = json.get("penalties_only_in_frontier");
            if (flag == null || !flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) {
                return false;
            }
            return flag.getAsBoolean();
        } catch (IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.error("[frontier] 配置读取失败，按「关闭」处理：{}", path, exception);
            return false;
        }
    }

    private static void saveDefault(Path path) {
        JsonObject json = new JsonObject();
        json.addProperty("penalties_only_in_frontier", false);
        json.addProperty("_comment", "开启后：感染增长与重生代价只在「外缘」（启用了尸潮开关的任务地点）生效");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.warn("[frontier] 默认配置写入失败：{}", path, exception);
        }
    }
}
