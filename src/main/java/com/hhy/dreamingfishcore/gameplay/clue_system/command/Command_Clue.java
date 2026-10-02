package com.hhy.dreamingfishcore.gameplay.clue_system.command;

import com.hhy.dreamingfishcore.gameplay.clue_system.ClueCatalog;
import com.hhy.dreamingfishcore.gameplay.clue_system.ClueDefinition;
import com.hhy.dreamingfishcore.gameplay.clue_system.ClueGrantService;
import com.hhy.dreamingfishcore.gameplay.clue_system.ClueGrantSource;
import com.hhy.dreamingfishcore.gameplay.clue_system.ClueGuaranteeService;
import com.hhy.dreamingfishcore.gameplay.clue_system.ClueSecrets;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 线索目录的运维命令（权限跟随 {@code /dreamingfish} 根节点的 2 级）。
 *
 * <pre>
 * /dreamingfish clue info     查看目录、私密定义与发放入口的规模
 * /dreamingfish clue audit    可达性审计：哪些线索没有确定入口
 * /dreamingfish clue reload   重新加载可见内容与私密定义
 * </pre>
 *
 * <p>没有这条命令的话，服主改完 {@code clue_secrets.json} 里的发放入口声明必须重启服务器——
 * 而"声明驱动"的意义正在于改配置不用重启。</p>
 *
 * <p>重载只换内存里的目录与索引，不动玩家已有的发现记录（那些在世界存档里）。</p>
 */
public final class Command_Clue {

    private Command_Clue() {
    }

    /** 供 {@code /dreamingfish} 根节点复用；根节点与权限由命令管理器统一提供。 */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("clue")
                .then(Commands.literal("info").executes(Command_Clue::info))
                .then(Commands.literal("audit").executes(Command_Clue::audit))
                .then(Commands.literal("reload").executes(Command_Clue::reload))
                // 管理员手动补页：稳定 ID 与旧编号都认（旧编号靠 catalog 的映射），
                // 是给新线索（没有历史编号）做验收测试的唯一手段。
                .then(Commands.literal("grant")
                        .then(Commands.argument("线索", StringArgumentType.string())
                                .executes(ctx -> grant(ctx,
                                        StringArgumentType.getString(ctx, "线索"), null))
                                .then(Commands.argument("玩家", EntityArgument.player())
                                        .executes(ctx -> grant(ctx,
                                                StringArgumentType.getString(ctx, "线索"),
                                                EntityArgument.getPlayer(ctx, "玩家")))))));
    }

    private static int grant(CommandContext<CommandSourceStack> context, String key,
                             ServerPlayer target) {
        ServerPlayer resolved = target;
        if (resolved == null) {
            try {
                resolved = context.getSource().getPlayerOrException();
            } catch (Exception exception) {
                context.getSource().sendFailure(Component.literal("请指定玩家，或以玩家身份执行"));
                return 0;
            }
        }
        String clueId = ClueCatalog.byId(key) != null
                ? key
                : ClueCatalog.idForLegacy(parseLegacyId(key));
        if (clueId == null) {
            context.getSource().sendFailure(Component.literal(
                    "找不到线索：§f" + key + "§c（可用稳定 ID 或旧编号，用 /dreamingfish clue info 看目录规模）"));
            return 0;
        }
        ServerPlayer player = resolved;
        ClueGrantService.Outcome outcome = ClueGrantService.grant(player, clueId);
        context.getSource().sendSuccess(() -> Component.literal(
                "发放 " + clueId + " → " + player.getScoreboardName() + " 结果：" + outcome), true);
        return outcome == ClueGrantService.Outcome.GRANTED ? 1 : 0;
    }

    /** 旧编号解析：不是数字就返回 0（catalog 里必定查不到）。 */
    private static int parseLegacyId(String key) {
        try {
            return Integer.parseInt(key.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(summary("线索目录")), false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        ClueCatalog.load();
        if (ClueCatalog.isReadOnly()) {
            context.getSource().sendFailure(Component.literal(
                    "线索目录重载失败，本次不提供线索（原文件保留，原因见服务器日志）"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(summary("线索目录已重载")), true);
        return 1;
    }

    /**
     * 线索可达性审计：列出每条线索有没有"玩家真的走得到"的获取途径。
     *
     * <p>只靠 0.01% 随机掉落的线索实际上等于拿不到；如果其中某条是证据包的材料，
     * 那个包的论证链就断了。这条命令把"凭印象"变成"跑一遍"。</p>
     */
    private static int audit(CommandContext<CommandSourceStack> context) {
        List<ClueDefinition> all = ClueCatalog.all();
        if (all.isEmpty()) {
            context.getSource().sendFailure(Component.literal(
                    "线索目录为空（未加载或加载失败），无法审计"));
            return 0;
        }

        List<String> guaranteed = ClueGuaranteeService.guaranteedClueIds();
        List<String> declared = new ArrayList<>();
        List<String> onlyDrop = new ArrayList<>();
        List<String> brokenDeclaration = new ArrayList<>();
        for (ClueDefinition definition : all) {
            String clueId = definition.id();
            List<String> sources = ClueCatalog.grantSourcesOf(clueId);
            // 与索引构建共用同一套判据，避免"审计说有入口、实际却没进索引"。
            boolean valid = ClueGrantSource.hasValidDeclaration(sources);
            boolean broken = !valid && !sources.isEmpty();
            if (broken) {
                brokenDeclaration.add(clueId);
            }
            if (valid) {
                declared.add(clueId);
            }
            if (!valid && !guaranteed.contains(clueId)) {
                onlyDrop.add(clueId);
            }
        }

        StringBuilder report = new StringBuilder("线索可达性审计（" + all.size() + " 条）");
        report.append("\n  声明入口 ").append(declared.size()).append(" 条：").append(shortIds(declared));
        report.append("\n  保底通道 ").append(guaranteed.size()).append(" 条：").append(shortIds(guaranteed));
        report.append("\n  仅随机掉落 ").append(onlyDrop.size()).append(" 条：").append(shortIds(onlyDrop));
        if (!brokenDeclaration.isEmpty()) {
            report.append("\n  §c声明解析失败 ").append(brokenDeclaration.size())
                    .append(" 条（不会生效）：").append(shortIds(brokenDeclaration));
        }
        report.append(packSummary(guaranteed));

        String text = report.toString();
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** 证据包视角：每个包里有多少材料确实有确定入口。 */
    private static String packSummary(List<String> guaranteed) {
        Map<String, List<String>> packs = new LinkedHashMap<>();
        for (ClueSecrets secrets : ClueCatalog.allSecrets()) {
            if (!secrets.evidencePackId().isBlank()) {
                packs.computeIfAbsent(secrets.evidencePackId(), ignored -> new ArrayList<>())
                        .add(secrets.id());
            }
        }
        if (packs.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : packs.entrySet()) {
            List<String> missing = new ArrayList<>();
            int reachable = 0;
            for (String clueId : entry.getValue()) {
                if (isReachable(clueId, guaranteed)) {
                    reachable++;
                } else {
                    missing.add(clueId);
                }
            }
            builder.append("\n  证据包 ").append(entry.getKey()).append("：")
                    .append(entry.getValue().size()).append(" 条材料，其中 ")
                    .append(reachable).append(" 条可达");
            builder.append(missing.isEmpty()
                    ? " §a（完整）"
                    : " §c（缺口：" + shortIds(missing) + "，只能靠掉落）");
        }
        return builder.toString();
    }

    /** 有确定入口 = 有能解析通过的声明，或者落在保底通道里。 */
    private static boolean isReachable(String clueId, List<String> guaranteed) {
        return guaranteed.contains(clueId)
                || ClueGrantSource.hasValidDeclaration(ClueCatalog.grantSourcesOf(clueId));
    }

    /** 优先用旧短号显示（更短、服主更好认）；没有旧编号就退回稳定 ID。 */
    private static String shortIds(List<String> clueIds) {
        if (clueIds.isEmpty()) {
            return "（无）";
        }
        List<String> labels = new ArrayList<>();
        for (String clueId : clueIds) {
            ClueDefinition definition = ClueCatalog.byId(clueId);
            labels.add(definition != null && definition.legacyId() > 0
                    ? String.format("%02d", definition.legacyId())
                    : clueId);
        }
        return String.join(" ", labels);
    }

    private static String summary(String prefix) {
        return prefix + "：" + ClueCatalog.count() + " 条；私密定义 "
                + ClueCatalog.allSecrets().size() + " 条；发放入口 "
                + ClueCatalog.grantSourceCount() + " 个"
                + (ClueCatalog.isReadOnly() ? "（只读保护中：上次加载失败）" : "");
    }
}
