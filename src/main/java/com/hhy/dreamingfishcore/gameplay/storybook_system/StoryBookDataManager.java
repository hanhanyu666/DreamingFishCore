package com.hhy.dreamingfishcore.gameplay.storybook_system;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.clue_system.ClueCatalog;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.gameplay.storybook_system.network.Packet_OpenStoryBookGUI;
import com.hhy.dreamingfishcore.gameplay.storybook_system.network.Packet_OpenStoryFragmentGUI;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 片段数据管理器
 * 负责加载和管理片段配置数据，以及玩家的随记本数据
 * 配置文件路径: config/dreamingfishcore/data/fragment_data.json
 * 玩家数据路径: <世界存档>/data/dreamingfishcore/storybook/player_progress.json
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class StoryBookDataManager {

    // ==================== 玩家随记本数据 ====================
    // 玩家数据：playerUUID -> StoryBookData
    public static Map<UUID, StoryBookData> PLAYER_DATA_CACHE = new ConcurrentHashMap<>();

    // 待保存的玩家数据队列
    private static final Set<UUID> DIRTY_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final int MAX_PLAYER_RECORDS = 16_384;
    private static final int MAX_PLAYER_FRAGMENT_ENTRIES = 16_384;
    /** 网络包和玩家排序请求共用的硬上限，避免客户端 VarInt 放大主线程工作量。 */
    public static final int MAX_NETWORK_ORDER_ENTRIES = 16_384;
    public static final int MAX_NETWORK_BOOK_ENTRIES = 16_384;
    public static final int MAX_NETWORK_TEXT_LENGTH = 32_768;

    private static boolean loaded;
    /** 片段定义只有在整份文件成功解析和校验后才允许覆盖。 */
    /** 玩家世界档案与片段定义分开保护；档案读取失败时绝不覆盖原档。 */
    private static boolean playerDataWritable;

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private static final Type PLAYER_DATA_TYPE = new TypeToken<Map<String, StoryBookData>>() {}.getType();

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUuid = player.getUUID();
            PLAYER_DATA_CACHE.computeIfAbsent(playerUuid, k -> new StoryBookData());
        }
    }

    // ==================== 玩家数据加载/保存 ====================

    /**
     * 加载当前世界的玩家随记本数据。线索内容由 {@code ClueCatalog} 独立加载。
     */
    public static synchronized void loadWorldData(MinecraftServer server) {
        // 世界切换/重复启动时不能残留上一个世界的玩家档案或可写状态。
        clearWorldCache();

        if (server == null) {
            loaded = true;
            playerDataWritable = false;
            DreamingFishCore.LOGGER.error("无法加载随记本玩家数据：服务器实例为空，进入只读保护");
            return;
        }

        Map<UUID, StoryBookData> loadedPlayers;
        try {
            Path playerPath = playerDataPath(server);
            if (Files.exists(playerPath) && Files.size(playerPath) == 0L) {
                throw new IllegalStateException(
                        "随记本玩家数据文件为空，已拒绝覆盖原文件：" + playerPath);
            }
            Map<String, StoryBookData> dataMap = JsonDataStore.read(
                    playerPath, GSON, PLAYER_DATA_TYPE, HashMap::new);
            validatePlayerDataMap(dataMap);
            loadedPlayers = new LinkedHashMap<>();
            for (Map.Entry<String, StoryBookData> entry : dataMap.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                loadedPlayers.put(uuid, entry.getValue());
            }
        } catch (Exception exception) {
            PLAYER_DATA_CACHE.clear();
            playerDataWritable = false;
            loaded = true;
            DreamingFishCore.LOGGER.error("读取世界随记本数据失败，本次会话不会覆盖损坏文件", exception);
            return;
        }

        // 读到这里数据已经安全拿到了：先落缓存、置可写，再做迁移。
        PLAYER_DATA_CACHE.putAll(loadedPlayers);
        playerDataWritable = true;
        loaded = true;

        // 迁移是「读取之后」的加工步骤，必须和上面的读取失败分支分开：
        // 旧写法共用一个 catch，迁移一旦抛异常就连刚读到的数据一起清空并转只读
        // ——读取明明成功了，不该因为加工步骤失败而丢掉数据。
        int migrated = 0;
        try {
            migrated = migrateLegacyClueIds();
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error(
                    "旧编号线索迁移失败；数据保持原样，本次会话其余功能不受影响", exception);
        }
        DreamingFishCore.LOGGER.info("随记本玩家数据加载完成，共 {} 个玩家{}",
                PLAYER_DATA_CACHE.size(),
                migrated > 0 ? "（其中 " + migrated + " 名玩家的旧编号线索已迁移到稳定 ID）" : "");
    }

    /**
     * 把旧整数编号迁移成稳定 ID（里程碑 2）。
     *
     * <p>必须在玩家数据加载完成之后、且线索目录已就绪时调用；映射不到旧编号就跳过
     * （内容被删掉时也要能正常开服），旧字段保持原样以便回退。</p>
     *
     * @return 发生了迁移的玩家数
     */
    private static int migrateLegacyClueIds() {
        int migrated = 0;
        for (Map.Entry<UUID, StoryBookData> entry : PLAYER_DATA_CACHE.entrySet()) {
            StoryBookData data = entry.getValue();
            if (data == null) {
                continue;
            }
            data.normalizeClueRecords();
            if (data.migrateLegacyClueIds(ClueCatalog::idForLegacy)) {
                // 加载路径内标记脏数据：不能走 markPlayerDirty（它会校验 loaded，见那里的注释）。
                markPlayerDirtyDuringLoad(entry.getKey());
                migrated++;
            }
        }
        return migrated;
    }

    /**
     * 只在玩家随记本数据发生变化时保存。
     */
    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        if (!loaded || DIRTY_PLAYERS.isEmpty()) {
            return true;
        }
        if (!playerDataWritable) {
            DreamingFishCore.LOGGER.error("随记本玩家数据未安全加载，拒绝覆盖原文件");
            return false;
        }
        if (server == null) {
            DreamingFishCore.LOGGER.error("无法保存随记本玩家数据：服务器实例为空");
            return false;
        }

        Map<String, StoryBookData> dataMap = new HashMap<>();
        for (Map.Entry<UUID, StoryBookData> entry : PLAYER_DATA_CACHE.entrySet()) {
            dataMap.put(entry.getKey().toString(), entry.getValue());
        }

        try {
            JsonDataStore.writeAtomic(playerDataPath(server), GSON, dataMap);
            DIRTY_PLAYERS.clear();
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入世界随记本数据失败，保留 dirty 状态等待下次保存", exception);
            return false;
        }
    }

    /**
     * 服务器关闭后释放世界级静态缓存，避免下一次开服串档。
     */
    public static synchronized void clearWorldCache() {
        PLAYER_DATA_CACHE.clear();
        DIRTY_PLAYERS.clear();
        loaded = false;
        playerDataWritable = false;
    }

    private static void validatePlayerDataMap(Map<String, StoryBookData> dataMap) {
        if (dataMap == null) {
            throw new IllegalStateException("随记本玩家数据根节点不是对象");
        }
        if (dataMap.size() > MAX_PLAYER_RECORDS) {
            throw new IllegalStateException("随记本玩家记录超过上限：" + MAX_PLAYER_RECORDS);
        }
        for (Map.Entry<String, StoryBookData> entry : dataMap.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalStateException("随记本玩家 UUID 为空");
            }
            UUID.fromString(entry.getKey());
            StoryBookData data = entry.getValue();
            if (data == null) {
                throw new IllegalStateException("随记本玩家记录为空：" + entry.getKey());
            }
            if (data.getFragmentPageUseCount() < 0
                    || data.getUnlockedFragmentIds().size() > MAX_PLAYER_FRAGMENT_ENTRIES
                    || data.getReadFragmentIds().size() > MAX_PLAYER_FRAGMENT_ENTRIES
                    || data.getUnlockedChapterIds().size() > MAX_PLAYER_FRAGMENT_ENTRIES
                    || data.getObtainedOrder().size() > MAX_PLAYER_FRAGMENT_ENTRIES
                    || data.getDiscoveredClueIds().size() > MAX_PLAYER_FRAGMENT_ENTRIES
                    || data.getReadClueIds().size() > MAX_PLAYER_FRAGMENT_ENTRIES
                    || data.getClueOrder().size() > MAX_PLAYER_FRAGMENT_ENTRIES) {
                throw new IllegalStateException("随记本玩家集合超过上限：" + entry.getKey());
            }
            validatePositiveIds(data.getUnlockedFragmentIds(), "已解锁片段", entry.getKey());
            validatePositiveIds(data.getReadFragmentIds(), "已读片段", entry.getKey());
            validatePositiveIds(data.getUnlockedChapterIds(), "已解锁章节", entry.getKey());
            Set<Integer> ordered = new HashSet<>();
            for (Integer id : data.getObtainedOrder()) {
                if (id == null || id <= 0 || !ordered.add(id)) {
                    throw new IllegalStateException("随记本排序列表含非法/重复片段：" + entry.getKey());
                }
            }
            // 稳定 ID 的发现记录同样要过一遍：空白或重复会让"已发现"判断失真。
            Set<String> discovered = new HashSet<>();
            for (String clueId : data.getDiscoveredClueIds()) {
                if (clueId == null || clueId.isBlank() || !discovered.add(clueId)) {
                    throw new IllegalStateException("随记本发现记录含非法/重复线索：" + entry.getKey());
                }
            }
        }
    }

    private static void validatePositiveIds(Set<Integer> ids, String label, String playerId) {
        for (Integer id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalStateException(label + "含非法 ID：" + playerId);
            }
        }
    }

    private static void requireLength(String value, int maxLength, String field, int fragmentId) {
        if (value == null || value.length() > maxLength) {
            throw new IllegalStateException(
                    "片段 " + fragmentId + " 的 " + field + " 为空或过长（上限 " + maxLength + "）");
        }
    }

    private static Path playerDataPath(MinecraftServer server) {
        return WorldDataPaths.resolve(server, "storybook", "player_progress.json");
    }

    /**
     * 标记玩家数据为脏（需要保存）
     */
    public static void markPlayerDirty(UUID playerUuid) {
        ensureLoaded();
        markPlayerDirtyDuringLoad(playerUuid);
    }

    /**
     * 加载路径内使用的脏标记：**不做「必须已加载」校验**。
     *
     * <p>迁移就跑在加载过程中，此时 {@code loaded} 可能还没置位。曾经这里走的是
     * {@link #markPlayerDirty}，于是加载流程回头要求"已经加载完成"、自己把自己卡死：
     * 异常被读取失败的 catch 接住 → 清空玩家缓存 → 整个会话只读 → 存档再也写不进去。
     * 凡是**加载过程中**标记脏数据，都走这个方法，不要依赖调用顺序。</p>
     */
    private static void markPlayerDirtyDuringLoad(UUID playerUuid) {
        if (playerUuid != null) {
            DIRTY_PLAYERS.add(playerUuid);
        }
    }

    // ==================== 玩家数据查询方法 ====================

    /**
     * 获取玩家的随记本数据
     */
    public static StoryBookData getPlayerStoryBook(UUID playerUuid) {
        ensureLoaded();
        if (playerUuid == null) {
            throw new IllegalArgumentException("玩家 UUID 不能为空");
        }
        return PLAYER_DATA_CACHE.computeIfAbsent(playerUuid, ignored -> new StoryBookData());
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("随记本玩家数据尚未随世界加载");
        }
    }

    /**
     * 获取玩家的随记本数据（ServerPlayer）
     */
    public static StoryBookData getPlayerStoryBook(ServerPlayer player) {
        return getPlayerStoryBook(player.getUUID());
    }



    // ==================== 里程碑 2：稳定 ID 的永久发现记录 ====================

    /**
     * 登记一条线索为永久发现（发放即视为已发现，ADR 0009）。
     *
     * <p>与旧的 {@code unlockFragmentForPlayer} 并存：旧路径按 int 编号走，
     * 新路径按稳定 ID 走，两者最终都落到同一份玩家档案里。</p>
     *
     * @return 是否是本次新发现的
     */
    public static boolean discoverClueForPlayer(UUID playerUuid, String clueId) {
        if (playerUuid == null || clueId == null || clueId.isBlank()) {
            return false;
        }
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        boolean discovered = storyBook.discoverClue(clueId);
        if (discovered) {
            markPlayerDirty(playerUuid);
        }
        return discovered;
    }

    public static boolean hasDiscoveredClue(UUID playerUuid, String clueId) {
        if (playerUuid == null || clueId == null || clueId.isBlank()) {
            return false;
        }
        StoryBookData storyBook = PLAYER_DATA_CACHE.get(playerUuid);
        return storyBook != null && storyBook.hasDiscoveredClue(clueId);
    }

    public static java.util.List<String> getDiscoveredClueIds(UUID playerUuid) {
        if (playerUuid == null) {
            return java.util.List.of();
        }
        StoryBookData storyBook = PLAYER_DATA_CACHE.get(playerUuid);
        return storyBook == null ? java.util.List.of() : storyBook.getSortedClueIds();
    }

    /** 标记一条已发现的线索为已读。 */
    public static void markClueReadForPlayer(UUID playerUuid, String clueId) {
        if (playerUuid == null || clueId == null || clueId.isBlank()) {
            return;
        }
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        if (storyBook.hasDiscoveredClue(clueId) && !storyBook.hasReadClue(clueId)) {
            storyBook.markClueRead(clueId);
            markPlayerDirty(playerUuid);
        }
    }

    /**
     * 解锁章节
     */
    public static boolean unlockChapterForPlayer(UUID playerUuid, int chapterId) {
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        boolean unlocked = storyBook.unlockChapter(chapterId);
        if (unlocked) {
            markPlayerDirty(playerUuid);
        }
        return unlocked;
    }



    /**
     * 检查玩家是否拥有随记本
     */
    public static boolean playerHasStoryBook(UUID playerUuid) {
        StoryBookData storyBook = PLAYER_DATA_CACHE.get(playerUuid);
        return storyBook != null && storyBook.hasStoryBook();
    }

    public static List<StoryBookEntryViewData> getStoryBookEntriesForPlayer(UUID playerUuid) {
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        List<StoryBookEntryViewData> entries = new ArrayList<>();
        // 里程碑 2 起按稳定 ID 组装；内容被删掉的线索静默跳过（与旧行为一致）。
        for (String clueId : storyBook.getSortedClueIds()) {
            com.hhy.dreamingfishcore.gameplay.clue_system.ClueDefinition definition =
                    com.hhy.dreamingfishcore.gameplay.clue_system.ClueCatalog.byId(clueId);
            if (definition == null) {
                continue;
            }
            entries.add(viewOf(definition, storyBook.hasReadClue(clueId)));
        }
        return entries;
    }

    /**
     * 把一条线索定义装成客户端视图。
     *
     * <p>只搬玩家可见字段：私密定义（{@code ClueSecrets}）在这里**没有任何入口**，
     * 想泄漏都泄漏不了。</p>
     */
    public static StoryBookEntryViewData viewOf(
            com.hhy.dreamingfishcore.gameplay.clue_system.ClueDefinition definition,
            boolean read) {
        return new StoryBookEntryViewData(
                definition.id(), definition.legacyId(), definition.stageId(), definition.chapterId(),
                definition.title(), definition.content(), definition.time(), definition.authorName(),
                definition.source(), definition.observationSpan(), definition.sample(),
                definition.conditions(), read);
    }

    /**
     * 给予玩家随记本
     */
    public static void giveStoryBookToPlayer(UUID playerUuid) {
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        if (!storyBook.hasStoryBook()) {
            storyBook.setHasStoryBook(true);
            markPlayerDirty(playerUuid);
        }
    }

    /**
     * 检查玩家是否开始旅程
     */
    public static boolean playerJourneyStarted(UUID playerUuid) {
        StoryBookData storyBook = PLAYER_DATA_CACHE.get(playerUuid);
        return storyBook != null && storyBook.isJourneyStarted();
    }

    /**
     * 开始玩家旅程
     */
    public static void startPlayerJourney(UUID playerUuid) {
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        if (!storyBook.isJourneyStarted()) {
            storyBook.setJourneyStarted(true);
            markPlayerDirty(playerUuid);
        }
    }

    /**
     * 增加玩家残页使用次数
     */
    public static int incrementFragmentPageUseCount(UUID playerUuid) {
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        storyBook.incrementFragmentPageUseCount();
        markPlayerDirty(playerUuid);
        return storyBook.getFragmentPageUseCount();
    }

    /**
     * 获取玩家残页使用次数
     */
    public static int getFragmentPageUseCount(UUID playerUuid) {
        StoryBookData storyBook = PLAYER_DATA_CACHE.get(playerUuid);
        return storyBook != null ? storyBook.getFragmentPageUseCount() : 0;
    }

    public static void openStoryBook(ServerPlayer player) {
        DreamingFishCore_NetworkManager.sendToClient(
                new Packet_OpenStoryBookGUI(getStoryBookEntriesForPlayer(player.getUUID())),
                player
        );
    }

    /**
     * 按稳定 ID 更新随记本排序（里程碑 2 的客户端排序包走这里）。
     *
     * <p>与旧路径同样的防线：客户端可以少传（界面是陈旧视图），但不能引入未知或重复的 ID，
     * 否则整包丢弃。</p>
     */
    public static void updateClueOrderForPlayer(UUID playerUuid, List<String> orderedClueIds) {
        if (playerUuid == null || orderedClueIds == null
                || orderedClueIds.size() > MAX_NETWORK_ORDER_ENTRIES) {
            return;
        }
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        Set<String> discovered = storyBook.getDiscoveredClueIds();
        Set<String> seen = new HashSet<>();
        for (String clueId : orderedClueIds) {
            if (clueId == null || clueId.isBlank()
                    || !discovered.contains(clueId) || !seen.add(clueId)) {
                return;
            }
        }
        storyBook.setClueOrder(orderedClueIds);
        markPlayerDirty(playerUuid);
    }

    public static void updateFragmentOrderForPlayer(UUID playerUuid, List<Integer> orderedFragmentIds) {
        if (playerUuid == null || orderedFragmentIds == null
                || orderedFragmentIds.size() > MAX_NETWORK_ORDER_ENTRIES) {
            return;
        }

        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        Set<Integer> unlocked = storyBook.getUnlockedFragmentIds();
        Set<Integer> seen = new HashSet<>();
        for (Integer fragmentId : orderedFragmentIds) {
            // The client may omit an entry while sorting a stale view, but it
            // must never introduce an unknown/duplicate ID into the world
            // state.  StoryBookData will append any owned IDs that were omitted.
            if (fragmentId == null || fragmentId <= 0
                    || !unlocked.contains(fragmentId) || !seen.add(fragmentId)) {
                return;
            }
        }
        List<Integer> before = storyBook.getSortedFragmentIds();
        storyBook.setObtainedOrder(orderedFragmentIds);
        if (!before.equals(storyBook.getSortedFragmentIds())) {
            markPlayerDirty(playerUuid);
        }
    }

    /**
     * 按稳定 ID 使用一张残页（里程碑 2 收尾后的唯一翻页流程）。
     *
     * <p>首次使用发随记本与成就、解锁所属章节、标记已读并弹开内容页；
     * "发现"在发放时就已经落盘（ADR 0009），这里只是读取与翻页。</p>
     */
    public static boolean useCluePage(ServerPlayer player, String clueId) {
        if (player == null || clueId == null || clueId.isBlank()) {
            return false;
        }
        UUID playerUuid = player.getUUID();
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        com.hhy.dreamingfishcore.gameplay.clue_system.ClueDefinition definition =
                com.hhy.dreamingfishcore.gameplay.clue_system.ClueCatalog.byId(clueId);
        if (definition == null) {
            player.sendSystemMessage(Component.literal("§c这张残页上没有可读取的内容。"));
            return false;
        }

        boolean firstUse = !storyBook.isJourneyStarted();
        giveStoryBookToPlayer(playerUuid);
        ensurePlayerHasStoryBookItem(player);

        if (firstUse) {
            startPlayerJourney(playerUuid);
            grantJourneyStartedAdvancement(player);
            player.sendSystemMessage(Component.literal("§6已解锁成就：§e远旅开端"));
        }

        incrementFragmentPageUseCount(playerUuid);

        boolean newlyDiscovered = discoverClueForPlayer(playerUuid, clueId);
        if (newlyDiscovered) {
            unlockChapterForPlayer(playerUuid, definition.chapterId());
            player.sendSystemMessage(Component.literal("§a你拼出了新的内容：§f" + definition.title()));
        } else {
            player.sendSystemMessage(Component.literal("§7你翻开了已收录的残页：§f"
                    + definition.title()));
        }

        markClueReadForPlayer(playerUuid, clueId);
        DreamingFishCore_NetworkManager.sendToClient(
                new Packet_OpenStoryFragmentGUI(viewOf(definition, true)), player);
        levelUpStoryFeedback(player);
        markPlayerDirty(playerUuid);
        return true;
    }

    private static void ensurePlayerHasStoryBookItem(ServerPlayer player) {
        ItemStack storyBookStack = new ItemStack(DreamingFishCore_Items.STORY_BOOK.get());
        if (player.getInventory().contains(storyBookStack)) {
            return;
        }

        if (!player.addItem(storyBookStack)) {
            player.drop(storyBookStack, false);
        }
    }

    private static void grantJourneyStartedAdvancement(ServerPlayer player) {
        var advancement = player.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "storybook/journey_started"));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "triggered");
        }
    }

    private static void levelUpStoryFeedback(ServerPlayer player) {
        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
    }
}
