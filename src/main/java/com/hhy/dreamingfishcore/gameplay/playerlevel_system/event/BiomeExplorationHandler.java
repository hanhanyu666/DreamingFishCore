package com.hhy.dreamingfishcore.gameplay.playerlevel_system.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.overalllevel.PlayerLevelManager;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.network.Packet_BiomeDiscoveryNotify;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.biome.PlayerBiomesDataManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 生物群系探索处理器
 * 检测玩家进入新生物群系并记录探索进度
 *
 * 参考原版 BiomeAmbientSoundsHandler 的实现方式
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class BiomeExplorationHandler {

    // ==================== 配置项 ====================
    /** 原版生物群系经验奖励（minecraft 命名空间） */
    private static final long VANILLA_BIOME_EXPERIENCE = 100L;

    /** 其他模组生物群系经验奖励（非 minecraft 命名空间） */
    private static final long MOD_BIOME_EXPERIENCE = 120L;

    /** 判断原版生物群系的命名空间 */
    private static final String VANILLA_NAMESPACE = "minecraft";

    // =================================================

    /**
     * 玩家生物群系缓存数据
     */
    private static class BiomeCacheEntry {
        Holder<Biome> lastBiome;
        ResourceLocation lastDimension;

        BiomeCacheEntry(Holder<Biome> biome, ResourceLocation dimension) {
            this.lastBiome = biome;
            this.lastDimension = dimension;
        }
    }

    // 使用 ThreadLocal 避免 HashMap 的并发开销，每个服务器线程有独立的缓存
    private static final ThreadLocal<PlayerBiomeCache> cache = ThreadLocal.withInitial(PlayerBiomeCache::new);

    /**
     * 玩家生物群系缓存，使用弱引用避免内存泄漏
     */
    private static class PlayerBiomeCache {
        private final java.util.Map<UUID, BiomeCacheEntry> cache = new java.util.WeakHashMap<>();

        BiomeCacheEntry get(UUID uuid) {
            return cache.get(uuid);
        }

        void put(UUID uuid, BiomeCacheEntry entry) {
            cache.put(uuid, entry);
        }

        void remove(UUID uuid) {
            cache.remove(uuid);
        }
    }

    /**
     * 定期检查玩家是否进入新生物群系（每秒检查一次，即每20 tick）
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !AuthSessionGuard.isAuthenticated(player)) return;
        if (player.level().isClientSide) return;
        if (player.tickCount % 20 != 0) return;

        UUID playerUUID = player.getUUID();
        BlockPos pos = player.blockPosition();

        // 获取当前生物群系和维度
        Holder<Biome> currentBiome = player.level().getBiome(pos);
        ResourceLocation currentDimension = player.level().dimension().location();

        // 获取缓存
        PlayerBiomeCache playerCache = cache.get();
        BiomeCacheEntry lastEntry = playerCache.get(playerUUID);

        // 检查生物群系或维度是否变化
        if (lastEntry != null
                && Objects.equals(lastEntry.lastBiome, currentBiome)
                && Objects.equals(lastEntry.lastDimension, currentDimension)) {
            return; // 还在同一个生物群系中
        }

        // 获取生物群系ID用于记录
        ResourceLocation biomeId = currentBiome.unwrapKey()
                .map(key -> key.location())
                .orElse(null);

        if (biomeId == null) {
            return;
        }

        // 更新缓存
        playerCache.put(playerUUID, new BiomeCacheEntry(currentBiome, currentDimension));

        // 构建生物群系唯一键（包含维度信息）
        String biomeKey = currentDimension + ":" + biomeId;

        // 尝试添加探索记录
        boolean isNewBiome = PlayerBiomesDataManager.addExploredBiome(playerUUID, biomeKey);

        onBiomeEntered(player, biomeId, biomeKey, isNewBiome);
    }

    /**
     * 玩家登出时清理缓存
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cache.get().remove(player.getUUID());
        }
    }

    /**
     * 处理玩家进入生物群系
     */
    private static void onBiomeEntered(ServerPlayer player, ResourceLocation biomeId,
                                       String biomeKey, boolean isNewBiome) {
        if (!AuthSessionGuard.isAuthenticated(player)) {
            return;
        }
        int totalExplored = PlayerBiomesDataManager.getExploredBiomeCount(player.getUUID());

        long expReward = isNewBiome ? calculateExperienceReward(biomeId) : 0L;

        DreamingFishCore_NetworkManager.sendToClient(
                new Packet_BiomeDiscoveryNotify(
                        biomeId.toString(),
                        getBiomeDisplayName(biomeId),
                        totalExplored,
                        expReward,
                        isNewBiome
                ),
                player
        );

        if (!isNewBiome) {
            return;
        }

        PlayerLevelManager.addPlayerExperienceServer(player, expReward);

        DreamingFishCore.LOGGER.info("玩家 {} 发现新生物群系：{}，总计 {} 个，获得 {} 经验",
                player.getScoreboardName(), biomeKey, totalExplored, expReward);
    }

    /**
     * 计算探索新生物群系的经验奖励
     * @param biomeId 生物群系ID
     * @return 经验值
     */
    private static long calculateExperienceReward(ResourceLocation biomeId) {
        // 原版生物群系 vs 模组生物群系
        boolean experienceReward = VANILLA_NAMESPACE.equals(biomeId.getNamespace());
        return experienceReward ? VANILLA_BIOME_EXPERIENCE : MOD_BIOME_EXPERIENCE;
    }

    /**
     * 查询玩家已探索的生物群系列表
     * 可通过命令调用此方法
     */
    public static void showExploredBiomes(ServerPlayer player) {
        Set<String> exploredBiomes = PlayerBiomesDataManager.getExploredBiomes(player.getUUID());
        int count = exploredBiomes.size();

        player.sendSystemMessage(Component.literal("§6========== 生物群系探索进度 =========="));
        player.sendSystemMessage(Component.literal("§e已探索生物群系数量：§f" + count));
        player.sendSystemMessage(Component.literal("§7-------------------------------------"));

        if (count > 0) {
            // 显示最近探索的几个生物群系
            exploredBiomes.stream()
                    .skip(Math.max(0, count - 10))
                    .forEach(biomeKey -> {
                        String[] parts = biomeKey.split(":", 3);
                        if (parts.length >= 3) {
                            String dimension = parts[1];
                            String biome = parts[2];
                            player.sendSystemMessage(Component.literal(
                                String.format("§8[§7%s§8] §f%s", dimension, biome)
                            ));
                        }
                    });
        } else {
            player.sendSystemMessage(Component.literal("§c你还没有探索任何生物群系！"));
        }

        player.sendSystemMessage(Component.literal("§6======================================"));
    }

    /**
     * 获取生物群系的显示名称
     */
    public static String getBiomeDisplayName(ResourceLocation biomeId) {
        if (VANILLA_NAMESPACE.equals(biomeId.getNamespace())) {
            String vanillaName = getVanillaBiomeChineseName(biomeId.getPath());
            if (vanillaName != null) {
                return vanillaName;
            }
        }

        String displayName = formatBiomePath(biomeId.getPath());
        if (!VANILLA_NAMESPACE.equals(biomeId.getNamespace())) {
            return biomeId.getNamespace() + " · " + displayName;
        }
        return displayName;
    }

    private static String getVanillaBiomeChineseName(String path) {
        return switch (path) {
            case "badlands" -> "恶地";
            case "bamboo_jungle" -> "竹林";
            case "basalt_deltas" -> "玄武岩三角洲";
            case "beach" -> "沙滩";
            case "birch_forest" -> "桦木森林";
            case "cherry_grove" -> "樱花树林";
            case "cold_ocean" -> "冷水海洋";
            case "crimson_forest" -> "绯红森林";
            case "dark_forest" -> "黑森林";
            case "deep_cold_ocean" -> "冷水深海";
            case "deep_dark" -> "深暗之域";
            case "deep_frozen_ocean" -> "冰冻深海";
            case "deep_lukewarm_ocean" -> "温水深海";
            case "deep_ocean" -> "深海";
            case "desert" -> "沙漠";
            case "dripstone_caves" -> "溶洞";
            case "end_barrens" -> "末地荒地";
            case "end_highlands" -> "末地高地";
            case "end_midlands" -> "末地内陆";
            case "eroded_badlands" -> "风蚀恶地";
            case "flower_forest" -> "繁花森林";
            case "forest" -> "森林";
            case "frozen_ocean" -> "冰冻海洋";
            case "frozen_peaks" -> "冰封山峰";
            case "frozen_river" -> "冰冻河流";
            case "grove" -> "雪林";
            case "ice_spikes" -> "冰刺平原";
            case "jagged_peaks" -> "尖峭山峰";
            case "jungle" -> "丛林";
            case "lukewarm_ocean" -> "温水海洋";
            case "lush_caves" -> "繁茂洞穴";
            case "mangrove_swamp" -> "红树林沼泽";
            case "meadow" -> "草甸";
            case "mushroom_fields" -> "蘑菇岛";
            case "nether_wastes" -> "下界荒地";
            case "ocean" -> "海洋";
            case "old_growth_birch_forest" -> "原始桦木森林";
            case "old_growth_pine_taiga" -> "原始松木针叶林";
            case "old_growth_spruce_taiga" -> "原始云杉针叶林";
            case "pale_garden" -> "苍白之园";
            case "plains" -> "平原";
            case "river" -> "河流";
            case "savanna" -> "热带草原";
            case "savanna_plateau" -> "热带高原";
            case "small_end_islands" -> "末地小型岛屿";
            case "snowy_beach" -> "积雪沙滩";
            case "snowy_plains" -> "积雪平原";
            case "snowy_slopes" -> "积雪山坡";
            case "snowy_taiga" -> "积雪针叶林";
            case "soul_sand_valley" -> "灵魂沙峡谷";
            case "sparse_jungle" -> "稀疏丛林";
            case "stony_peaks" -> "裸岩山峰";
            case "stony_shore" -> "石岸";
            case "sunflower_plains" -> "向日葵平原";
            case "swamp" -> "沼泽";
            case "taiga" -> "针叶林";
            case "the_end" -> "末地";
            case "the_void" -> "虚空";
            case "warm_ocean" -> "暖水海洋";
            case "warped_forest" -> "诡异森林";
            case "windswept_forest" -> "风袭森林";
            case "windswept_gravelly_hills" -> "风袭沙砾丘陵";
            case "windswept_hills" -> "风袭丘陵";
            case "windswept_savanna" -> "风袭热带草原";
            case "wooded_badlands" -> "疏林恶地";
            default -> null;
        };
    }

    private static String formatBiomePath(String path) {
        String[] words = path.split("_");
        StringBuilder displayName = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (!displayName.isEmpty()) {
                displayName.append(' ');
            }
            displayName.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                displayName.append(word.substring(1));
            }
        }
        return displayName.toString();
    }
}
