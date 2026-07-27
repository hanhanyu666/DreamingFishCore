package com.hhy.dreamingfishcore.gameplay.playerattributes_system.limb_health_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import net.minecraftforge.fml.loading.FMLPaths;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 肢体伤害配置类
 * <p>
 * 管理各肢体部位的伤害倍率，支持从 JSON 配置文件加载
 */
public class LimbDamageConfig {

    private static final String CONFIG_FILE = "dreamingfishcore_limb_damage.json";
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);

    /**
     * 默认伤害倍率
     */
    private static final Map<String, Float> DEFAULT_MULTIPLIERS = new HashMap<>();

    static {
        DEFAULT_MULTIPLIERS.put("HEAD", 1.2F);
        DEFAULT_MULTIPLIERS.put("CHEST", 1.0F);
        DEFAULT_MULTIPLIERS.put("LEGS", 0.9F);
        DEFAULT_MULTIPLIERS.put("FEET", 0.9F);
    }

    /**
     * 当前伤害倍率配置
     */
    private static final Map<String, Float> multipliers = new HashMap<>(DEFAULT_MULTIPLIERS);

    /**
     * GSON 实例
     */
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * 初始化配置
     */
    public static void init() {
        // 先加载默认值
        multipliers.putAll(DEFAULT_MULTIPLIERS);

        // 尝试从文件加载
        // if (Files.exists(CONFIG_PATH)) {
        //     loadFromFile();
        // } else {
        //     // 文件不存在，创建默认配置文件
        //     saveToFile();
        // }

        DreamingFishCore.LOGGER.info("肢体伤害系统配置已加载: 头部×{}, 胸部×{}, 腿部×{}, 脚部×{}",
                multipliers.get("HEAD"),
                multipliers.get("CHEST"),
                multipliers.get("LEGS"),
                multipliers.get("FEET"));
    }

    /**
     * 从文件加载配置
     */
    private static void loadFromFile() {
        try {
            Type mapType = new TypeToken<Map<String, Float>>() {
            }.getType();
            Map<String, Float> loaded = JsonDataStore.read(
                    CONFIG_PATH,
                    GSON,
                    mapType,
                    HashMap::new);

            multipliers.clear();
            multipliers.putAll(DEFAULT_MULTIPLIERS);
            multipliers.putAll(loaded);
            DreamingFishCore.LOGGER.info("肢体伤害配置已从文件加载: {}", CONFIG_PATH);
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("加载肢体伤害配置文件失败: {}", exception.getMessage());
            multipliers.putAll(DEFAULT_MULTIPLIERS);
        }
    }

    /**
     * 保存配置到文件
     */
    public static void saveToFile() {
        try {
            JsonDataStore.writeAtomic(CONFIG_PATH, GSON, multipliers);
            DreamingFishCore.LOGGER.info("肢体伤害配置已保存到文件: {}", CONFIG_PATH);
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn("保存肢体伤害配置文件失败: {}", exception.getMessage());
        }
    }

    /**
     * 获取部位的伤害倍率
     *
     * @param limbType 肢体部位
     * @return 伤害倍率
     */
    public static float getMultiplier(LimbType limbType) {
        return multipliers.getOrDefault(limbType.name(), 1.0F);
    }

    /**
     * 设置部位的伤害倍率
     *
     * @param limbType   肢体部位
     * @param multiplier 伤害倍率
     */
    public static void setMultiplier(LimbType limbType, float multiplier) {
        multipliers.put(limbType.name(), multiplier);
    }

    /**
     * 重置为默认值
     */
    public static void resetToDefault() {
        multipliers.clear();
        multipliers.putAll(DEFAULT_MULTIPLIERS);
        // saveToFile(); // 不生成配置文件
    }

    /**
     * 获取当前所有倍率配置
     */
    public static Map<String, Float> getAllMultipliers() {
        return new HashMap<>(multipliers);
    }
}
