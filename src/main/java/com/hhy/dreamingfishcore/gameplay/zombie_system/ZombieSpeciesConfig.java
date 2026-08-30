package com.hhy.dreamingfishcore.gameplay.zombie_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server-side configuration for the siege zombie and its story-gated abilities.
 *
 * <p>The file is intentionally JSON rather than a static {@code ModConfigSpec}.
 * Story stage IDs are content owned and can be added by a server operator without
 * rebuilding the mod.  A stage override only needs to contain the fields that it
 * wants to change; all omitted fields inherit the defaults above it.</p>
 */
public final class ZombieSpeciesConfig {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String CONFIG_FILE_NAME = "zombie_species.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._:/-]{0,127}");
    private static final Set<String> DEFAULT_PROTECTED_BLOCKS = Set.of(
            "minecraft:bedrock",
            "minecraft:barrier",
            "minecraft:end_portal",
            "minecraft:end_portal_frame",
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block",
            "minecraft:structure_block",
            "minecraft:jigsaw"
    );

    private static volatile ZombieSpeciesConfig current = defaults();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private boolean enabled = true;
    private double speedMultiplier = 1.35D;
    private boolean digging = true;
    private boolean openDoors = true;
    private boolean breakingDoors = true;
    private boolean placingBlocks = true;
    private boolean stacking = true;
    /** Enables the movement-sound listener used when a player is out of sight. */
    private boolean hearing = true;
    /** Enables alert broadcasts and bounded relay waves between siege zombies. */
    private boolean broadcasting = true;
    /** Fans visible attackers around a player instead of driving every path into one point. */
    private boolean surrounding = true;
    /** Prevents this species from mining, placing blocks, or breaking doors in task locations. */
    private boolean taskLocationProtection = true;
    /** Grants Regeneration I to players inside an active task location. */
    private boolean taskLocationRegeneration = true;
    /** Prevents naturally spawned hostile monsters from appearing in task locations. */
    private boolean taskLocationSpawnProtection = true;

    /** Maximum distance at which the vanilla player target goal may acquire a player. */
    private double trackingRange = 45.0D;
    /** Radius in which a moving player's server-side footstep signal can be heard. */
    private double hearingRange = 45.0D;
    /** Radius of one alert broadcast hop. */
    private double broadcastRange = 20.0D;
    /** Hard hop limit for one broadcast wave; protects a large horde from an O(n^2) storm. */
    private int broadcastMaxHops = 4;
    /** Maximum recipients/enqueued relay nodes for one wave. */
    private int broadcastMaxRecipients = 64;
    /** Minimum ticks between movement-sound samples for one player. */
    private int hearingCooldownTicks = 6;
    /** Minimum ticks between waves for the same target. */
    private int broadcastCooldownTicks = 10;
    /** How long a hidden target remains owned by the alert goal after a sound/broadcast. */
    private int alertMemoryTicks = 120;
    /** Hidden sounds inside this radius refresh memory without moving the pursuit anchor. */
    private double alertRetargetDistance = 4.0D;
    /** Minimum time a zombie keeps its selected exterior breach anchor. */
    private int breachCommitmentTicks = 160;
    /** Distance from the player at which the final-path surrounding steer starts. */
    private double surroundActivationRange = 12.0D;
    /** Preferred horizontal ring radius around the player. */
    private double surroundRadius = 2.4D;
    /** Blend weight applied to the original navigation direction near the ring. */
    private double surroundSteeringStrength = 0.55D;

    private int digRange = 8;
    private float maxDigHardness = 3.0F;
    private int digCooldownTicks = 0;
    private List<String> diggableBlocks = new ArrayList<>();
    private List<String> protectedBlocks = new ArrayList<>(DEFAULT_PROTECTED_BLOCKS);

    private String placementBlock = "minecraft:cobblestone";
    private int placementBlocks = 16;
    private int placeRange = 4;
    private int placeCooldownTicks = 20;

    private int stackSearchRange = 6;
    private int maxStackHeight = 3;
    /** Minimum target-feet height above an unmounted zombie before stacking is considered. */
    private double stackMinimumTargetHeight = 4.0D;
    private int stackDurationTicks = 16;
    private int stackCooldownTicks = 40;
    private double stackJumpVelocity = 0.52D;
    private double stackJumpHorizontalSpeed = 0.20D;

    /** Enables the natural monster-pool zombie split. */
    private boolean naturalSpawn = true;
    /** Total zombie-family weight relative to the original entry (120 = +20%). */
    private int zombieFamilySpawnPercent = 120;
    /** Share of the scaled zombie-family weight retained by vanilla. */
    private int vanillaZombieSpawnPercent = 40;
    /** Share of the scaled zombie-family weight assigned to the new species. */
    private int customZombieSpawnPercent = 60;
    /** Weight multiplier for every non-zombie hostile entry (80 = -20%). */
    private int otherMonsterSpawnPercent = 80;

    private Map<String, StageOverride> stageOverrides = new LinkedHashMap<>();

    /** Gson needs a public no-argument constructor. */
    public ZombieSpeciesConfig() {
    }

    /** Loads the configured snapshot, creating a documented default file when absent. */
    public static synchronized ZombieSpeciesConfig init() {
        current = load(getConfigPath());
        return current;
    }

    /** Reloads the file without restarting the server. Existing entities pick it up within a tick. */
    public static synchronized ZombieSpeciesConfig reload() {
        return reload(getConfigPath());
    }

    /**
     * Strict reload overload used by commands and tests. A damaged edit must not
     * replace the last-known-good story gates with permissive defaults.
     */
    public static synchronized ZombieSpeciesConfig reload(Path path) {
        if (Files.notExists(path)) {
            current = load(path);
            return current;
        }
        try {
            ZombieSpeciesConfig loaded = readValidated(path);
            current = loaded;
            return loaded;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.warn(
                    "丧尸物种配置重载失败，继续使用上一份有效配置：{}", path, exception);
            throw new IllegalStateException("丧尸物种配置无效，已保留上一份有效设置", exception);
        }
    }

    public static ZombieSpeciesConfig current() {
        return current;
    }

    public static Path getConfigPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve(CONFIG_FILE_NAME);
    }

    /**
     * Persists one ability override for a story stage.  Commands edit a copy
     * first, so a failed validation or atomic write cannot partially mutate the
     * live server configuration.
     */
    public static synchronized ResolvedSettings setAbilityForStage(
            String stageId,
            Ability ability,
            boolean enabled) {
        return setAbilityForStage(getConfigPath(), stageId, ability, enabled);
    }

    static synchronized ResolvedSettings setAbilityForStage(
            Path path,
            String stageId,
            Ability ability,
            boolean enabled) {
        if (path == null) {
            throw new IllegalArgumentException("配置路径不能为空");
        }
        if (ability == null) {
            throw new IllegalArgumentException("能力不能为空");
        }
        String validatedStageId = requireId(stageId, "stageId");

        ZombieSpeciesConfig candidate = GSON.fromJson(GSON.toJson(current), ZombieSpeciesConfig.class);
        if (candidate == null) {
            throw new IllegalStateException("无法复制当前丧尸配置");
        }
        StageOverride override = candidate.stageOverrides.computeIfAbsent(
                validatedStageId, ignored -> new StageOverride());
        override.setAbility(ability, enabled);
        candidate.validateAndNormalize();

        try {
            JsonDataStore.writeAtomic(path, GSON, candidate);
        } catch (IOException exception) {
            throw new IllegalStateException("保存丧尸物种配置失败", exception);
        }
        current = candidate;
        return candidate.resolveForStage(validatedStageId);
    }

    /**
     * Persists one atomic all-abilities toggle for a story stage.  This is
     * used by the in-game shortcut so a failed write can never leave only a
     * subset of the ability gates changed.
     */
    public static synchronized ResolvedSettings setAllAbilitiesForStage(
            String stageId,
            boolean enabled) {
        return setAllAbilitiesForStage(getConfigPath(), stageId, enabled);
    }

    static synchronized ResolvedSettings setAllAbilitiesForStage(
            Path path,
            String stageId,
            boolean enabled) {
        if (path == null) {
            throw new IllegalArgumentException("配置路径不能为空");
        }
        String validatedStageId = requireId(stageId, "stageId");

        ZombieSpeciesConfig candidate = GSON.fromJson(GSON.toJson(current), ZombieSpeciesConfig.class);
        if (candidate == null) {
            throw new IllegalStateException("无法复制当前丧尸配置");
        }
        StageOverride override = candidate.stageOverrides.computeIfAbsent(
                validatedStageId, ignored -> new StageOverride());
        for (Ability ability : Ability.values()) {
            override.setAbility(ability, enabled);
        }
        candidate.validateAndNormalize();

        try {
            JsonDataStore.writeAtomic(path, GSON, candidate);
        } catch (IOException exception) {
            throw new IllegalStateException("保存丧尸物种配置失败", exception);
        }
        current = candidate;
        return candidate.resolveForStage(validatedStageId);
    }

    public static synchronized ZombieSpeciesConfig load(Path path) {
        ZombieSpeciesConfig defaults = defaults();
        if (Files.notExists(path)) {
            try {
                JsonDataStore.writeAtomic(path, GSON, defaults);
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("丧尸物种配置默认文件创建失败：{}", path, exception);
            }
            return defaults;
        }

        try {
            return readValidated(path);
        } catch (Exception exception) {
            // Never overwrite an operator's damaged file. The in-memory defaults keep the server running.
            DreamingFishCore.LOGGER.warn("丧尸物种配置损坏，已仅使用内存默认值：{}", path, exception);
            return defaults;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public int getDigRange() {
        return digRange;
    }

    public float getMaxDigHardness() {
        return maxDigHardness;
    }

    public int getDigCooldownTicks() {
        return digCooldownTicks;
    }

    public int getPlacementBlocks() {
        return placementBlocks;
    }

    public int getPlaceRange() {
        return placeRange;
    }

    public int getPlaceCooldownTicks() {
        return placeCooldownTicks;
    }

    public int getStackSearchRange() {
        return stackSearchRange;
    }

    public int getMaxStackHeight() {
        return maxStackHeight;
    }

    public int getStackDurationTicks() {
        return stackDurationTicks;
    }

    public int getStackCooldownTicks() {
        return stackCooldownTicks;
    }

    public double getStackJumpVelocity() {
        return stackJumpVelocity;
    }

    public double getStackJumpHorizontalSpeed() {
        return stackJumpHorizontalSpeed;
    }

    public boolean isNaturalSpawn() {
        return naturalSpawn;
    }

    public int getZombieFamilySpawnPercent() {
        return zombieFamilySpawnPercent;
    }

    public int getVanillaZombieSpawnPercent() {
        return vanillaZombieSpawnPercent;
    }

    public int getCustomZombieSpawnPercent() {
        return customZombieSpawnPercent;
    }

    public int getOtherMonsterSpawnPercent() {
        return otherMonsterSpawnPercent;
    }

    public double getTrackingRange() {
        return trackingRange;
    }

    public double getHearingRange() {
        return hearingRange;
    }

    public double getBroadcastRange() {
        return broadcastRange;
    }

    public int getBroadcastMaxHops() {
        return broadcastMaxHops;
    }

    public int getBroadcastMaxRecipients() {
        return broadcastMaxRecipients;
    }

    public int getHearingCooldownTicks() {
        return hearingCooldownTicks;
    }

    public int getBroadcastCooldownTicks() {
        return broadcastCooldownTicks;
    }

    public int getAlertMemoryTicks() {
        return alertMemoryTicks;
    }

    public double getAlertRetargetDistance() {
        return alertRetargetDistance;
    }

    public int getBreachCommitmentTicks() {
        return breachCommitmentTicks;
    }

    public double getSurroundActivationRange() {
        return surroundActivationRange;
    }

    public double getSurroundRadius() {
        return surroundRadius;
    }

    public double getSurroundSteeringStrength() {
        return surroundSteeringStrength;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    private static ZombieSpeciesConfig readValidated(Path path) throws IOException {
        if (Files.size(path) == 0L) {
            throw new IOException("配置文件为空");
        }
        ZombieSpeciesConfig loaded = JsonDataStore.read(
                path, GSON, ZombieSpeciesConfig.class, ZombieSpeciesConfig::new);
        loaded.validateAndNormalize();
        return loaded;
    }

    /** Resolves the settings for a stage ID; useful for tests and management commands. */
    public ResolvedSettings resolveForStage(String stageId) {
        StageOverride override = stageId == null ? null : stageOverrides.get(stageId);
        return new ResolvedSettings(
                valueOr(override == null ? null : override.enabled, enabled),
                valueOr(override == null ? null : override.digging, digging),
                valueOr(override == null ? null : override.openDoors, openDoors),
                valueOr(override == null ? null : override.breakingDoors, breakingDoors),
                valueOr(override == null ? null : override.placingBlocks, placingBlocks),
                valueOr(override == null ? null : override.stacking, stacking),
                valueOr(override == null ? null : override.hearing, hearing),
                valueOr(override == null ? null : override.broadcasting, broadcasting),
                valueOr(override == null ? null : override.surrounding, surrounding),
                valueOr(override == null ? null : override.taskLocationProtection, taskLocationProtection),
                valueOr(override == null ? null : override.taskLocationRegeneration, taskLocationRegeneration),
                valueOr(override == null ? null : override.taskLocationSpawnProtection,
                        taskLocationSpawnProtection),
                valueOr(override == null ? null : override.speedMultiplier, speedMultiplier),
                valueOr(override == null ? null : override.trackingRange, trackingRange),
                valueOr(override == null ? null : override.hearingRange, hearingRange),
                valueOr(override == null ? null : override.broadcastRange, broadcastRange),
                valueOr(override == null ? null : override.broadcastMaxHops, broadcastMaxHops),
                valueOr(override == null ? null : override.broadcastMaxRecipients, broadcastMaxRecipients),
                valueOr(override == null ? null : override.hearingCooldownTicks, hearingCooldownTicks),
                valueOr(override == null ? null : override.broadcastCooldownTicks, broadcastCooldownTicks),
                valueOr(override == null ? null : override.alertMemoryTicks, alertMemoryTicks),
                valueOr(override == null ? null : override.alertRetargetDistance, alertRetargetDistance),
                valueOr(override == null ? null : override.breachCommitmentTicks, breachCommitmentTicks),
                valueOr(override == null ? null : override.surroundActivationRange, surroundActivationRange),
                valueOr(override == null ? null : override.surroundRadius, surroundRadius),
                valueOr(override == null ? null : override.surroundSteeringStrength, surroundSteeringStrength),
                valueOr(override == null ? null : override.digRange, digRange),
                valueOr(override == null ? null : override.maxDigHardness, maxDigHardness),
                valueOr(override == null ? null : override.digCooldownTicks, digCooldownTicks),
                valueOr(override == null ? null : override.placementBlock, placementBlock),
                valueOr(override == null ? null : override.placementBlocks, placementBlocks),
                valueOr(override == null ? null : override.placeRange, placeRange),
                valueOr(override == null ? null : override.placeCooldownTicks, placeCooldownTicks),
                valueOr(override == null ? null : override.stackSearchRange, stackSearchRange),
                valueOr(override == null ? null : override.maxStackHeight, maxStackHeight),
                valueOr(override == null ? null : override.stackMinimumTargetHeight, stackMinimumTargetHeight),
                valueOr(override == null ? null : override.stackDurationTicks, stackDurationTicks),
                valueOr(override == null ? null : override.stackCooldownTicks, stackCooldownTicks),
                valueOr(override == null ? null : override.stackJumpVelocity, stackJumpVelocity),
                valueOr(override == null ? null : override.stackJumpHorizontalSpeed, stackJumpHorizontalSpeed),
                valueOr(override == null ? null : override.naturalSpawn, naturalSpawn),
                valueOr(override == null ? null : override.zombieFamilySpawnPercent, zombieFamilySpawnPercent),
                valueOr(override == null ? null : override.vanillaZombieSpawnPercent, vanillaZombieSpawnPercent),
                valueOr(override == null ? null : override.customZombieSpawnPercent, customZombieSpawnPercent),
                valueOr(override == null ? null : override.otherMonsterSpawnPercent, otherMonsterSpawnPercent),
                diggableBlocks,
                protectedBlocks
        );
    }

    /** Resolves against the server-authoritative story stage, falling back before a world is loaded. */
    public ResolvedSettings resolveForLevel(net.minecraft.world.level.Level level) {
        String stageId = StoryManager.getCurrentStageIdOrDefault();
        return resolveForStage(stageId);
    }

    public boolean isAbilityEnabled(String stageId, Ability ability) {
        ResolvedSettings settings = resolveForStage(stageId);
        return settings.enabled() && settings.isAbilityEnabled(ability);
    }

    public Map<String, StageOverride> getStageOverrides() {
        return Map.copyOf(stageOverrides);
    }

    public enum Ability {
        DIGGING,
        OPEN_DOORS,
        BREAKING_DOORS,
        PLACING_BLOCKS,
        STACKING,
        HEARING,
        BROADCASTING,
        SURROUNDING,
        TASK_LOCATION_PROTECTION,
        TASK_LOCATION_REGENERATION,
        TASK_LOCATION_SPAWN_PROTECTION
    }

    /** Immutable runtime snapshot. A new snapshot is cheap and is refreshed when an entity notices a config change. */
    public record ResolvedSettings(
            boolean enabled,
            boolean digging,
            boolean openDoors,
            boolean breakingDoors,
            boolean placingBlocks,
            boolean stacking,
            boolean hearing,
            boolean broadcasting,
            boolean surrounding,
            boolean taskLocationProtection,
            boolean taskLocationRegeneration,
            boolean taskLocationSpawnProtection,
            double speedMultiplier,
            double trackingRange,
            double hearingRange,
            double broadcastRange,
            int broadcastMaxHops,
            int broadcastMaxRecipients,
            int hearingCooldownTicks,
            int broadcastCooldownTicks,
            int alertMemoryTicks,
            double alertRetargetDistance,
            int breachCommitmentTicks,
            double surroundActivationRange,
            double surroundRadius,
            double surroundSteeringStrength,
            int digRange,
            float maxDigHardness,
            int digCooldownTicks,
            String placementBlock,
            int placementBlocks,
            int placeRange,
            int placeCooldownTicks,
            int stackSearchRange,
            int maxStackHeight,
            double stackMinimumTargetHeight,
            int stackDurationTicks,
            int stackCooldownTicks,
            double stackJumpVelocity,
            double stackJumpHorizontalSpeed,
            boolean naturalSpawn,
            int zombieFamilySpawnPercent,
            int vanillaZombieSpawnPercent,
            int customZombieSpawnPercent,
            int otherMonsterSpawnPercent,
            List<String> diggableBlocks,
            List<String> protectedBlocks) {

        public ResolvedSettings {
            // Keep snapshots safe from accidental mutation by an AI goal or a
            // command implementation after a reload.
            diggableBlocks = List.copyOf(diggableBlocks == null ? List.of() : diggableBlocks);
            protectedBlocks = List.copyOf(protectedBlocks == null ? List.of() : protectedBlocks);
        }

        public boolean isAbilityEnabled(Ability ability) {
            return switch (ability) {
                case DIGGING -> digging;
                case OPEN_DOORS -> openDoors;
                case BREAKING_DOORS -> breakingDoors;
                case PLACING_BLOCKS -> placingBlocks;
                case STACKING -> stacking;
                case HEARING -> hearing;
                case BROADCASTING -> broadcasting;
                case SURROUNDING -> surrounding;
                case TASK_LOCATION_PROTECTION -> taskLocationProtection;
                case TASK_LOCATION_REGENERATION -> taskLocationRegeneration;
                case TASK_LOCATION_SPAWN_PROTECTION -> taskLocationSpawnProtection;
            };
        }

        public ResourceLocation placementBlockId() {
            return ResourceLocation.parse(placementBlock);
        }

        public Set<ResourceLocation> diggableBlockIds() {
            return parseIds(diggableBlocks);
        }

        public Set<ResourceLocation> protectedBlockIds() {
            return parseIds(protectedBlocks);
        }

        private static Set<ResourceLocation> parseIds(List<String> values) {
            Set<ResourceLocation> result = new LinkedHashSet<>();
            if (values != null) {
                for (String value : values) {
                    if (value != null && !value.isBlank()) {
                        result.add(ResourceLocation.parse(value));
                    }
                }
            }
            return Set.copyOf(result);
        }
    }

    /** Nullable fields allow a stage to inherit each value independently. */
    public static final class StageOverride {
        private Boolean enabled;
        private Boolean digging;
        private Boolean openDoors;
        private Boolean breakingDoors;
        private Boolean placingBlocks;
        private Boolean stacking;
        private Boolean hearing;
        private Boolean broadcasting;
        private Boolean surrounding;
        private Boolean taskLocationProtection;
        private Boolean taskLocationRegeneration;
        private Boolean taskLocationSpawnProtection;
        private Double speedMultiplier;
        private Double trackingRange;
        private Double hearingRange;
        private Double broadcastRange;
        private Integer broadcastMaxHops;
        private Integer broadcastMaxRecipients;
        private Integer hearingCooldownTicks;
        private Integer broadcastCooldownTicks;
        private Integer alertMemoryTicks;
        private Double alertRetargetDistance;
        private Integer breachCommitmentTicks;
        private Double surroundActivationRange;
        private Double surroundRadius;
        private Double surroundSteeringStrength;
        private Integer digRange;
        private Float maxDigHardness;
        private Integer digCooldownTicks;
        private String placementBlock;
        private Integer placementBlocks;
        private Integer placeRange;
        private Integer placeCooldownTicks;
        private Integer stackSearchRange;
        private Integer maxStackHeight;
        private Double stackMinimumTargetHeight;
        private Integer stackDurationTicks;
        private Integer stackCooldownTicks;
        private Double stackJumpVelocity;
        private Double stackJumpHorizontalSpeed;
        private Boolean naturalSpawn;
        private Integer zombieFamilySpawnPercent;
        private Integer vanillaZombieSpawnPercent;
        private Integer customZombieSpawnPercent;
        private Integer otherMonsterSpawnPercent;

        public StageOverride() {
        }

        private void setAbility(Ability ability, boolean enabled) {
            switch (ability) {
                case DIGGING -> digging = enabled;
                case OPEN_DOORS -> openDoors = enabled;
                case BREAKING_DOORS -> breakingDoors = enabled;
                case PLACING_BLOCKS -> placingBlocks = enabled;
                case STACKING -> stacking = enabled;
                case HEARING -> hearing = enabled;
                case BROADCASTING -> broadcasting = enabled;
                case SURROUNDING -> surrounding = enabled;
                case TASK_LOCATION_PROTECTION -> taskLocationProtection = enabled;
                case TASK_LOCATION_REGENERATION -> taskLocationRegeneration = enabled;
                case TASK_LOCATION_SPAWN_PROTECTION -> taskLocationSpawnProtection = enabled;
            }
        }
    }

    private static ZombieSpeciesConfig defaults() {
        ZombieSpeciesConfig config = new ZombieSpeciesConfig();
        config.validateAndNormalize();
        return config;
    }

    private void validateAndNormalize() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的丧尸物种配置版本：" + schemaVersion);
        }
        speedMultiplier = requireRange(speedMultiplier, 0.1D, 5.0D, "speedMultiplier");
        trackingRange = requireRange(trackingRange, 1.0D, 128.0D, "trackingRange");
        hearingRange = requireRange(hearingRange, 1.0D, 128.0D, "hearingRange");
        broadcastRange = requireRange(broadcastRange, 1.0D, 64.0D, "broadcastRange");
        broadcastMaxHops = requireRange(broadcastMaxHops, 0, 8, "broadcastMaxHops");
        broadcastMaxRecipients = requireRange(broadcastMaxRecipients, 1, 256, "broadcastMaxRecipients");
        hearingCooldownTicks = requireRange(hearingCooldownTicks, 1, 200, "hearingCooldownTicks");
        broadcastCooldownTicks = requireRange(broadcastCooldownTicks, 0, 20 * 60, "broadcastCooldownTicks");
        alertMemoryTicks = requireRange(alertMemoryTicks, 20, 20 * 60, "alertMemoryTicks");
        alertRetargetDistance = requireRange(
                alertRetargetDistance, 1.0D, 16.0D, "alertRetargetDistance");
        breachCommitmentTicks = requireRange(
                breachCommitmentTicks, 20, 20 * 60, "breachCommitmentTicks");
        surroundActivationRange = requireRange(
                surroundActivationRange, 3.0D, 32.0D, "surroundActivationRange");
        surroundRadius = requireRange(surroundRadius, 1.25D, 6.0D, "surroundRadius");
        surroundSteeringStrength = requireRange(
                surroundSteeringStrength, 0.05D, 0.9D, "surroundSteeringStrength");
        if (surroundRadius >= surroundActivationRange) {
            throw new IllegalStateException("surroundRadius 必须小于 surroundActivationRange");
        }
        digRange = requireRange(digRange, 1, 32, "digRange");
        maxDigHardness = (float) requireRange(maxDigHardness, 0.0D, 50.0D, "maxDigHardness");
        digCooldownTicks = requireRange(digCooldownTicks, 0, 20 * 60, "digCooldownTicks");
        placementBlocks = requireRange(placementBlocks, 0, 4096, "placementBlocks");
        placeRange = requireRange(placeRange, 1, 16, "placeRange");
        placeCooldownTicks = requireRange(placeCooldownTicks, 0, 20 * 60, "placeCooldownTicks");
        stackSearchRange = requireRange(stackSearchRange, 1, 16, "stackSearchRange");
        maxStackHeight = requireRange(maxStackHeight, 1, 8, "maxStackHeight");
        stackMinimumTargetHeight = requireRange(
                stackMinimumTargetHeight, 1.0D, 16.0D, "stackMinimumTargetHeight");
        stackDurationTicks = requireRange(stackDurationTicks, 4, 200, "stackDurationTicks");
        stackCooldownTicks = requireRange(stackCooldownTicks, 0, 20 * 60, "stackCooldownTicks");
        stackJumpVelocity = requireRange(stackJumpVelocity, 0.1D, 1.5D, "stackJumpVelocity");
        stackJumpHorizontalSpeed = requireRange(stackJumpHorizontalSpeed, 0.0D, 1.0D, "stackJumpHorizontalSpeed");
        zombieFamilySpawnPercent = requireRange(
                zombieFamilySpawnPercent, 0, 1000, "zombieFamilySpawnPercent");
        vanillaZombieSpawnPercent = requireRange(
                vanillaZombieSpawnPercent, 0, 1000, "vanillaZombieSpawnPercent");
        customZombieSpawnPercent = requireRange(
                customZombieSpawnPercent, 0, 1000, "customZombieSpawnPercent");
        otherMonsterSpawnPercent = requireRange(
                otherMonsterSpawnPercent, 0, 1000, "otherMonsterSpawnPercent");
        placementBlock = requireId(placementBlock, "placementBlock");
        diggableBlocks = normalizeIds(diggableBlocks, "diggableBlocks");
        protectedBlocks = normalizeIds(protectedBlocks, "protectedBlocks");
        if (protectedBlocks.isEmpty()) {
            protectedBlocks = new ArrayList<>(DEFAULT_PROTECTED_BLOCKS);
        }
        if (stageOverrides == null) {
            stageOverrides = new LinkedHashMap<>();
        }
        for (Map.Entry<String, StageOverride> entry : stageOverrides.entrySet()) {
            String stageId = entry.getKey();
            requireId(stageId, "stageOverrides key");
            StageOverride override = entry.getValue();
            if (override == null) {
                throw new IllegalStateException("stageOverrides 不能包含空值：" + stageId);
            }
            validateStageOverride(stageId, override);
        }
    }

    /** Validates nullable override values before they can reach a live entity. */
    private void validateStageOverride(String stageId, StageOverride override) {
        String prefix = "stageOverrides[" + stageId + "].";
        if (override.speedMultiplier != null) {
            override.speedMultiplier = requireRange(
                    override.speedMultiplier, 0.1D, 5.0D, prefix + "speedMultiplier");
        }
        if (override.trackingRange != null) {
            override.trackingRange = requireRange(
                    override.trackingRange, 1.0D, 128.0D, prefix + "trackingRange");
        }
        if (override.hearingRange != null) {
            override.hearingRange = requireRange(
                    override.hearingRange, 1.0D, 128.0D, prefix + "hearingRange");
        }
        if (override.broadcastRange != null) {
            override.broadcastRange = requireRange(
                    override.broadcastRange, 1.0D, 64.0D, prefix + "broadcastRange");
        }
        if (override.broadcastMaxHops != null) {
            override.broadcastMaxHops = requireRange(
                    override.broadcastMaxHops, 0, 8, prefix + "broadcastMaxHops");
        }
        if (override.broadcastMaxRecipients != null) {
            override.broadcastMaxRecipients = requireRange(
                    override.broadcastMaxRecipients, 1, 256, prefix + "broadcastMaxRecipients");
        }
        if (override.hearingCooldownTicks != null) {
            override.hearingCooldownTicks = requireRange(
                    override.hearingCooldownTicks, 1, 200, prefix + "hearingCooldownTicks");
        }
        if (override.broadcastCooldownTicks != null) {
            override.broadcastCooldownTicks = requireRange(
                    override.broadcastCooldownTicks, 0, 20 * 60, prefix + "broadcastCooldownTicks");
        }
        if (override.alertMemoryTicks != null) {
            override.alertMemoryTicks = requireRange(
                    override.alertMemoryTicks, 20, 20 * 60, prefix + "alertMemoryTicks");
        }
        if (override.alertRetargetDistance != null) {
            override.alertRetargetDistance = requireRange(
                    override.alertRetargetDistance, 1.0D, 16.0D, prefix + "alertRetargetDistance");
        }
        if (override.breachCommitmentTicks != null) {
            override.breachCommitmentTicks = requireRange(
                    override.breachCommitmentTicks, 20, 20 * 60, prefix + "breachCommitmentTicks");
        }
        if (override.surroundActivationRange != null) {
            override.surroundActivationRange = requireRange(
                    override.surroundActivationRange, 3.0D, 32.0D, prefix + "surroundActivationRange");
        }
        if (override.surroundRadius != null) {
            override.surroundRadius = requireRange(
                    override.surroundRadius, 1.25D, 6.0D, prefix + "surroundRadius");
        }
        if (override.surroundSteeringStrength != null) {
            override.surroundSteeringStrength = requireRange(
                    override.surroundSteeringStrength, 0.05D, 0.9D, prefix + "surroundSteeringStrength");
        }
        double effectiveSurroundRange = valueOr(
                override.surroundActivationRange, surroundActivationRange);
        double effectiveSurroundRadius = valueOr(override.surroundRadius, surroundRadius);
        if (effectiveSurroundRadius >= effectiveSurroundRange) {
            throw new IllegalStateException(
                    prefix + "surroundRadius 必须小于 surroundActivationRange");
        }
        if (override.digRange != null) {
            override.digRange = requireRange(override.digRange, 1, 32, prefix + "digRange");
        }
        if (override.maxDigHardness != null) {
            override.maxDigHardness = (float) requireRange(
                    override.maxDigHardness, 0.0D, 50.0D, prefix + "maxDigHardness");
        }
        if (override.digCooldownTicks != null) {
            override.digCooldownTicks = requireRange(
                    override.digCooldownTicks, 0, 20 * 60, prefix + "digCooldownTicks");
        }
        if (override.placementBlock != null) {
            override.placementBlock = requireId(override.placementBlock, prefix + "placementBlock");
        }
        if (override.placementBlocks != null) {
            override.placementBlocks = requireRange(
                    override.placementBlocks, 0, 4096, prefix + "placementBlocks");
        }
        if (override.placeRange != null) {
            override.placeRange = requireRange(override.placeRange, 1, 16, prefix + "placeRange");
        }
        if (override.placeCooldownTicks != null) {
            override.placeCooldownTicks = requireRange(
                    override.placeCooldownTicks, 0, 20 * 60, prefix + "placeCooldownTicks");
        }
        if (override.stackSearchRange != null) {
            override.stackSearchRange = requireRange(
                    override.stackSearchRange, 1, 16, prefix + "stackSearchRange");
        }
        if (override.maxStackHeight != null) {
            override.maxStackHeight = requireRange(
                    override.maxStackHeight, 1, 8, prefix + "maxStackHeight");
        }
        if (override.stackMinimumTargetHeight != null) {
            override.stackMinimumTargetHeight = requireRange(
                    override.stackMinimumTargetHeight, 1.0D, 16.0D,
                    prefix + "stackMinimumTargetHeight");
        }
        if (override.stackDurationTicks != null) {
            override.stackDurationTicks = requireRange(
                    override.stackDurationTicks, 4, 200, prefix + "stackDurationTicks");
        }
        if (override.stackCooldownTicks != null) {
            override.stackCooldownTicks = requireRange(
                    override.stackCooldownTicks, 0, 20 * 60, prefix + "stackCooldownTicks");
        }
        if (override.stackJumpVelocity != null) {
            override.stackJumpVelocity = requireRange(
                    override.stackJumpVelocity, 0.1D, 1.5D, prefix + "stackJumpVelocity");
        }
        if (override.stackJumpHorizontalSpeed != null) {
            override.stackJumpHorizontalSpeed = requireRange(
                    override.stackJumpHorizontalSpeed, 0.0D, 1.0D, prefix + "stackJumpHorizontalSpeed");
        }
        if (override.zombieFamilySpawnPercent != null) {
            override.zombieFamilySpawnPercent = requireRange(
                    override.zombieFamilySpawnPercent, 0, 1000,
                    prefix + "zombieFamilySpawnPercent");
        }
        if (override.vanillaZombieSpawnPercent != null) {
            override.vanillaZombieSpawnPercent = requireRange(
                    override.vanillaZombieSpawnPercent, 0, 1000,
                    prefix + "vanillaZombieSpawnPercent");
        }
        if (override.customZombieSpawnPercent != null) {
            override.customZombieSpawnPercent = requireRange(
                    override.customZombieSpawnPercent, 0, 1000,
                    prefix + "customZombieSpawnPercent");
        }
        if (override.otherMonsterSpawnPercent != null) {
            override.otherMonsterSpawnPercent = requireRange(
                    override.otherMonsterSpawnPercent, 0, 1000,
                    prefix + "otherMonsterSpawnPercent");
        }
    }

    private static List<String> normalizeIds(List<String> values, String fieldName) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                result.add(requireId(value, fieldName));
            }
        }
        return new ArrayList<>(result);
    }

    private static String requireId(String value, String fieldName) {
        if (value == null || !ID_PATTERN.matcher(value).matches()) {
            throw new IllegalStateException(fieldName + " ID 非法：" + value);
        }
        // ResourceLocation gives a clearer error for malformed namespace/path combinations.
        ResourceLocation.parse(value);
        return value;
    }

    private static int requireRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalStateException(fieldName + " 超出范围 [" + min + ", " + max + "]：" + value);
        }
        return value;
    }

    private static double requireRange(double value, double min, double max, String fieldName) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalStateException(fieldName + " 超出范围 [" + min + ", " + max + "]：" + value);
        }
        return value;
    }

    private static <T> T valueOr(T override, T fallback) {
        return override == null ? fallback : override;
    }
}
