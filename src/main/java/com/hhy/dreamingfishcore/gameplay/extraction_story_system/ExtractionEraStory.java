package com.hhy.dreamingfishcore.gameplay.extraction_story_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryTaskData;
import com.hhy.dreamingfishcore.gameplay.story_system.runtime.StoryTextCatalog;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 第三阶段：梦外行动（搜打撤期）。
 *
 * <p>接在开篇期（编号 1）与余梦期（编号 2）之后，主题是把玩家从"在梦里活下去"
 * 推向"主动进入梦外的对局地图搜物资并活着带回来"——也就是搜打撤玩法的剧情外壳。</p>
 *
 * <p><b>本类只提供阶段与任务定义和触发入口</b>，遵循这套剧情系统的既有分工：</p>
 * <ul>
 *   <li>阶段/任务的顺序与编号写在 Java（本类），运行时绝不从 JSON 读流程；</li>
 *   <li>文案走 {@link StoryTextCatalog#textOrDefault}，键缺失时用这里的兜底文本，
 *       所以新键不必先改策划文案文件就能跑；策划以后补上同名键即可覆盖。</li>
 * </ul>
 *
 * <p><b>任务编号刻意取 3001 起的高段</b>：定义校验要求任务编号全局唯一，
 * 取高段可以避免与开篇期/余梦期已占用的低编号相撞——撞了会让整个故事定义校验失败、
 * 服务器起不来，属于最容易犯又最难查的错。</p>
 */
public final class ExtractionEraStory {

    /** 阶段稳定 ID（与阶段编号 3 一起构成唯一标识）。 */
    public static final String STAGE_ID = "dreamingfishcore:extraction_era";

    /** 文案键：策划可在故事文案文件里覆盖。 */
    public static final String STAGE_NAME_KEY = "extraction_era.stage.name";
    public static final String STAGE_DESCRIPTION_KEY = "extraction_era.stage.description";
    public static final String TASK_BRIEFING_NAME_KEY = "extraction_era.task.briefing.name";
    public static final String TASK_BRIEFING_CONTENT_KEY = "extraction_era.task.briefing.content";
    public static final String TASK_FIRST_EXTRACT_NAME_KEY = "extraction_era.task.first_extract.name";
    public static final String TASK_FIRST_EXTRACT_CONTENT_KEY = "extraction_era.task.first_extract.content";
    public static final String TASK_BRING_BACK_NAME_KEY = "extraction_era.task.bring_back.name";
    public static final String TASK_BRING_BACK_CONTENT_KEY = "extraction_era.task.bring_back.content";

    /** 任务稳定 ID。 */
    public static final String TASK_BRIEFING_ID = "dreamingfishcore:extraction_era_briefing";
    public static final String TASK_FIRST_EXTRACT_ID = "dreamingfishcore:extraction_era_first_extract";
    public static final String TASK_BRING_BACK_ID = "dreamingfishcore:extraction_era_bring_back";

    /**
     * 联络人的"梦外行动简报"NPC 消息 ID。
     *
     * <p>定义在 JAR 默认消息文件 {@code dreamingfishcore/defaults/npc_messages.json} 里，
     * 用现有联络人 NPC（周岑，id 105）发出，避免为一条简报新增 NPC。</p>
     */
    public static final String BRIEFING_MESSAGE_ID = "dreamingfishcore:extraction_era/zhoucen/briefing";

    private static final String STAGE_DESCRIPTION =
            "梦外的门被推开了一条缝。带上装备进去，把还能用的东西带回来——"
                    + "记住，值钱的是你活着走出来这件事本身。";

    private ExtractionEraStory() {
    }

    /** 生成第三阶段的定义。唯一入口，由 StoryManager 装配。 */
    public static StoryStageData createStageDefinition() {
        StoryStageData stage = new StoryStageData(STAGE_ID, 3,
                StoryTextCatalog.textOrDefault(STAGE_NAME_KEY, "梦外行动"),
                StoryTextCatalog.textOrDefault(STAGE_DESCRIPTION_KEY, STAGE_DESCRIPTION));
        createTasks().forEach(stage::addTask);
        return stage;
    }

    static List<StoryTaskData> createTasks() {
        return List.of(
                task(TASK_BRIEFING_ID, 3001,
                        StoryTextCatalog.textOrDefault(TASK_BRIEFING_NAME_KEY, "了解梦外行动"),
                        StoryTextCatalog.textOrDefault(TASK_BRIEFING_CONTENT_KEY,
                                "听完联络人对梦外对局的说明：进去搜物资、活着走到撤离点、"
                                        + "带出来的东西才算你的。")),
                task(TASK_FIRST_EXTRACT_ID, 3002,
                        StoryTextCatalog.textOrDefault(TASK_FIRST_EXTRACT_NAME_KEY, "第一次活着回来"),
                        StoryTextCatalog.textOrDefault(TASK_FIRST_EXTRACT_CONTENT_KEY,
                                "进入一次梦外对局并成功撤离。死了就什么也带不回来。")),
                task(TASK_BRING_BACK_ID, 3003,
                        StoryTextCatalog.textOrDefault(TASK_BRING_BACK_NAME_KEY, "带一件有用的东西回来"),
                        StoryTextCatalog.textOrDefault(TASK_BRING_BACK_CONTENT_KEY,
                                "把对局里找到的物资带到撤离点并成功撤出，让它在梦外也属于你。")));
    }

    /**
     * NPC 消息已读：读完联络人的"梦外行动简报"就算了解了规则（完成任务 3001）。
     *
     * <p>与开篇期/余梦期的写法一致：先比对阶段，再幂等写入。写入统一走
     * {@link StoryManager#recordPlayerTaskProgress}——它的注释写明"入口只接受服务端剧情验证后的完成"，
     * 而"玩家确实读了这条私信"就是服务端事实。</p>
     */
    public static synchronized void onNpcMessageRead(ServerPlayer player, String definitionId, int npcId) {
        if (player == null || !BRIEFING_MESSAGE_ID.equals(definitionId)) {
            return;
        }
        if (!STAGE_ID.equals(StoryManager.getCurrentStageIdOrDefault())) {
            return;     // 不在本章节：不计入，也不报错
        }
        if (StoryManager.isPlayerFinishedTask(TASK_BRIEFING_ID, player.getUUID())) {
            return;     // 幂等
        }
        if (StoryManager.recordPlayerTaskProgress(TASK_BRIEFING_ID,
                player.getGameProfile().getName(), player.getUUID())) {
            DreamingFishCore.LOGGER.info("[extraction_story] {} 读完梦外行动简报，完成剧情任务 {}",
                    player.getGameProfile().getName(), TASK_BRIEFING_ID);
        }
    }

    /**
     * 造一个个人任务。
     *
     * <p>与开篇期/余梦期保持同一写法：默认发布、个人作用域。这里不绑地点与引导
     * （撤离发生在对局地图里，不依赖固定坐标；简报由 NPC 私信触发）。</p>
     */
    private static StoryTaskData task(String id, int number, String name, String content) {
        StoryTaskData task = new StoryTaskData(id, number, name, content, 0L, 0L);
        task.setPublishedByDefault(true);
        task.setScope(StoryTaskData.Scope.PERSONAL);
        task.setLocationId("");
        return task;
    }
}
