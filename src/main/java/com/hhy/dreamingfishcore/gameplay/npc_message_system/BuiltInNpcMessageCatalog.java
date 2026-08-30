package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.npc_system.StoryNpcContentPolicy;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageCatalog;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 内置开场私信内容。
 *
 * <p>NPC 私信配置属于服主可编辑内容，因此这里采用“只补缺失 ID”的方式。
 * 这样已有服务器可以保留自己的文案，新版本只会增加本次开场需要的内容。</p>
 */
public final class BuiltInNpcMessageCatalog {
    public static final String OPENING_STAGE_ID = StoryStageCatalog.DREAM_BEGINNING_ID;
    private static final String BUILT_IN_MESSAGE_RESOURCE =
            "/dreamingfishcore/defaults/npc_messages.json";
    private static final Gson GSON = new GsonBuilder().create();

    /** 白芷给每位玩家发送的一次性生存观察。内部 ID 保持不变，以兼容已有存档。 */
    public static final String BAIZHI_FIRST_STAGE_PROTOCOL_ID =
            "dreamingfishcore:baizhi/first_stage_protocol";

    static final String LEGACY_BAIZHI_PROTOCOL_SUBJECT = "白芷 · 第一阶段临时生理协议";
    static final String LEGACY_BAIZHI_PROTOCOL_CONTENT =
            "这里是白芷。先把目前能确认的规则写清楚：第一阶段允许身体依靠自然状态缓慢恢复，但自然回血最多只到最大生命值的 70%；超过这条线，要靠医疗物资或人工处理。\n\n白天的观察窗口会让未完全感染者的感染读数逐渐回落；完整一个白天约降低 5 点（感染值按 0—100 计）。这不是治愈，也不会把已经转为感染者的状态直接逆转。\n\n重生节点每天会补回 5 点分裂储备，上限 100 点；死亡时的消耗规则不变。把它当作临时缓冲，不要因此冒险。阶段推进后协议可能调整，我会在终端里再通知你。";
    static final String PREVIOUS_BAIZHI_OBSERVATIONS_CONTENT =
            "我把这几天观察到的情况理了一遍，你先记着。\n\n"
                    + "人的身体还会自己慢慢恢复，不过伤势缓到一半左右就会停住。再往上，光靠吃东西和休息没有用，得找药，或者让懂得处理伤口的人来。金苹果和其他能直接恢复伤势的东西不受这个限制。\n\n"
                    + "白天对感染似乎有些压制。只要还没有彻底失去控制，从天亮撑到天黑，感染大概能降下去五点。别把这当成痊愈，已经发生的变化不会自己倒回去。\n\n"
                    + "重生节点也在自行恢复，每天会补回五点分裂储备，最多存到一百。死一次该消耗的还是会照常消耗，所以别仗着它会回补就去冒险。\n\n"
                    + "目前我能确认的只有这些。要是观察结果有变化，我再告诉你。照顾好自己。";
    static final String BAIZHI_OBSERVATIONS_SUBJECT = "这几天的观察";
    static final String BAIZHI_OBSERVATIONS_CONTENT =
            "我是白芷，很高兴认识你，我现在在阿拜多斯的学校做医疗志愿，你可以随时来找我。\n\n"
                    + "危机刚刚爆发，根据我最近的观察，目前大家的自愈能力尚未完全消失，但是应该有所下降，光靠进食似乎无法完全恢复自身的健康，需要额外的药品进行辅助。当然了，感染程度以及重生机制都会随着时间流逝缓慢恢复，这一切实在是太突然了，我还需要更多的观察，请多加小心。";

    private BuiltInNpcMessageCatalog() {
    }

    /**
     * 返回现有列表中缺失的内置消息；不会修改传入列表。
     */
    public static List<NpcMessageDefinition> createMissingMessages(
            List<NpcMessageDefinition> existing) {
        Set<String> existingIds = new HashSet<>();
        if (existing != null) {
            for (NpcMessageDefinition definition : existing) {
                if (definition != null) {
                    existingIds.add(definition.getId());
                }
            }
        }

        List<NpcMessageDefinition> additions = new ArrayList<>();
        List<NpcMessageDefinition> builtInMessages = loadCompleteMessages();
        if (builtInMessages.isEmpty()) {
            // 资源缺失时只保留白芷的最小观察消息；删除的角色绝不在代码回退中复活。
            builtInMessages = List.of(createBaizhiProtocol());
        }
        for (NpcMessageDefinition definition : builtInMessages) {
            if (definition != null) {
                addIfMissing(existingIds, additions, definition);
            }
        }
        return additions;
    }

    /** 读取 JAR 内的完整 NPC 私信集；返回的新对象可安全写入服主配置。 */
    static List<NpcMessageDefinition> loadCompleteMessages() {
        try (InputStream stream = BuiltInNpcMessageCatalog.class
                .getResourceAsStream(BUILT_IN_MESSAGE_RESOURCE)) {
            if (stream == null) {
                DreamingFishCore.LOGGER.error(
                        "未找到内置 NPC 私信：{}", BUILT_IN_MESSAGE_RESOURCE);
                return List.of();
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                NpcMessageConfig config = GSON.fromJson(reader, NpcMessageConfig.class);
                if (config == null || config.getMessages().isEmpty()) {
                    DreamingFishCore.LOGGER.error(
                            "内置 NPC 私信为空：{}", BUILT_IN_MESSAGE_RESOURCE);
                    return List.of();
                }
                List<NpcMessageDefinition> retained = new ArrayList<>();
                for (NpcMessageDefinition definition : config.getMessages()) {
                    if (definition != null
                            && StoryNpcContentPolicy.isRetained(definition.getNpcId())) {
                        retained.add(definition);
                    }
                }
                return retained;
            }
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error(
                    "读取内置 NPC 私信失败：{}", BUILT_IN_MESSAGE_RESOURCE, exception);
            return List.of();
        }
    }

    /**
     * 把未被服主改写过的旧版白芷文案更新为沉浸式版本。
     * 标题和正文分别比对，因此只会替换仍等于内置旧值的部分。
     */
    public static boolean migrateBaizhiObservationsCopy(
            List<NpcMessageDefinition> definitions) {
        if (definitions == null) {
            return false;
        }
        boolean changed = false;
        for (NpcMessageDefinition definition : definitions) {
            if (definition == null || !BAIZHI_FIRST_STAGE_PROTOCOL_ID.equals(definition.getId())) {
                continue;
            }
            String subject = LEGACY_BAIZHI_PROTOCOL_SUBJECT.equals(definition.getSubject())
                    ? BAIZHI_OBSERVATIONS_SUBJECT
                    : definition.getSubject();
            String content = isPreviousBaizhiContent(definition.getContent())
                    ? BAIZHI_OBSERVATIONS_CONTENT
                    : definition.getContent();
            changed |= definition.replaceText(subject, content);
        }
        return changed;
    }

    /** 已投递私信保存的是文案快照；启动时同步修复仍为旧内置文案的记录。 */
    public static boolean migrateDeliveredBaizhiObservations(
            List<NpcMessageRecord> records) {
        if (records == null) {
            return false;
        }
        boolean changed = false;
        for (NpcMessageRecord record : records) {
            if (record == null
                    || record.getDirection() != NpcMessageRecord.Direction.NPC_TO_PLAYER
                    || !BAIZHI_FIRST_STAGE_PROTOCOL_ID.equals(record.getDefinitionId())) {
                continue;
            }
            String subject = LEGACY_BAIZHI_PROTOCOL_SUBJECT.equals(record.getSubject())
                    ? BAIZHI_OBSERVATIONS_SUBJECT
                    : record.getSubject();
            String content = isPreviousBaizhiContent(record.getContent())
                    ? BAIZHI_OBSERVATIONS_CONTENT
                    : record.getContent();
            changed |= record.replaceText(subject, content);
        }
        return changed;
    }

    private static boolean isPreviousBaizhiContent(String content) {
        return LEGACY_BAIZHI_PROTOCOL_CONTENT.equals(content)
                || PREVIOUS_BAIZHI_OBSERVATIONS_CONTENT.equals(content);
    }

    private static void addIfMissing(
            Set<String> existingIds,
            List<NpcMessageDefinition> additions,
            NpcMessageDefinition definition) {
        if (existingIds.add(definition.getId())) {
            additions.add(definition);
        }
    }

    private static NpcMessageDefinition createBaizhiProtocol() {
        return new NpcMessageDefinition(
                BAIZHI_FIRST_STAGE_PROTOCOL_ID,
                101,
                BAIZHI_OBSERVATIONS_SUBJECT,
                BAIZHI_OBSERVATIONS_CONTENT,
                NpcMessageDefinition.DeliveryTrigger.MANUAL)
                .once(true)
                .priority(100);
    }

}
