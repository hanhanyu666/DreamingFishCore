package com.hhy.dreamingfishcore.gameplay.storybook_system;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
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
import net.neoforged.fml.loading.FMLPaths;

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

    private static final Path FRAGMENT_DATA_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(DreamingFishCore.MODID)
            .resolve("data")
            .resolve("fragment_data.json");

    // ==================== 片段配置数据 ====================
    // 片段缓存：fragmentId -> FragmentData
    public static Map<Integer, FragmentData> FRAGMENT_CACHE = new ConcurrentHashMap<>();

    // 阶段片段索引：stageId -> List<FragmentData>
    public static Map<Integer, List<FragmentData>> STAGE_INDEX = new ConcurrentHashMap<>();

    // 章节片段索引：chapterId -> List<FragmentData>
    public static Map<Integer, List<FragmentData>> CHAPTER_INDEX = new ConcurrentHashMap<>();

    // ==================== 玩家随记本数据 ====================
    // 玩家数据：playerUUID -> StoryBookData
    public static Map<UUID, StoryBookData> PLAYER_DATA_CACHE = new ConcurrentHashMap<>();

    // 待保存的玩家数据队列
    private static final Set<UUID> DIRTY_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static boolean loaded;
    private static boolean fragmentConfigWritable;

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private static final Type FRAGMENT_LIST_TYPE = new TypeToken<List<FragmentData>>() {}.getType();
    private static final Type PLAYER_DATA_TYPE = new TypeToken<Map<String, StoryBookData>>() {}.getType();

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUuid = player.getUUID();
            PLAYER_DATA_CACHE.computeIfAbsent(playerUuid, k -> new StoryBookData());
        }
    }

    // ==================== 片段配置数据加载/保存 ====================

    /**
     * 加载片段配置数据
     */
    public static void loadFragmentData() {
        FRAGMENT_CACHE.clear();
        STAGE_INDEX.clear();
        CHAPTER_INDEX.clear();

        if (Files.notExists(FRAGMENT_DATA_PATH) && !saveDefaultFragmentConfig()) {
            return;
        }

        try {
            if (Files.size(FRAGMENT_DATA_PATH) == 0L) {
                fragmentConfigWritable = false;
                DreamingFishCore.LOGGER.error("片段配置为空，拒绝覆盖文件：{}", FRAGMENT_DATA_PATH);
                return;
            }

            List<FragmentData> fragmentList = JsonDataStore.read(
                    FRAGMENT_DATA_PATH,
                    GSON,
                    FRAGMENT_LIST_TYPE,
                    ArrayList::new);
            if (fragmentList != null) {
                for (FragmentData fragment : fragmentList) {
                    if (fragment == null) {
                        continue;
                    }
                    FRAGMENT_CACHE.put(fragment.getId(), fragment);
                    // 构建阶段索引
                    STAGE_INDEX.computeIfAbsent(fragment.getStageId(), k -> new ArrayList<>()).add(fragment);
                    // 构建章节索引
                    CHAPTER_INDEX.computeIfAbsent(fragment.getChapterId(), k -> new ArrayList<>()).add(fragment);
                }
            }
            fragmentConfigWritable = true;
        } catch (Exception exception) {
            fragmentConfigWritable = false;
            DreamingFishCore.LOGGER.error(
                    "片段配置及备份读取失败，拒绝覆盖原文件：{}",
                    FRAGMENT_DATA_PATH,
                    exception);
        }

        DreamingFishCore.LOGGER.info("片段数据加载完成，共 {} 条片段，{} 个阶段，{} 个章节",
                FRAGMENT_CACHE.size(), STAGE_INDEX.size(), CHAPTER_INDEX.size());
    }

    /**
     * 保存片段配置数据
     */
    public static boolean saveFragmentData() {
        if (!fragmentConfigWritable) {
            DreamingFishCore.LOGGER.error("片段配置未安全加载，拒绝覆盖文件：{}", FRAGMENT_DATA_PATH);
            return false;
        }

        List<FragmentData> fragmentList = new ArrayList<>(FRAGMENT_CACHE.values());
        fragmentList.sort(Comparator.comparingInt(FragmentData::getId));
        try {
            JsonDataStore.writeAtomic(FRAGMENT_DATA_PATH, GSON, fragmentList);
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("保存片段数据失败：{}", FRAGMENT_DATA_PATH, exception);
            return false;
        }
    }

    /**
     * 保存默认片段配置
     */
    private static boolean saveDefaultFragmentConfig() {
        List<FragmentData> defaultFragments = new ArrayList<>();

        // 序章片段（使用4次残页解锁）
        defaultFragments.add(new FragmentData(
                1,
                1,
                0,
                "未知幸存者",
                "残页一",
                "2066年3月15日",
                "这是一切的开始...\n\n在这片荒芜的土地上，埋藏着许多不为人知的秘密。\n\n空气中弥漫着不祥的气息，我必须继续调查下去..."
        ));

        defaultFragments.add(new FragmentData(
                2,
                1,
                0,
                "失踪的矿工",
                "残页二",
                "2066年4月2日",
                "我们挖到了一些奇怪的东西...那不是矿石...\n\n那是某种古老的遗迹，里面传来低沉的嗡嗡声。\n\n我不该再待在这里了..."
        ));

        defaultFragments.add(new FragmentData(
                3,
                1,
                0,
                "匿名研究者",
                "残页三",
                "2066年5月10日",
                "有些东西比黄金更珍贵，也更危险...\n\n那批货物来自地下深处，我把它们藏在了北方的废弃矿坑里。\n\n如果你能找到它们，就是你的了。但记住，知识是有代价的..."
        ));

        defaultFragments.add(new FragmentData(
                4,
                1,
                0,
                "最后的记录者",
                "残页四",
                "2066年6月1日",
                "我终于明白了真相...\n\n那些感染者...他们不是怪物。\n\n他们是在进化。\n\n如果你看到了这个，请继续往北走...那里有答案..."
        ));

        try {
            JsonDataStore.writeAtomic(FRAGMENT_DATA_PATH, GSON, defaultFragments);
            fragmentConfigWritable = true;
            DreamingFishCore.LOGGER.info("默认片段配置已保存");
            return true;
        } catch (Exception exception) {
            fragmentConfigWritable = false;
            DreamingFishCore.LOGGER.error("保存默认片段配置失败：{}", FRAGMENT_DATA_PATH, exception);
            return false;
        }
    }

    // ==================== 玩家数据加载/保存 ====================

    /**
     * 加载当前世界的玩家随记本数据。片段定义仍从全局配置加载。
     */
    public static void loadWorldData(MinecraftServer server) {
        loadFragmentData();
        PLAYER_DATA_CACHE.clear();
        DIRTY_PLAYERS.clear();
        try {
            Map<String, StoryBookData> dataMap = JsonDataStore.read(
                    playerDataPath(server), GSON, PLAYER_DATA_TYPE, HashMap::new);
            for (Map.Entry<String, StoryBookData> entry : dataMap.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    if (entry.getValue() != null) {
                        PLAYER_DATA_CACHE.put(uuid, entry.getValue());
                    }
                } catch (IllegalArgumentException exception) {
                    DreamingFishCore.LOGGER.warn("跳过无效的玩家 UUID：{}", entry.getKey());
                }
            }
            DreamingFishCore.LOGGER.info("随记本玩家数据加载完成，共 {} 个玩家", PLAYER_DATA_CACHE.size());
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("读取世界随记本数据失败，本次会话不会覆盖损坏文件", exception);
        } finally {
            loaded = true;
        }
    }

    /**
     * 只在玩家随记本数据发生变化时保存。
     */
    public static boolean saveIfDirty(MinecraftServer server) {
        if (!loaded || DIRTY_PLAYERS.isEmpty()) {
            return true;
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
    public static void clearWorldCache() {
        PLAYER_DATA_CACHE.clear();
        DIRTY_PLAYERS.clear();
        loaded = false;
    }

    private static Path playerDataPath(MinecraftServer server) {
        return WorldDataPaths.resolve(server, "storybook", "player_progress.json");
    }

    /**
     * 标记玩家数据为脏（需要保存）
     */
    public static void markPlayerDirty(UUID playerUuid) {
        ensureLoaded();
        if (playerUuid != null) {
            DIRTY_PLAYERS.add(playerUuid);
        }
    }

    // ==================== 片段查询方法 ====================

    /**
     * 根据片段ID获取片段数据
     */
    public static FragmentData getFragment(int fragmentId) {
        return FRAGMENT_CACHE.get(fragmentId);
    }

    /**
     * 根据阶段ID获取该阶段的所有片段
     */
    public static List<FragmentData> getFragmentsByStage(int stageId) {
        return new ArrayList<>(STAGE_INDEX.getOrDefault(stageId, Collections.emptyList()));
    }

    /**
     * 根据章节ID获取该章节的所有片段
     */
    public static List<FragmentData> getFragmentsByChapter(int chapterId) {
        return new ArrayList<>(CHAPTER_INDEX.getOrDefault(chapterId, Collections.emptyList()));
    }

    /**
     * 获取所有片段
     */
    public static Map<Integer, FragmentData> getAllFragments() {
        return new HashMap<>(FRAGMENT_CACHE);
    }

    /**
     * 获取片段总数
     */
    public static int getFragmentCount() {
        return FRAGMENT_CACHE.size();
    }

    /**
     * 检查片段是否存在
     */
    public static boolean hasFragment(int fragmentId) {
        return FRAGMENT_CACHE.containsKey(fragmentId);
    }

    // ==================== 玩家数据查询方法 ====================

    /**
     * 获取玩家的随记本数据
     */
    public static StoryBookData getPlayerStoryBook(UUID playerUuid) {
        ensureLoaded();
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

    /**
     * 解锁片段
     */
    public static boolean unlockFragmentForPlayer(UUID playerUuid, int fragmentId) {
        if (!hasFragment(fragmentId)) {
            return false;
        }

        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        boolean unlocked = storyBook.unlockFragment(fragmentId);
        if (unlocked) {
            markPlayerDirty(playerUuid);
        }
        return unlocked;
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
     * 标记片段已读
     */
    public static void markFragmentReadForPlayer(UUID playerUuid, int fragmentId) {
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        storyBook.markFragmentRead(fragmentId);
        markPlayerDirty(playerUuid);
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
        for (Integer fragmentId : storyBook.getSortedFragmentIds()) {
            FragmentData fragmentData = getFragment(fragmentId);
            if (fragmentData == null) {
                continue;
            }
            entries.add(new StoryBookEntryViewData(
                    fragmentData.getId(),
                    fragmentData.getStageId(),
                    fragmentData.getChapterId(),
                    fragmentData.getTitle(),
                    fragmentData.getContent(),
                    fragmentData.getTime(),
                    fragmentData.getAuthorName(),
                    storyBook.hasReadFragment(fragmentId)
            ));
        }
        return entries;
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

    public static void updateFragmentOrderForPlayer(UUID playerUuid, List<Integer> orderedFragmentIds) {
        if (orderedFragmentIds == null) {
            return;
        }

        StoryBookData storyBook = getPlayerStoryBook(playerUuid);
        storyBook.setObtainedOrder(orderedFragmentIds);
        markPlayerDirty(playerUuid);
    }

    public static boolean useFragmentPage(ServerPlayer player) {
        return useFragmentPage(player, null);
    }

    /**
     * 使用片段残页。
     * 如果物品指定了 fragmentId，则直接读取对应 json 片段；否则按默认顺序补全下一条内容。
     */
    public static boolean useFragmentPage(ServerPlayer player, Integer specifiedFragmentId) {
        UUID playerUuid = player.getUUID();
        StoryBookData storyBook = getPlayerStoryBook(playerUuid);

        if (specifiedFragmentId == null) {
            player.sendSystemMessage(Component.literal("§c这张残页没有绑定编号，无法整理出新的内容。"));
            return false;
        }

        if (!hasFragment(specifiedFragmentId)) {
            player.sendSystemMessage(Component.literal("§c指定的残页编号不存在：§f" + specifiedFragmentId));
            return false;
        }

        boolean firstUse = !storyBook.isJourneyStarted();
        giveStoryBookToPlayer(playerUuid);
        ensurePlayerHasStoryBookItem(player);

        if (firstUse) {
            startPlayerJourney(playerUuid);
            unlockChapterForPlayer(playerUuid, 0);
            grantJourneyStartedAdvancement(player);
            player.sendSystemMessage(Component.literal("§6已解锁成就：§e远旅开端"));
        }

        incrementFragmentPageUseCount(playerUuid);

        int fragmentIdToDisplay = specifiedFragmentId;
        if (!storyBook.hasUnlockedFragment(fragmentIdToDisplay)) {
            unlockFragmentForPlayer(playerUuid, fragmentIdToDisplay);
            FragmentData fragmentData = getFragment(fragmentIdToDisplay);
            if (fragmentData != null) {
                unlockChapterForPlayer(playerUuid, fragmentData.getChapterId());
                player.sendSystemMessage(Component.literal("§a你拼出了新的内容：§f" + fragmentData.getTitle()));
            }
        } else {
            player.sendSystemMessage(Component.literal("§7你翻开了已收录的残页：§f" + specifiedFragmentId));
        }

        FragmentData fragmentToDisplay = getFragment(fragmentIdToDisplay);
        if (fragmentToDisplay == null) {
            player.sendSystemMessage(Component.literal("§c这张残页上没有可读取的内容。"));
            return false;
        }

        markFragmentReadForPlayer(playerUuid, fragmentIdToDisplay);
        DreamingFishCore_NetworkManager.sendToClient(new Packet_OpenStoryFragmentGUI(fragmentToDisplay), player);
        levelUpStoryFeedback(player);
        markPlayerDirty(playerUuid);
        return true;
    }

    private static int getLatestUnlockedFragmentId(StoryBookData storyBook) {
        List<Integer> obtainedOrder = storyBook.getObtainedOrder();
        if (!obtainedOrder.isEmpty()) {
            return obtainedOrder.get(obtainedOrder.size() - 1);
        }
        return storyBook.getUnlockedFragmentIds().stream()
                .max(Integer::compareTo)
                .orElse(-1);
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
