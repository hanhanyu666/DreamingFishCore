package com.hhy.dreamingfishcore.gameplay.raid_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.hhy.dreamingfishcore.gameplay.raid_system.extraction.ExtractionService;
import com.hhy.dreamingfishcore.gameplay.raid_system.loot.RaidLootApplier;
import com.hhy.dreamingfishcore.gameplay.raid_system.loot.RaidLootService;
import com.hhy.dreamingfishcore.gameplay.raid_system.map.RaidRegionService;
import com.hhy.dreamingfishcore.gameplay.raid_system.map.RaidVariantBlocks;
import com.hhy.dreamingfishcore.gameplay.raid_system.map.RaidVariantService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
                .then(Commands.literal("clear")
                        .executes(Command_Raid::clearApplied))
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
                .then(Commands.literal("arena")
                        .then(Commands.literal("status")
                                .executes(Command_Raid::arenaStatus))
                        .then(Commands.literal("back")
                                .executes(context -> arenaBack(context, false))
                                .then(Commands.literal("all")
                                        .executes(context -> arenaBack(context, true)))))
                .then(Commands.literal("region")
                        .then(Commands.literal("list")
                                .executes(Command_Raid::regionList))
                        .then(Commands.literal("capture")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                        .executes(Command_Raid::regionCapture)))))
                        .then(Commands.literal("place")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(Command_Raid::regionPlace))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(Command_Raid::regionReset)))))
                .then(Commands.literal("variants")
                        .executes(Command_Raid::variants)
                        .then(Commands.literal("reload")
                                .executes(Command_Raid::variantsReload))
                        .then(Commands.literal("overview")
                                .executes(Command_Raid::variantsOverview))
                        .then(Commands.literal("apply")
                                .executes(context -> variantsApply(context, null))
                                .then(Commands.argument("variant", StringArgumentType.string())
                                        .executes(context -> variantsApply(context,
                                                StringArgumentType.getString(context, "variant")))))
                        .then(Commands.literal("restore")
                                .executes(Command_Raid::variantsRestore)))
                .then(Commands.literal("variant_block")
                        .then(Commands.literal("list")
                                .executes(Command_Raid::variantBlockList))
                        .then(Commands.argument("variant", StringArgumentType.string())
                                .then(Commands.argument("block", StringArgumentType.greedyString())
                                        .executes(Command_Raid::variantBlockRecord))))
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
        List<String> messages = new ArrayList<>();
        // 开新局前先把上一局的战利品还回去：服主常常只是连续开新局而不结束上一局，
        // 不这么做的话箱子里的旧战利品会一直留着（而且会让新一局"箱子非空"）
        RaidService.ensureLoaded(source.getServer());
        RaidService.current().ifPresent(previous -> {
            messages.add("先回滚上一局 #" + previous.raidId() + "：");
            messages.addAll(RaidLootApplier.clear(source.getServer(), previous.raidId()));
        });
        messages.addAll(RaidService.startNewRaid(source.getServer(), map, difficulty, players));
        // 顺序照设计稿：先定地图变体（可能封路），再做撤离点，最后生成并落地战利品
        messages.addAll(RaidVariantService.selectAndRecord(source.getServer()));
        // 顺序照设计稿：先定撤离点（玩家要有路可走），再生成并落地战利品
        messages.addAll(ExtractionService.selectAndRecord(source.getServer()));
        // 开局自动化：把时间拨到本局起始点，并把参与者送进竞技场
        messages.addAll(com.hhy.dreamingfishcore.gameplay.raid_system.map.RaidArenaService
                .onRaidStart(source.getServer()));
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

    /** 看竞技场维度是否加载、进场落点、当前时间与三个开关。 */
    private static int arenaStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        reply(source, com.hhy.dreamingfishcore.gameplay.raid_system.map.RaidArenaService
                .status(source.getServer()));
        return 1;
    }

    /**
     * 回家路：把在竞技场里的玩家送回出口（**不需要进行中的对局**）。
     *
     * @param all true = 所有还在竞技场的人；false = 只送执行者自己
     */
    private static int arenaBack(CommandContext<CommandSourceStack> context, boolean all) {
        CommandSourceStack source = context.getSource();
        reply(source, com.hhy.dreamingfishcore.gameplay.raid_system.map.RaidArenaService
                .sendHome(source.getServer(), all ? null : source.getPlayer()));
        return 1;
    }

    /**
     * 抓取一片区域存成"地图零件"。
     *
     * <p>**只抓方块、不抓实体**：抓实体的话每次放置都会把箱子里的怪、掉落物一起复制一遍，
     * 这在"每局重建竞技场"的用法下是灾难。以后真要抓实体就单开一个命令。</p>
     */
    private static int regionCapture(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        BlockPos from = BlockPosArgument.getBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getBlockPos(context, "to");
        reply(source, RaidRegionService.capture(source.getServer(), source.getLevel(), name, from, to, false));
        return 1;
    }

    private static int regionPlace(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        reply(source, RaidRegionService.place(source.getServer(), source.getLevel(), name, pos, false));
        return 1;
    }

    private static int regionReset(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        reply(source, RaidRegionService.reset(source.getServer(), source.getLevel(), name, pos, false));
        return 1;
    }

    private static int regionList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        reply(source, RaidRegionService.list(source.getServer(), source.getLevel()));
        return 1;
    }

    /** 为当前对局抽地图变体并写进对局记录（含连通性校验，抽不合格会退回无变体）。 */
    private static int variants(CommandContext<CommandSourceStack> context) {
        reply(context.getSource(), RaidVariantService.selectAndRecord(context.getSource().getServer()));
        return 1;
    }

    private static int variantsReload(CommandContext<CommandSourceStack> context) {
        reply(context.getSource(), RaidVariantService.reload(context.getSource().getServer()));
        return 1;
    }

    private static int variantsOverview(CommandContext<CommandSourceStack> context) {
        RaidVariantService.ensureLoaded(context.getSource().getServer());
        reply(context.getSource(), RaidVariantService.overview());
        return 1;
    }

    /** 应用变体方块：不给参数就用本局选中的变体，给了就只应用那一个变体。 */
    private static int variantsApply(CommandContext<CommandSourceStack> context, String variantId) {
        reply(context.getSource(), RaidVariantBlocks.apply(context.getSource().getServer(), variantId));
        return 1;
    }

    /** 把变体方块还原成登记时的原方块（登记保留，之后还能再应用）。 */
    private static int variantsRestore(CommandContext<CommandSourceStack> context) {
        reply(context.getSource(), RaidVariantBlocks.restore(context.getSource().getServer()));
        return 1;
    }

    /** 站在目标方块前登记：变体 <variant> 生效时，这个方块变成 <block>。 */
    private static int variantBlockRecord(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String variantId = StringArgumentType.getString(context, "variant");
        String blockId = StringArgumentType.getString(context, "block");
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            source.sendFailure(Component.literal("请把准心对着要切换的方块再执行（距离 "
                    + player.blockInteractionRange() + " 格内）"));
            return 0;
        }
        String reply = RaidVariantBlocks.record(source.getServer(), player.serverLevel(),
                blockHit.getBlockPos(), variantId, blockId);
        source.sendSuccess(() -> Component.literal(reply), false);
        return 1;
    }

    private static int variantBlockList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        RaidVariantBlocks.ensureForCurrentRaid(source.getServer());
        reply(source, RaidVariantBlocks.describe());
        return 1;
    }

    /** 清理所有未清理的填充记录（含还原覆盖前的内容）。 */
    private static int clearApplied(CommandContext<CommandSourceStack> context) {
        reply(context.getSource(), RaidLootApplier.clearAll(context.getSource().getServer()));
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
