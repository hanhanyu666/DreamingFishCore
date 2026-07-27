package com.hhy.dreamingfishcore.gameplay.npc_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.gameplay.npc_system.network.Packet_OpenNpcDialogueGUI;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.loading.FMLPaths;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class NpcManager {
    public static final String ENTITY_NPC_ID_TAG = "DreamingFishCoreNpcId";
    private static final double MAX_INTERACTION_DISTANCE_SQR = 8.0D * 8.0D;
    private static final Path NPC_DATA_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(DreamingFishCore.MODID)
            .resolve("npc_data.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Type NPC_MAP_TYPE = new TypeToken<Map<Integer, NpcData>>() {}.getType();

    private static Map<Integer, NpcData> npcCache = new ConcurrentHashMap<>();
    private static boolean configWritable;

    public static void init() {
        load();
    }

    public static synchronized void load() {
        if (Files.notExists(NPC_DATA_PATH)) {
            npcCache = new ConcurrentHashMap<>();
            createDefaultNpc();
            configWritable = true;
            save();
            return;
        }

        try {
            if (Files.size(NPC_DATA_PATH) == 0L) {
                configWritable = false;
                DreamingFishCore.LOGGER.error("NPC 配置为空，保留当前内存数据并拒绝覆盖文件：{}", NPC_DATA_PATH);
                return;
            }

            Map<Integer, NpcData> loaded = JsonDataStore.read(
                    NPC_DATA_PATH,
                    GSON,
                    NPC_MAP_TYPE,
                    ConcurrentHashMap::new);
            npcCache = new ConcurrentHashMap<>(loaded);
            configWritable = true;
            if (npcCache.isEmpty()) {
                createDefaultNpc();
                save();
            }
            DreamingFishCore.LOGGER.info("NPC数据加载完成，共 {} 个NPC", npcCache.size());
        } catch (Exception exception) {
            configWritable = false;
            DreamingFishCore.LOGGER.error(
                    "NPC 配置及备份读取失败，保留当前内存数据并拒绝覆盖文件：{}",
                    NPC_DATA_PATH,
                    exception);
        }
    }

    public static synchronized boolean save() {
        if (!configWritable) {
            DreamingFishCore.LOGGER.error("NPC 配置未安全加载，拒绝覆盖文件：{}", NPC_DATA_PATH);
            return false;
        }
        try {
            JsonDataStore.writeAtomic(NPC_DATA_PATH, GSON, npcCache);
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("保存NPC数据失败：{}", NPC_DATA_PATH, exception);
            return false;
        }
    }

    public static Optional<NpcData> getNpc(int npcId) {
        return Optional.ofNullable(npcCache.get(npcId));
    }

    public static List<NpcData> getAllNpcs() {
        List<NpcData> npcs = new ArrayList<>(npcCache.values());
        npcs.sort(Comparator.comparingInt(NpcData::getNpcId));
        return npcs;
    }

    public static boolean openNpcDialogue(ServerPlayer player, int npcId) {
        return openNpcDialogue(player, npcId, -1);
    }

    public static boolean openNpcDialogue(ServerPlayer player, int npcId, int entityId) {
        Optional<NpcData> npc = getNpc(npcId);
        if (npc.isEmpty()) {
            return false;
        }
        DreamingFishCore_NetworkManager.sendToClient(new Packet_OpenNpcDialogueGUI(createViewData(player, npc.get(), entityId)), player);
        return true;
    }

    public static void handleInteraction(ServerPlayer player, int npcId, int entityId, NpcInteractionType interactionType) {
        if (!isValidInteractionTarget(player, npcId, entityId)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你距离该 NPC 太远，或 NPC 已经失效。"));
            DreamingFishCore.LOGGER.warn("拒绝玩家 {} 的无效 NPC 交互请求：npcId={}, entityId={}",
                    player.getScoreboardName(), npcId, entityId);
            return;
        }

        Optional<NpcData> optionalNpc = getNpc(npcId);
        if (optionalNpc.isEmpty()) {
            return;
        }

        NpcData npc = optionalNpc.get();
        int requiredFavorability = npc.getActionFavorabilityRequirements().getOrDefault(interactionType.name(), 0);
        if (!NpcRelationManager.canUseAction(npcId, player.getUUID(), interactionType, requiredFavorability)) {
            openNpcDialogue(player, npcId, entityId);
            return;
        }

        if (interactionType == NpcInteractionType.DIALOGUE) {
            NpcRelationManager.addFavorability(npcId, player.getUUID(), 1);
        } else if (interactionType == NpcInteractionType.GIFT_ITEM) {
            handleGift(player, npc);
        }

        openNpcDialogue(player, npcId, entityId);
    }

    /**
     * 校验客户端提交的 NPC 与实体绑定。管理员通过 /npc open 打开的测试界面没有实体 ID，
     * 仅允许管理员继续操作；普通玩家必须与同维度、仍存活且绑定正确的实体保持合理距离。
     */
    private static boolean isValidInteractionTarget(ServerPlayer player, int npcId, int entityId) {
        if (entityId < 0) {
            return player.hasPermissions(2);
        }

        Entity target = player.serverLevel().getEntity(entityId);
        if (target == null || !target.isAlive() || target.level() != player.level()) {
            return false;
        }
        if (!target.getPersistentData().contains(ENTITY_NPC_ID_TAG)
                || target.getPersistentData().getInt(ENTITY_NPC_ID_TAG) != npcId) {
            return false;
        }
        return player.distanceToSqr(target) <= MAX_INTERACTION_DISTANCE_SQR;
    }

    public static NpcDialogueViewData createViewData(ServerPlayer player, NpcData npc) {
        return createViewData(player, npc, -1);
    }

    public static NpcDialogueViewData createViewData(ServerPlayer player, NpcData npc, int entityId) {
        NpcRelationData relation = NpcRelationManager.getRelation(npc.getNpcId(), player.getUUID());
        NpcThoughtData thought = npc.getCurrentThought();
        return new NpcDialogueViewData(
                npc.getNpcId(),
                entityId,
                npc.getNpcName(),
                npc.getNpcIntroduction(),
                npc.getNpcGender(),
                npc.getNpcProfession(),
                npc.getStoryStageId(),
                npc.getDialogues(),
                thought == null ? "" : thought.getThoughtText(),
                thought == null ? "" : thought.getWantedItemId(),
                relation.getFavorability(),
                relation.getRelationType().getDisplayName(),
                getAvailableActionNames(player, npc)
        );
    }

    private static void handleGift(ServerPlayer player, NpcData npc) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        NpcThoughtData thought = npc.getCurrentThought();
        int favorability = 2;
        if (thought != null && itemId.equals(thought.getWantedItemId())) {
            favorability = thought.getFavorabilityReward();
        }
        stack.shrink(1);
        NpcRelationManager.addFavorability(npc.getNpcId(), player.getUUID(), favorability);
    }

    private static List<String> getAvailableActionNames(ServerPlayer player, NpcData npc) {
        List<String> actions = new ArrayList<>();
        for (NpcInteractionType type : NpcInteractionType.values()) {
            int required = npc.getActionFavorabilityRequirements().getOrDefault(type.name(), 0);
            if (NpcRelationManager.canUseAction(npc.getNpcId(), player.getUUID(), type, required)) {
                actions.add(type.name());
            }
        }
        return actions;
    }

    private static void createDefaultNpc() {
        NpcData npc = new NpcData(1, "剧情记录员", "他记录着服务器共同推进的故事，也会记住每位玩家与他的交情。", "未知", "记录员");
        List<String> dialogues = new ArrayList<>();
        dialogues.add("欢迎回来。这个世界的故事不是一个人写完的。");
        dialogues.add("如果你带来了我正在寻找的东西，我会记住这份帮助。");
        npc.setDialogues(dialogues);
        npc.setStoryStageId(1);
        npc.setCurrentThought(new NpcThoughtData("我现在想要一个苹果，用来确认赠礼系统是否正常。", "minecraft:apple", "", 20, 0.0D));
        Map<String, Integer> requirements = new HashMap<>();
        requirements.put(NpcInteractionType.DIALOGUE.name(), 0);
        requirements.put(NpcInteractionType.GIFT_ITEM.name(), 0);
        requirements.put(NpcInteractionType.FOLLOW.name(), 300);
        requirements.put(NpcInteractionType.SET_HOME.name(), 300);
        requirements.put(NpcInteractionType.VIEW_BACKPACK.name(), 600);
        requirements.put(NpcInteractionType.ASSIGN_TASK.name(), 100);
        requirements.put(NpcInteractionType.WARNING_RULES.name(), 600);
        npc.setActionFavorabilityRequirements(requirements);
        npcCache.put(npc.getNpcId(), npc);
    }

}
