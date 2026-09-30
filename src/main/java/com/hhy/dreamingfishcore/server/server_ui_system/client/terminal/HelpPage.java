package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.notice_system.network.Packet_NewPlayerGuideViewed;
import net.minecraft.client.Minecraft;

/** 梦屿生存手册：身体、体力勇气、死亡重生、感染、剧情与选择六个章节。 */
final class HelpPage extends TerminalPage {
    private record Topic(String number, String title, String subtitle, int accent, Icons icon,
                         String[] metrics, String[] facts, String tipTitle, String tipText) {
    }

    private static final Topic[] TOPICS = {
            new Topic("01", "身体与肢体", "命中部位决定实际承伤", 0xFFFF6677, Icons.SHIELD,
                    new String[]{"头部 ×1.2", "胸部 ×1.0", "腿 / 脚 ×0.9"},
                    new String[]{
                            "身体分为头、胸、腿、脚四个受伤区域；近战与弹射物会按实际命中部位结算。",
                            "头部受伤最危险，胸部保持原伤害，腿部和脚部受到的伤害较低。",
                            "受伤部位会在体征监测中短暂标出；跌落、火焰等环境伤害不使用肢体倍率。"
                    },
                    "主动承伤", "近战攻击临身前起跳，可能让命中落到腿或脚，以 ×0.9 承伤；但跳跃会消耗 3 点体力。"),
            new Topic("02", "体力与勇气", "行动资源与心理状态", 0xFFB58BFF, Icons.BOLT,
                    new String[]{"疾跑 -4 / 秒", "跳跃 -3", "停歇 +5 / 秒"},
                    new String[]{
                            "疾跑和跳跃消耗体力；停止消耗 5 秒后开始恢复，耗尽后须恢复到 20 点才能再次疾跑。",
                            "光亮、白天和 30 格内的同伴帮助恢复勇气；黑暗、夜晚、敌怪和地下深处会加速恐惧。",
                            "环境恢复通常止于 60；10 秒内击杀 5 只敌怪可增加 10 点，目睹附近玩家死亡会失去 10 点。",
                            "勇气低于 20% 获得虚弱 I 与缓慢 I；达到 85% 获得力量 I。"
                    },
                    "夜间行动", "带上光源、尽量结伴，并为撤离预留至少 20 点体力；不要让体力和勇气同时见底。"),
            new Topic("03", "死亡与重生", "模板重建余量、物品栏与尸体", 0xFFFFC857, Icons.REFRESH,
                    new String[]{"幸存者 5 点", "感染者 20 点", "每日 +5 点"},
                    new String[]{
                            "正常重生会扣除基础模板重建余量，物品留在死亡地点的尸体中，需要返回取回。",
                            "也可额外消耗 30 点保留物品栏：幸存者总计 35 点，感染者总计 50 点。",
                            "尸体会保存归属与位置；正常重生时可以锁定尸体，保护留下的物品。",
                            "余梦期的余量停止自然恢复。维护服务开放后，可向医疗工作人员提交物资，每个主世界游戏日办理一次；所需物品与恢复量以现场说明为准。"
                    },
                    "出发前检查", "在个人档案查看剩余模板重建余量。余量上限为 100；最后一点余量仍可完成一次标准重建，耗尽后的下一次死亡需要他人救援。"),
            new Topic("04", "感染与体征", "受伤会推动感染恶化", 0xFF8B5CF6, Icons.DROP,
                    new String[]{"受伤会累积", "白天 -5 / 日", "100% 感染者"},
                    new String[]{
                            "生命值净下降就会增加感染，增加量约为损失生命的 1/5；被丧尸击败还会额外增加。",
                            "在感染者 32 格内停留，每 30 秒也会增加 1 点感染；达到 80% 后获得虚弱 I 与缓慢 I。",
                            "根据目前的观察，未完全感染者在有天空的白天会逐渐回落，完整一个白天约降低 5 点；这不是治愈。",
                            "感染达到 100% 就会成为感染者，死亡时需要消耗更多模板重建余量；感染规则会随故事阶段变化。"
                    },
                    "关注体征", "饱食度触发的普通自然回血最多恢复到最大生命的 70%；金苹果、恢复效果和医疗物资不受此限制。"),
            new Topic("05", "共同推进剧情", "逐光会筹建与你的选择", 0xFF78D6A3, Icons.USERS,
                    new String[]{"全服任务", "个人对话", "加入与否"},
                    new String[]{
                            "当前阶段：逐光会正在筹建。你可以参与建设，也可以保持独立——两者都能正常游玩。",
                            "是否加入逐光会由你自己决定；独立协作者同样能参与救援、调查与公共事务。",
                            "回复 NPC、完成承诺或违背立场会改变好感度与关系；重要地点与目标会存入个人任务。",
                            "阶段由作者在合适时机推进，不设必须赶上的时限；成功与失败的任务都会被保留。"
                    },
                    "留意终端", "广播与 NPC 私信是剧情入口：地点、物资与建设需求会随消息和公告送达。"),
            new Topic("06", "选择与后果", "这里没有唯一标准答案", 0xFF8CCEFF, Icons.MAP,
                    new String[]{"可以分歧", "结果会保留", "世界会回应"},
                    new String[]{
                            "支持、拒绝、隐瞒或公开信息，都可能改变任务的成功与失败。",
                            "选择可能影响 NPC 关系、组织身份、可见线索，以及后续出现的人物与事件。",
                            "其他玩家可以作出相反决定；最终世界状态取决于全服行动汇合后的结果。",
                            "已经发生的阶段变化会进入世界历史，后来者将在这些结果之上继续行动。"
                    },
                    "先了解再决定", "意见不同时请与其他玩家讨论。不同选择并非错误，分歧本身也是梦屿故事的一部分。")
    };

    private static boolean reported;
    private int selected;

    HelpPage(TerminalScreen terminal) {
        super(terminal, "梦屿生存手册");
    }

    @Override
    protected void onShow() {
        if (!reported && Minecraft.getInstance().player != null && Minecraft.getInstance().getConnection() != null) {
            reported = true;
            DreamingFishCore_NetworkManager.sendToServer(new Packet_NewPlayerGuideViewed());
        }
    }

    private record Key(int topic, Responsive.Size size) {
    }

    @Override
    protected UiNode<?> build() {
        return Responsive.of(size -> Dynamic.of(() -> new Key(selected, size), key -> content(key.size())));
    }

    private UiNode<?> content(Responsive.Size size) {
        boolean compact = size == Responsive.Size.COMPACT;
        SideList<Integer> nav = new SideList<>(compact, index -> selected = index);
        for (int i = 0; i < TOPICS.length; i++) {
            Topic topic = TOPICS[i];
            nav.item(i, compact ? topic.number() + " " + topic.title() : topic.number() + "  " + topic.title(),
                    topic.subtitle(), topic.accent());
        }
        nav.select(selected);
        UiNode<?> detail = topicDetail(TOPICS[selected], size);
        if (compact) {
            return ScrollView.of(nav, detail).gap(Theme.Space.MD).padding(Theme.Space.MD, Theme.Space.SM).edgeFade(ColorRole.SURFACE);
        }
        UiNode<?> sidebar = Ui.column(
                Ui.column(TerminalUi.sectionLabel("SURVIVAL MANUAL"),
                        Ui.row(Text.of("梦屿生存手册").style(TextStyle.SUBTITLE).singleLine(), Ui.spacer(),
                                Text.of((selected + 1) + " / " + TOPICS.length).style(TextStyle.LABEL_STRONG)
                                        .color(TOPICS[selected].accent()).singleLine())).gap(2.0F),
                ScrollView.of(nav).grow(1.0F).basis(0.0F)
        ).gap(Theme.Space.MD).width(160.0F);
        return Ui.row(sidebar, ScrollView.of(detail).grow(1.0F).basis(0.0F).edgeFade(ColorRole.SURFACE))
                .alignItems(Align.STRETCH).gap(Theme.Space.LG)
                .padding(Theme.Space.LG, Theme.Space.MD, Theme.Space.LG, Theme.Space.SM);
    }

    private static UiNode<?> topicDetail(Topic topic, Responsive.Size size) {
        int accent = topic.accent();
        Box title = Ui.row(
                TerminalUi.iconBadge(topic.icon(), accent, 28.0F),
                Ui.column(Text.of("CHAPTER " + topic.number()).style(TextStyle.CAPTION_STRONG).color(accent).singleLine(),
                        Text.of(topic.title()).style(TextStyle.HEADLINE).singleLine(),
                        Text.of(topic.subtitle()).style(TextStyle.CAPTION).singleLine()).gap(1.0F).grow(1.0F).shrink(1.0F)
        ).gap(Theme.Space.MD).alignItems(Align.CENTER);

        Box metrics = Ui.row().gap(Theme.Space.SM).alignItems(Align.STRETCH);
        for (String metric : topic.metrics()) {
            metrics.add(Ui.column(Text.of(metric).style(TextStyle.LABEL_STRONG).centered().singleLine(),
                            accentLine(accent))
                    .gap(5.0F).padding(Theme.Space.SM, Theme.Space.MD, Theme.Space.SM, Theme.Space.SM)
                    .radius(Theme.Radius.MD).background(UiColor.withAlpha(0xFFFFFFFF, 0.04F))
                    .border(1.0F, UiColor.withAlpha(accent, 0.25F)).grow(1.0F).basis(0.0F));
        }

        Box facts = Ui.column().gap(Theme.Space.SM);
        for (String fact : topic.facts()) {
            facts.add(Ui.row(Ui.stack().size(5.0F, 5.0F).radius(2.5F).background(accent).margin(0.0F, 4.0F, 0.0F, 0.0F),
                    Text.of(fact).style(TextStyle.BODY_SECONDARY.withLineGap(3.0F)).grow(1.0F).shrink(1.0F))
                    .gap(Theme.Space.SM).alignItems(Align.START));
        }
        UiNode<?> factCard = TerminalUi.card().add(
                Ui.row(Icon.of(Icons.LIST, 10.0F).color(accent), Text.of("机制要点").style(TextStyle.LABEL_STRONG).color(accent)).gap(5.0F),
                facts);
        UiNode<?> tip = TerminalUi.card().accent(accent).tint(UiColor.withAlpha(accent, 0.08F)).add(
                Ui.row(Icon.of(Icons.SPARKLE, 10.0F).color(accent),
                        Text.of("生存提示 · " + topic.tipTitle()).style(TextStyle.LABEL_STRONG).color(accent)).gap(5.0F),
                Text.of(topic.tipText()).style(TextStyle.BODY.withLineGap(3.0F)));
        return Ui.column(title.enter(EnterEffect.FADE_UP), metrics.enter(EnterEffect.FADE_UP.delayed(40.0F)),
                factCard.enter(EnterEffect.FADE_UP.delayed(80.0F)), tip.enter(EnterEffect.FADE_UP.delayed(120.0F)))
                .gap(Theme.Space.MD);
    }

    private static UiNode<?> accentLine(int accent) {
        return Ui.stack().height(2.0F).radius(1.0F).background(UiColor.withAlpha(accent, 0.8F)).margin(8.0F, 0.0F);
    }
}
