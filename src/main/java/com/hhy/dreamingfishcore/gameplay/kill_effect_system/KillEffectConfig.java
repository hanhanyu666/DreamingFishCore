package com.hhy.dreamingfishcore.gameplay.kill_effect_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.rank_system.RankTier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Configuration for rank levels that can receive kill effects. */
public final class KillEffectConfig {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final List<Integer> DEFAULT_ELIGIBLE_RANK_LEVELS = List.of(
            RankTier.FISH_PLUS.level(),
            RankTier.FISH_PLUS_PLUS.level(),
            RankTier.MYTH.level());

    private static final int LEGACY_SCHEMA_VERSION = 1;
    private static final List<Integer> LEGACY_DEFAULT_ELIGIBLE_RANK_LEVELS = List.of(1, 2, 3);

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    private static volatile KillEffectConfig current = defaults();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private List<Integer> eligibleRankLevels = new ArrayList<>(DEFAULT_ELIGIBLE_RANK_LEVELS);
    private transient Set<Integer> eligibleRankLevelLookup = Set.of();

    public KillEffectConfig() {
        rebuildLookup();
    }

    public KillEffectConfig(List<Integer> eligibleRankLevels) {
        this.eligibleRankLevels = normalize(eligibleRankLevels == null
                ? DEFAULT_ELIGIBLE_RANK_LEVELS
                : eligibleRankLevels);
        rebuildLookup();
    }

    public static KillEffectConfig load() {
        return load(getConfigPath());
    }

    /** Loads the configured snapshot for runtime systems. */
    public static synchronized KillEffectConfig init() {
        current = load();
        return current;
    }

    /** Reloads the configured snapshot for runtime systems. */
    public static synchronized KillEffectConfig reload() {
        current = load();
        return current;
    }

    /** Returns the immutable-by-contract snapshot used by runtime queries. */
    public static KillEffectConfig current() {
        return current;
    }

    /** Performs an O(1) lookup against the in-memory snapshot; it never reads disk. */
    public static boolean isRankLevelEligible(int rankLevel) {
        return current.eligibleRankLevelLookup.contains(rankLevel);
    }

    /**
     * Loads a config from the supplied path. The overload keeps tests and tools independent of FML paths.
     */
    public static synchronized KillEffectConfig load(Path path) {
        KillEffectConfig defaults = defaults();

        if (Files.notExists(path)) {
            try {
                JsonDataStore.writeAtomic(path, GSON, defaults);
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("击杀特效配置默认文件创建失败：{}", path, exception);
            }
            return defaults;
        }

        try {
            if (Files.size(path) == 0L) {
                throw new IOException("配置文件为空");
            }

            KillEffectConfig loaded = JsonDataStore.read(
                    path,
                    GSON,
                    KillEffectConfig.class,
                    KillEffectConfig::new);
            if (loaded.validateAndNormalize()) {
                try {
                    JsonDataStore.writeAtomic(path, GSON, loaded);
                } catch (IOException exception) {
                    DreamingFishCore.LOGGER.warn("击杀特效配置迁移结果写回失败，将继续使用内存中的新配置：{}", path, exception);
                }
            }
            return loaded;
        } catch (Exception exception) {
            // An existing damaged file is deliberately left untouched.
            DreamingFishCore.LOGGER.warn("击杀特效配置损坏，已仅使用内存默认值：{}", path, exception);
            return defaults;
        }
    }

    public static Path getConfigPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve("kill_effect.json");
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public List<Integer> getEligibleRankLevels() {
        return List.copyOf(eligibleRankLevels);
    }

    public boolean isEligibleRankLevel(int rankLevel) {
        return eligibleRankLevels.contains(rankLevel);
    }

    private static KillEffectConfig defaults() {
        return new KillEffectConfig(DEFAULT_ELIGIBLE_RANK_LEVELS);
    }

    private boolean validateAndNormalize() {
        boolean legacyConfig = schemaVersion == LEGACY_SCHEMA_VERSION;
        if (!legacyConfig && schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的击杀特效配置版本：" + schemaVersion);
        }

        List<Integer> previousLevels = eligibleRankLevels == null
                ? null
                : new ArrayList<>(eligibleRankLevels);
        if (eligibleRankLevels == null) {
            eligibleRankLevels = new ArrayList<>(DEFAULT_ELIGIBLE_RANK_LEVELS);
        } else if (legacyConfig && normalizeLegacy(eligibleRankLevels)
                .equals(LEGACY_DEFAULT_ELIGIBLE_RANK_LEVELS)) {
            eligibleRankLevels = new ArrayList<>(DEFAULT_ELIGIBLE_RANK_LEVELS);
        } else if (legacyConfig) {
            eligibleRankLevels = normalize(normalizeLegacy(eligibleRankLevels));
        } else {
            eligibleRankLevels = normalize(eligibleRankLevels);
        }
        schemaVersion = CURRENT_SCHEMA_VERSION;
        rebuildLookup();
        return legacyConfig || previousLevels == null || !eligibleRankLevels.equals(previousLevels);
    }

    private static List<Integer> normalize(List<Integer> levels) {
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer level : levels) {
            if (level != null
                    && level >= RankTier.FISH_PLUS.level()
                    && level <= RankTier.MYTH.level()) {
                normalized.add(level);
            }
        }
        return new ArrayList<>(normalized);
    }

    private static List<Integer> normalizeLegacy(List<Integer> levels) {
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer level : levels) {
            if (level != null && level >= 0) {
                normalized.add(switch (level) {
                    case 5 -> RankTier.FISH.level();
                    case 6 -> RankTier.FISH_PLUS.level();
                    case 7 -> RankTier.FISH_PLUS_PLUS.level();
                    case 8 -> RankTier.MYTH.level();
                    default -> level;
                });
            }
        }
        return new ArrayList<>(normalized);
    }

    private void rebuildLookup() {
        eligibleRankLevelLookup = Set.copyOf(eligibleRankLevels);
    }
}
