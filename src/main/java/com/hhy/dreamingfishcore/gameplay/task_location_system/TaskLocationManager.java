package com.hhy.dreamingfishcore.gameplay.task_location_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryWorldState;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务地点系统的服务端唯一入口。
 *
 * <p>配置是服主维护的固定地图定义，选区状态只在当前服务器会话中存在。
 * 故事任务通过本类查询地点和结算瞬间的在场玩家，不各自保存坐标。</p>
 */
public final class TaskLocationManager {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_LOCATIONS = 4096;
    /** Resolve through NeoForge's active game directory so run/server instances do not read a
     * similarly named config from the IDE project working directory. */
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(DreamingFishCore.MODID)
            .resolve("task_locations.json")
            .toAbsolutePath().normalize();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    private static final Map<String, TaskLocationDefinition> LOCATIONS = new LinkedHashMap<>();
    private static final Map<UUID, SelectionSession> SELECTIONS = new ConcurrentHashMap<>();
    private static boolean loaded;
    private static boolean writesEnabled;

    private TaskLocationManager() {
    }

    /** 服务器启动时加载；文件不存在时写出一份可直接编辑的空配置。 */
    public static synchronized void load() {
        LOCATIONS.clear();
        SELECTIONS.clear();
        BuildableTerritoryPolicy.clearAll();
        loaded = false;
        writesEnabled = false;

        boolean fileExisted = Files.exists(CONFIG_PATH);
        try {
            DefinitionDocument document = JsonDataStore.read(
                    CONFIG_PATH, GSON, DefinitionDocument.class, DefinitionDocument::new);
            validateDocument(document);
            install(document);
            loaded = true;
            writesEnabled = true;
            if (!fileExisted) {
                writeDocument();
            }
            DreamingFishCore.LOGGER.info("任务地点加载完成，共 {} 个，配置：{}", LOCATIONS.size(), CONFIG_PATH);
        } catch (Exception exception) {
            LOCATIONS.clear();
            loaded = true;
            writesEnabled = false;
            DreamingFishCore.LOGGER.error(
                    "任务地点配置加载失败，本次会话已禁用地点修改：{}", CONFIG_PATH, exception);
        }
    }

    /** 从磁盘重新加载一整份候选配置；校验失败时保留当前内存数据。 */
    public static synchronized int reload() {
        ensureLoaded();
        try {
            DefinitionDocument candidate = JsonDataStore.read(
                    CONFIG_PATH, GSON, DefinitionDocument.class, DefinitionDocument::new);
            validateDocument(candidate);
            install(candidate);
            writesEnabled = true;
            SELECTIONS.clear();
            BuildableTerritoryPolicy.clearAll();
            return LOCATIONS.size();
        } catch (IOException exception) {
            throw new IllegalStateException("读取任务地点配置失败：" + exception.getMessage(), exception);
        }
    }

    public static synchronized void clearWorldCache() {
        LOCATIONS.clear();
        SELECTIONS.clear();
        loaded = false;
        writesEnabled = false;
        BuildableTerritoryPolicy.clearAll();
    }

    public static synchronized Collection<TaskLocationDefinition> getAllLocations() {
        ensureLoaded();
        return LOCATIONS.values().stream()
                .sorted(Comparator.comparing(TaskLocationDefinition::getId))
                .toList();
    }

    public static synchronized Optional<TaskLocationDefinition> getLocation(String locationId) {
        ensureLoaded();
        return Optional.ofNullable(LOCATIONS.get(locationId));
    }

    /** 供服主命令使用的名称查询；名称在配置中必须唯一。 */
    public static synchronized Optional<TaskLocationDefinition> getLocationByName(String locationName) {
        ensureLoaded();
        String normalizedName = normalizeLocationName(locationName);
        if (normalizedName.isEmpty()) {
            return Optional.empty();
        }
        return LOCATIONS.values().stream()
                .filter(location -> normalizeLocationName(location.getName()).equals(normalizedName))
                .findFirst();
    }

    /** 返回包含指定方块的启用地点。配置校验保证同一方块最多属于一个地点。 */
    public static synchronized Optional<TaskLocationDefinition> findLocationAt(Level level, BlockPos position) {
        if (!loaded || level == null || position == null) {
            return Optional.empty();
        }
        return LOCATIONS.values().stream()
                .filter(location -> location.contains(level.dimension(), position))
                .findFirst();
    }

    /**
     * Legacy broad protection query: true for any active story location. New callers should use
     * {@link #isBlockProtected(Level, BlockPos)} when they specifically mean ordinary block edits.
     */
    public static boolean isProtected(Level level, BlockPos position) {
        return isStoryStructureProtected(level, position);
    }

    /** Returns true only for the original authored-scene mode. */
    public static boolean isBlockProtected(Level level, BlockPos position) {
        return findLocationAt(level, position)
                .map(TaskLocationDefinition::protectsBlocks)
                .orElse(false);
    }

    /** Returns true for any active story location, including buildable settlements. */
    public static boolean isStoryStructureProtected(Level level, BlockPos position) {
        return findLocationAt(level, position)
                .map(TaskLocationDefinition::protectsEntities)
                .orElse(false);
    }

    public static boolean isBuildable(Level level, BlockPos position) {
        return findLocationAt(level, position)
                .map(TaskLocationDefinition::isBuildable)
                .orElse(false);
    }

    /**
     * Finds the buildable story location that can contain an EconomySystem claim rectangle.
     * EconomySystem remains the sole owner of private-territory persistence and permissions.
     */
    public static synchronized Optional<TaskLocationDefinition> findBuildableLocationForClaim(
            Level level, BlockPos first, BlockPos second) {
        if (!loaded || level == null || first == null || second == null) {
            return Optional.empty();
        }
        return LOCATIONS.values().stream()
                .filter(location -> location.containsClaim(level.dimension(), first, second))
                .findFirst();
    }

    /**
     * Finds an active story location touched by an EconomySystem claim rectangle. This lets the
     * integration leave the rest of the world unrestricted while identifying claims that touch
     * a story boundary.
     */
    public static synchronized Optional<TaskLocationDefinition> findStoryLocationIntersectingClaim(
            Level level, BlockPos first, BlockPos second) {
        if (!loaded || level == null || first == null || second == null) {
            return Optional.empty();
        }
        return LOCATIONS.values().stream()
                .filter(location -> location.intersectsClaim(level.dimension(), first, second))
                .findFirst();
    }

    /** Finds a protected story location touched by an EconomySystem claim rectangle. */
    public static synchronized Optional<TaskLocationDefinition> findProtectedLocationIntersectingClaim(
            Level level, BlockPos first, BlockPos second) {
        if (!loaded || level == null || first == null || second == null) {
            return Optional.empty();
        }
        return LOCATIONS.values().stream()
                .filter(location -> !location.isBuildable())
                .filter(location -> location.intersectsClaim(level.dimension(), first, second))
                .findFirst();
    }

    /** Finds a buildable story location touched by an EconomySystem claim rectangle. */
    public static synchronized Optional<TaskLocationDefinition> findBuildableLocationIntersectingClaim(
            Level level, BlockPos first, BlockPos second) {
        if (!loaded || level == null || first == null || second == null) {
            return Optional.empty();
        }
        return LOCATIONS.values().stream()
                .filter(TaskLocationDefinition::isBuildable)
                .filter(location -> location.intersectsClaim(level.dimension(), first, second))
                .findFirst();
    }

    /**
     * 收集结算瞬间位于地点内的生存/冒险玩家。
     * 创造和旁观玩家不属于阶段任务个人记录参与者。
     */
    public static List<ServerPlayer> getEligiblePlayers(MinecraftServer server, String locationId) {
        if (server == null) {
            return List.of();
        }
        TaskLocationDefinition location = getLocation(locationId)
                .orElseThrow(() -> new IllegalArgumentException("任务地点不存在：" + locationId));
        if (!location.isEnabled()) {
            return List.of();
        }

        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!AuthSessionGuard.isAuthenticated(player)) {
                continue;
            }
            GameType gameType = player.gameMode.getGameModeForPlayer();
            if (gameType != GameType.SURVIVAL && gameType != GameType.ADVENTURE) {
                continue;
            }
            if (location.contains(player.serverLevel().dimension(), player.blockPosition())) {
                result.add(player);
            }
        }
        return List.copyOf(result);
    }

    /** 直接生成 StoryManager.resolveTask 所需的参与者快照。 */
    public static List<StoryWorldState.TaskParticipant> collectTaskParticipants(
            MinecraftServer server, String locationId) {
        return getEligiblePlayers(server, locationId).stream()
                .map(player -> new StoryWorldState.TaskParticipant(
                        player.getUUID(), player.getScoreboardName()))
                .toList();
    }

    public static synchronized void beginSelection(ServerPlayer player, String displayName) {
        String normalizedName = requireLocationName(displayName);
        TaskLocationDefinition existing = getLocationByName(normalizedName).orElse(null);
        beginSelection(player, normalizedName,
                existing == null ? TaskLocationMode.PROTECTED : existing.getMode());
    }

    /** Starts a selection and explicitly chooses the mode for a new or existing location. */
    public static synchronized void beginSelection(
            ServerPlayer player, String displayName, TaskLocationMode mode) {
        ensureWritable();
        String normalizedName = requireLocationName(displayName);
        TaskLocationMode selectedMode = mode == null ? TaskLocationMode.PROTECTED : mode;
        TaskLocationDefinition existing = getLocationByName(normalizedName).orElse(null);
        String locationId = existing == null ? createLocationId() : existing.getId();
        SELECTIONS.put(player.getUUID(),
                new SelectionSession(locationId, normalizedName, selectedMode));
        NotificationPushHelper.sendTopLeftNotification(player,
                "§b任务地点选区已开始§r\n模式："
                        + (selectedMode == TaskLocationMode.BUILDABLE ? "可建造" : "强制保护")
                        + "\n左/右键方块选点；旁观模式可使用 task_location pos1 / pos2。", 9000);
    }

    public static boolean isSelecting(ServerPlayer player) {
        return player != null && SELECTIONS.containsKey(player.getUUID());
    }

    public static synchronized boolean selectFirstPoint(ServerPlayer player, BlockPos position) {
        SelectionSession session = SELECTIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        session.dimension = player.serverLevel().dimension();
        session.first = position.immutable();
        session.second = null;
        NotificationPushHelper.sendTopLeftNotification(player,
                "§a第一个角点已设置§r：" + formatPosition(position)
                        + "\n请右键另一个方块设置第二个角点。", 7000);
        return true;
    }

    public static synchronized boolean selectSecondPoint(ServerPlayer player, BlockPos position) {
        SelectionSession session = SELECTIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        if (session.first == null) {
            NotificationPushHelper.sendTopLeftNotification(player, "§c请先左键方块设置第一个角点。", 5000);
            return true;
        }
        if (!session.dimension.equals(player.serverLevel().dimension())) {
            NotificationPushHelper.sendTopLeftNotification(player, "§c两个角点必须位于同一维度。", 5000);
            return true;
        }
        session.second = position.immutable();
        NotificationPushHelper.sendTopLeftNotification(player,
                "§a第二个角点已设置§r：" + formatPosition(position)
                        + "\n执行 §e/dreamingfish task_location confirm§r 保存地点。", 9000);
        return true;
    }

    public static synchronized TaskLocationDefinition confirmSelection(ServerPlayer player) {
        ensureWritable();
        SelectionSession session = SELECTIONS.get(player.getUUID());
        if (session == null) {
            throw new IllegalStateException("你当前没有正在设置的任务地点");
        }
        if (session.first == null || session.second == null || session.dimension == null) {
            throw new IllegalStateException("任务地点需要两个角点后才能保存");
        }

        TaskLocationDefinition definition = new TaskLocationDefinition(
                session.locationId, session.displayName, session.dimension,
                session.first, session.second, session.mode);
        upsert(definition);
        SELECTIONS.remove(player.getUUID());
        return definition;
    }

    public static synchronized boolean cancelSelection(ServerPlayer player) {
        return SELECTIONS.remove(player.getUUID()) != null;
    }

    public static synchronized boolean removeLocation(String locationId) {
        ensureWritable();
        TaskLocationDefinition removed = LOCATIONS.remove(locationId);
        if (removed == null) {
            return false;
        }
        try {
            writeDocument();
            BuildableTerritoryPolicy.clearAll();
        } catch (RuntimeException exception) {
            LOCATIONS.put(removed.getId(), removed);
            throw exception;
        }
        return true;
    }

    /** 供服主命令使用的名称删除；内部仍按稳定 ID 删除。 */
    public static synchronized boolean removeLocationByName(String locationName) {
        TaskLocationDefinition location = getLocationByName(locationName).orElse(null);
        return location != null && removeLocation(location.getId());
    }

    private static void upsert(TaskLocationDefinition definition) {
        definition.validate();
        for (TaskLocationDefinition existing : LOCATIONS.values()) {
            if (!existing.getId().equals(definition.getId())
                    && existing.isEnabled() && definition.isEnabled()
                    && existing.intersects(definition)) {
                throw new IllegalArgumentException(
                        "任务地点与现有地点重叠：" + existing.getId());
            }
        }

        TaskLocationDefinition previous = LOCATIONS.put(definition.getId(), definition);
        try {
            writeDocument();
        } catch (RuntimeException exception) {
            if (previous == null) {
                LOCATIONS.remove(definition.getId());
            } else {
                LOCATIONS.put(previous.getId(), previous);
            }
            throw exception;
        }
    }

    private static void validateDocument(DefinitionDocument document) {
        if (document == null || document.schemaVersion != SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的任务地点配置版本");
        }
        if (document.locations == null) {
            document.locations = new ArrayList<>();
        }
        if (document.locations.size() > MAX_LOCATIONS) {
            throw new IllegalStateException("任务地点数量超过限制：" + MAX_LOCATIONS);
        }

        Map<String, TaskLocationDefinition> checked = new LinkedHashMap<>();
        Map<String, TaskLocationDefinition> names = new LinkedHashMap<>();
        for (TaskLocationDefinition location : document.locations) {
            if (location == null) {
                throw new IllegalStateException("任务地点配置包含空项目");
            }
            location.validate();
            if (checked.putIfAbsent(location.getId(), location) != null) {
                throw new IllegalStateException("任务地点 ID 重复：" + location.getId());
            }
            String normalizedName = normalizeLocationName(location.getName());
            if (names.putIfAbsent(normalizedName, location) != null) {
                throw new IllegalStateException("任务地点名称重复：" + location.getName());
            }
        }

        List<TaskLocationDefinition> locations = new ArrayList<>(checked.values());
        for (int left = 0; left < locations.size(); left++) {
            TaskLocationDefinition first = locations.get(left);
            if (!first.isEnabled()) {
                continue;
            }
            for (int right = left + 1; right < locations.size(); right++) {
                TaskLocationDefinition second = locations.get(right);
                if (second.isEnabled() && first.intersects(second)) {
                    throw new IllegalStateException(
                            "启用的任务地点不能重叠：" + first.getId() + " / " + second.getId());
                }
            }
        }
    }

    private static void install(DefinitionDocument document) {
        LOCATIONS.clear();
        document.locations.stream()
                .sorted(Comparator.comparing(TaskLocationDefinition::getId))
                .forEach(location -> LOCATIONS.put(location.getId(), location));
    }

    private static void writeDocument() {
        ensureWritable();
        DefinitionDocument document = new DefinitionDocument(
                SCHEMA_VERSION, new ArrayList<>(LOCATIONS.values()));
        try {
            JsonDataStore.writeAtomic(CONFIG_PATH, GSON, document);
        } catch (IOException exception) {
            throw new IllegalStateException("保存任务地点配置失败：" + exception.getMessage(), exception);
        }
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("任务地点系统尚未加载");
        }
    }

    private static void ensureWritable() {
        ensureLoaded();
        if (!writesEnabled) {
            throw new IllegalStateException("任务地点配置处于只读保护状态");
        }
    }

    private static String formatPosition(BlockPos position) {
        return position.getX() + ", " + position.getY() + ", " + position.getZ();
    }

    private static String requireLocationName(String locationName) {
        if (locationName == null || locationName.isBlank()) {
            throw new IllegalArgumentException("任务地点名称不能为空");
        }
        return locationName.trim();
    }

    private static String normalizeLocationName(String locationName) {
        return locationName == null ? "" : locationName.trim().toLowerCase(Locale.ROOT);
    }

    /** 内部 ID 不暴露给选区命令，避免显示名称变更影响任务和世界存档引用。 */
    private static String createLocationId() {
        String locationId;
        do {
            locationId = DreamingFishCore.MODID + ":location_"
                    + UUID.randomUUID().toString().replace("-", "");
        } while (LOCATIONS.containsKey(locationId));
        return locationId;
    }

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }

    public static synchronized Status getStatus() {
        return new Status(loaded, writesEnabled, LOCATIONS.size(), CONFIG_PATH);
    }

    public record Status(boolean loaded, boolean writesEnabled, int locationCount, Path configPath) {
    }

    private static final class SelectionSession {
        private final String locationId;
        private final String displayName;
        private final TaskLocationMode mode;
        private net.minecraft.resources.ResourceKey<Level> dimension;
        private BlockPos first;
        private BlockPos second;

        private SelectionSession(String locationId, String displayName, TaskLocationMode mode) {
            this.locationId = locationId;
            this.displayName = displayName;
            this.mode = mode;
        }
    }

    private static final class DefinitionDocument {
        private int schemaVersion = SCHEMA_VERSION;
        private List<TaskLocationDefinition> locations = new ArrayList<>();

        private DefinitionDocument() {
        }

        private DefinitionDocument(int schemaVersion, List<TaskLocationDefinition> locations) {
            this.schemaVersion = schemaVersion;
            this.locations = locations;
        }
    }
}
