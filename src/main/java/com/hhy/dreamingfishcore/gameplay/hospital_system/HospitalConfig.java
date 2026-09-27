package com.hhy.dreamingfishcore.gameplay.hospital_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/** 仅配置人数及每日材料，不承载流程脚本。医院由余梦期入口自动发布。 */
public final class HospitalConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Settings current = Settings.defaults();
    private HospitalConfig() { }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("dreamingfishcore/hospital.json");
    }

    public static Settings get() { return current; }
    public static void clear() { current = Settings.defaults(); }

    public static void reload() {
        try {
            if (Files.notExists(path())) JsonDataStore.writeAtomic(path(), GSON, Settings.defaults());
            if (Files.size(path()) > 16384) throw new IllegalArgumentException("医院配置超过大小限制");
            Settings candidate = GSON.fromJson(Files.readString(path(), StandardCharsets.UTF_8), Settings.class);
            if (candidate == null) throw new IllegalArgumentException("医院配置不能为空");
            candidate.validate();
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(candidate.dailyItemId())) == net.minecraft.world.item.Items.AIR) {
                throw new IllegalArgumentException("每日维护物品不存在：" + candidate.dailyItemId());
            }
            current = candidate;
        } catch (Exception exception) {
            throw new IllegalStateException("医院配置读取失败：" + exception.getMessage(), exception);
        }
    }

    public record Settings(int schemaVersion, int requiredPlayers, String dailyItemId,
                           int dailyItemCount, float dailyRestorePoints) {
        public static Settings defaults() {
            return new Settings(1, 3, "minecraft:golden_apple", 2, 5);
        }

        public void validate() {
            if (schemaVersion != 1 || requiredPlayers < 1 || requiredPlayers > 16384
                    || dailyItemId == null || !dailyItemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || dailyItemId.length() > 128 || dailyItemCount < 1 || dailyItemCount > 64
                    || !Float.isFinite(dailyRestorePoints) || dailyRestorePoints <= 0 || dailyRestorePoints > 100) {
                throw new IllegalArgumentException("医院配置的版本、人数、物品或恢复量非法");
            }
        }
    }
}
