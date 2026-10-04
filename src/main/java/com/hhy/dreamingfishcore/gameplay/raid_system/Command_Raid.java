package com.hhy.dreamingfishcore.gameplay.raid_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.hhy.dreamingfishcore.gameplay.raid_system.extraction.ExtractionService;
import com.hhy.dreamingfishcore.gameplay.raid_system.loot.RaidLootApplier;
import com.hhy.dreamingfishcore.gameplay.raid_system.loot.RaidLootService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /dreamingfish raid ...}：对局的开局、查询与收尾。
 *
 * <p>第 1 步只涉及"对局身份"：开新局、看当前局、预测种子、结束对局。
 * 具体生成内容（撤离点、资源点…）由后续步骤接在这里。</p>
 */
public final class Command_Raid {

    private Command_Raid() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("raid")
                .then(Commands.literal("info")
                        .executes(Command_Raid::info))
                .then(Commands.literal("history")
                        .executes(Command_Raid::history))
                .then(Commands.literal("new")
                        .then(Commands.argument("map", StringArgumentType.word())
                                .executes(context -> newRaid(context, 0, 1))
                                .then(Commands.argument("difficulty", IntegerArgumentType.integer(0, 10))
                                        .executes(context -> newRaid(context,
                                                IntegerArgumentType.getInteger(context, "difficulty"), 1))
                                        .then(Commands.argument("players",
                                                        IntegerArgumentType.integer(1, 100))
                                                .executes(context -> newRaid(context,
                                                        IntegerArgumentType.getInteger(context, "difficulty"),
                                                        IntegerArgumentType.getInteger(context, "players")))))))
                .then(Commands.literal("seed")
                        .then(Commands.argument("map", StringArgumentType.word())
                                .executes(context -> seed(context, -1L))
                                .then(Commands.argument("raid_id",
                                                com.mojang.brigadier.arguments.LongArgumentType.longArg(1L))
                                        .executes(context -> seed(context,
                                                com.mojang.brigadier.arguments.LongArgumentType
                                                        .getLong(context, "raid_id"))))))
                .then(Commands.literal("extractions")
                        .executes(Command_Raid::extractions)
                        .then(Commands.literal("reload")
                                .executes(Command_Raid::extractionsReload))
                        .then(Commands.literal("overview")
                                .executes(Command_Raid::extractionsOverview)))
                .then(Commands.literal("plan")
                        .executes(Command_Raid::plan))
                .then(Commands.literal("loot")
                        .then(Commands.literal("reload")
                                .executes(Command_Raid::lootReload))
                        .then(Commands.literal("overview")
                                .executes(Command_Raid::lootOverview)))
                .then(Commands.literal("end")
                        .executes(Command_Raid::end)));
    }

    private static void reply(CommandSourceStack source, List<String> lines) {
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<String> messages = new ArrayList<>();
        RaidService.ensureLoaded(source.getServer());
        RaidService.current().ifPresentOrElse(
                manifest -> messages.addAll(manifest.describe()),
                () -> messages.add("当前没有进行中的对局（用 /dreamingfish raid new <地图> 开一局）"));
        reply(source, messages);
        return 1;
    }

    private static int history(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        RaidService.ensureLoaded(source.getServer());
        List<RaidManifest> raids = RaidService.history();
        if (raids.isEmpty()) {
            source.sendSuccess(() -> Component.literal("还没有历史对局"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("历史对局 " + raids.size() + " 条（最近 "
                + Math.min(10, raids.size()) + " 条）："), false);
        for (int index = raids.size() - 1; index >= 0 && index >= raids.size() - 10; index--) {
            RaidManifest manifest = raids.get(index);
            source.sendSuccess(() -> Component.literal("  #" + manifest.raidId() + "  地图 " + manifest.mapId()
                    + "  种子 " + manifest.raidSeed() + "  人数 " + manifest.playerCount()), false);
        }
        return raids.size();
    }

    private static int newRaid(CommandContext<CommandSourceStack> context, int difficulty, int players) {
        CommandSourceStack source = context.getSource();
        String map = StringArgumentType.getString(context, "map");
        List<String> messages = new ArrayList<>(
                RaidService.startNewRaid(source.getServer(), map, difficulty, players));
        // 顺序照设计稿：先定撤离点（玩家要有路可走），再生成并落地战利品
        messages.addAll(ExtractionService.selectAndRecord(source.getServer()));
        // 服主要求：开新局就把计划填进世界（填充过程只在空容器里放，且逐个记账以便结束时清回）
        messages.addAll(RaidLootService.generatePlan(source.getServer()));
        RaidService.current().ifPresent(manifest -> messages.addAll(
                RaidLootApplier.apply(source.getServer(), manifest, RaidLootService.plan(), true)));
        reply(source, messages);
        return 1;
    }

    private static int seed(CommandContext<CommandSourceStack> context, long raidId) {
        CommandSourceStack source = context.getSource();
        String map = StringArgumentType.getString(context, "map");
        long targetRaid = raidId > 0L ? raidId : RaidService.current()
                .map(RaidManifest::raidId).orElse(1L);
        long predicted = RaidService.predictSeed(source.getServer(), map, targetRaid);
        source.sendSuccess(() -> Component.literal("地图 " + map + " 第 " + targetRaid + " 局的主种子："
                + predicted + "（0x" + Long.toHexString(predicted) + "）"), false);
        source.sendSuccess(() -> Component.literal("子系统种子示例：loot="
                + new RaidRandom(predicted).forSystem("loot").peek(1)[0]
                + "  mobs=" + new RaidRandom(predicted).forSystem("mobs").peek(1)[0]), false);
        DreamingFishCore.LOGGER.info("[raid] 预测种子：地图 {} 第 {} 局 -> {}", map, targetRaid, predicted);
        return 1;
    }

    private static int end(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<String> messages = new ArrayList<>();
        RaidService.ensureLoaded(source.getServer());
        // 服主要求：结束对局时把本局填进去的物品清掉（内容被动过的容器一律跳过）
        RaidService.current().ifPresent(manifest ->
                messages.addAll(RaidLootApplier.clear(source.getServer(), manifest.raidId())));
        messages.addAll(RaidService.endRaid(source.getServer()));
        reply(source, messages);
        return 1;
    }

    /** 用当前对局与本局锚点算出各区域的战利品计划（第 10~14 步）。 */
    private static int plan(CommandContext<CommandSourceStack> context) {
        reply(context.getSource(), RaidLootService.generatePlan(context.getSource().getServer()));
        return 1;
    }

    /** 重新选一次撤离点并记进对局记录（换了配置或想重抽时用）。 */
    private static int extractions(CommandContext<CommandSourceStack> context) {
        reply(context.getSource(), ExtractionService.selectAndRecord(context.getSource().getServer()));
        return 1;
    }

    private static int extractionsReload(CommandContext<CommandSourceStack> context) {
        reply(context.getSource(), ExtractionService.reload(context.getSource().getServer()));
        return 1;
    }

    private static int extractionsOverview(CommandContext<CommandSourceStack> context) {
        ExtractionService.ensureLoaded(context.getSource().getServer());
        reply(context.getSource(), ExtractionService.overview());
        return 1;
    }

    private static int lootReload(CommandContext<CommandSourceStack> context) {
        reply(context.getSource(), RaidLootService.reload(context.getSource().getServer()));
        return 1;
    }

    private static int lootOverview(CommandContext<CommandSourceStack> context) {
        RaidLootService.ensureLoaded(context.getSource().getServer());
        reply(context.getSource(), RaidLootService.overview());
        return 1;
    }
}
