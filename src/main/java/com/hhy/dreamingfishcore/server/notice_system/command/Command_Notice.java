package com.hhy.dreamingfishcore.server.notice_system.command;

import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.server.notice_system.NoticeCategory;
import com.hhy.dreamingfishcore.server.notice_system.NoticeData;
import com.hhy.dreamingfishcore.server.notice_system.NoticeDeliveryService;
import com.hhy.dreamingfishcore.server.notice_system.NoticeManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * 公告管理指令
 * /notice add "标题" "内容" - 添加服务器通知（兼容旧用法）
 * /notice add maintenance "标题" "内容" - 添加服务器通知（MAINTENANCE）
 * /notice add game "stageId" "storyDate" "标题" "内容" - 添加游戏公告
 * /notice delete <ID> - 删除指定ID的公告
 * /notice list - 列出所有公告
 * /notice reload - 重新加载公告配置
 *
 * 支持 & 符号代替颜色符号：
 * &a(绿) &b(青) &c(红) &d(粉) &e(黄) &f(白) &l(粗体) &m(删除线) &n(下划线) &o(斜体) &r(重置)
 */
public class Command_Notice {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("notice")
                .requires(source -> source.hasPermission(2));
        root.then(Commands.literal("add")
                .then(Commands.argument("input", StringArgumentType.greedyString())
                        .executes(Command_Notice::executeAddNotice)));
        root.then(Commands.literal("delete")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(Command_Notice::executeDeleteNotice)));
        root.then(Commands.literal("list")
                .executes(Command_Notice::executeListNotices));
        root.then(Commands.literal("reload")
                .executes(Command_Notice::executeReload));
        dispatcher.register(root);
    }

    /** 添加服务器通知（MAINTENANCE，旧命令格式仍然有效）或带故事元数据的游戏公告。 */
    private static int executeAddNotice(CommandContext<CommandSourceStack> context) {
        String input = StringArgumentType.getString(context, "input");

        String trimmedInput = input == null ? "" : input.trim();
        NoticeCategory category = NoticeCategory.MAINTENANCE;
        String stageId = "";
        String storyDate = "";
        String[] parts;

        if (hasCommandPrefix(trimmedInput, "game")) {
            category = NoticeCategory.GAME;
            parts = parseQuotedStrings(stripCommandPrefix(trimmedInput, "game"));
            if (parts.length != 4) {
                sendUsageFailure(context, "§c格式错误！游戏公告用法：/notice add game \"stageId\" \"storyDate\" \"标题\" \"内容\"");
                return 0;
            }
            stageId = parts[0].trim();
            storyDate = parts[1].trim();
            if (stageId.isEmpty() || storyDate.isEmpty()) {
                context.getSource().sendFailure(Component.literal("§c游戏公告的 stageId 和 storyDate 不能为空"));
                return 0;
            }
            if (StoryManager.getStage(stageId) == null) {
                context.getSource().sendFailure(Component.literal("§c不存在故事阶段：" + stageId));
                return 0;
            }
        } else if (hasCommandPrefix(trimmedInput, "maintenance")) {
            parts = parseQuotedStrings(stripCommandPrefix(trimmedInput, "maintenance"));
            if (parts.length != 2) {
                sendUsageFailure(context, "§c格式错误！服务器通知用法：/notice add maintenance \"标题\" \"内容\"");
                return 0;
            }
        } else {
            // 保留旧命令：未声明类别的公告一律按服务器通知（MAINTENANCE）处理。
            parts = parseQuotedStrings(trimmedInput);
            if (parts.length != 2) {
                sendUsageFailure(context, "§c格式错误！兼容用法：/notice add \"标题\" \"内容\"");
                return 0;
            }
        }

        String title = category == NoticeCategory.GAME ? parts[2] : parts[0];
        String content = category == NoticeCategory.GAME ? parts[3] : parts[1];

        // 将 & 替换为 §
        String formattedTitle = title.replace("&", "§");
        String formattedContent = content.replace("&", "§");

        int newId = NoticeManager.getMaxNoticeId() + 1;
        long now = System.currentTimeMillis();

        NoticeData newNotice = new NoticeData(
                newId, formattedTitle, formattedContent, now,
                category, stageId, storyDate, "");

        if (NoticeManager.addNotice(newNotice)) {
            context.getSource().sendSuccess(
                    () -> Component.literal("§a成功添加公告 [ID:" + newId + "]: " + formattedTitle),
                    true
            );
            NoticeDeliveryService.publishToEligibleOnlinePlayers(newNotice);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§c添加公告失败"));
            return 0;
        }
    }

    /**
     * 解析引号包裹的字符串
     * 例如：输入 "标题" "内容" -> 返回 ["标题", "内容"]
     */
    private static String[] parseQuotedStrings(String input) {
        java.util.List<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '"') {
                if (inQuotes) {
                    // 结束引号
                    result.add(current.toString());
                    current = new StringBuilder();
                }
                inQuotes = !inQuotes;
            } else if (inQuotes) {
                current.append(c);
            } else if (!Character.isWhitespace(c)) {
                // 所有参数都必须使用引号，避免把错误的前缀静默吞掉。
                return new String[0];
            }
        }

        // 如果最后一个引号没有闭合，返回空结果让调用方给出准确用法。
        if (inQuotes) {
            return new String[0];
        }

        return result.toArray(new String[0]);
    }

    private static boolean hasCommandPrefix(String input, String prefix) {
        return input.equals(prefix)
                || input.startsWith(prefix + " ")
                || input.startsWith(prefix + "\t");
    }

    private static String stripCommandPrefix(String input, String prefix) {
        return input.substring(prefix.length()).trim();
    }

    private static void sendUsageFailure(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(Component.literal(message));
    }

    /**
     * 删除指定ID的公告
     * 用法：/notice delete <ID>
     */
    private static int executeDeleteNotice(CommandContext<CommandSourceStack> context) {
        int noticeId = IntegerArgumentType.getInteger(context, "id");

        if (NoticeManager.getNoticeById(noticeId) == null) {
            context.getSource().sendFailure(Component.literal("§c删除失败：未找到ID为 " + noticeId + " 的公告"));
            return 0;
        }

        if (NoticeManager.deleteNotice(noticeId)) {
            context.getSource().sendSuccess(
                    () -> Component.literal("§a成功删除公告 [ID:" + noticeId + "]"),
                    true
            );
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("§c删除失败：配置文件写入失败，操作已回滚"));
            return 0;
        }
    }

    /**
     * 列出所有公告
     * 用法：/notice list
     */
    private static int executeListNotices(CommandContext<CommandSourceStack> context) {
        var notices = NoticeManager.getNotices();

        if (notices.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("§e当前没有公告"),
                    false
            );
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.literal("§6===== 公告列表 (共 " + notices.size() + " 条) ====="),
                false
        );

        for (NoticeData notice : notices) {
            String timeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
                    .format(new java.util.Date(notice.getPublishTime()));
            String categoryLabel;
            if (notice.getCategory() == NoticeCategory.GAME) {
                categoryLabel = "§6游戏公告 §7[阶段:" + notice.getStoryStageId()
                        + " | 剧情日期:" + notice.getStoryDate() + "]";
            } else {
                categoryLabel = "§b服务器通知";
            }
            context.getSource().sendSuccess(
                    () -> Component.literal("§e[ID:" + notice.getNoticeId() + "] §f" + notice.getNoticeTitle()
                            + " §7(" + timeStr + ") " + categoryLabel),
                    false
            );
        }

        return notices.size();
    }

    /**
     * 重新加载公告配置
     * 用法：/notice reload
     */
    private static int executeReload(CommandContext<CommandSourceStack> context) {
        NoticeManager.loadFromConfig();
        int count = NoticeManager.getNotices().size();

        context.getSource().sendSuccess(
                () -> Component.literal("§a已重新加载公告配置，当前共 " + count + " 条公告"),
                true
        );

        return 1;
    }
}
