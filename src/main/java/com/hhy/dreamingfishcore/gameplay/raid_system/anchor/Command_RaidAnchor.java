package com.hhy.dreamingfishcore.gameplay.raid_system.anchor;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /dreamingfish raid_anchor ...}：锚点的日常操作面。
 *
 * <p>所有写操作都走 {@link RaidAnchorService#applyOverlay}，改完立刻落盘，避免"改了但重启就丢"。</p>
 */
public final class Command_RaidAnchor {

    /** 放置时最远看多少格。 */
    private static final double REACH = 8.0D;

    private Command_RaidAnchor() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("raid_anchor")
                .then(Commands.literal("list")
                        .executes(context -> list(context, null, null))
                        .then(Commands.argument("zone", StringArgumentType.word())
                                .executes(context -> list(context,
                                        StringArgumentType.getString(context, "zone"), null))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .executes(context -> list(context,
                                                StringArgumentType.getString(context, "zone"),
                                                StringArgumentType.getString(context, "type"))))))
                .then(Commands.literal("validate")
                        .executes(Command_RaidAnchor::validate))
                .then(Commands.literal("reload")
                        .executes(Command_RaidAnchor::reload))
                .then(Commands.literal("place")
                        .then(Commands.argument("zone", StringArgumentType.word())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .executes(context -> place(context, null))
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(context -> place(context,
                                                        StringArgumentType.getString(context, "id")))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> remove(context,
                                        StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("enable")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> setEnabled(context,
                                        StringArgumentType.getString(context, "id"), true))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> setEnabled(context,
                                        StringArgumentType.getString(context, "id"), false))))
                .then(Commands.literal("export")
                        .executes(context -> export(context, null))
                        .then(Commands.argument("zone", StringArgumentType.word())
                                .executes(context -> export(context,
                                        StringArgumentType.getString(context, "zone"))))));
    }

    private static void reply(CommandSourceStack source, List<String> lines) {
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
    }

    private static int list(CommandContext<CommandSourceStack> context, String zone, String type) {
        CommandSourceStack source = context.getSource();
        RaidAnchorService.ensureLoaded(source.getServer());

        RaidAnchorType filterType = null;
        if (type != null) {
            filterType = RaidAnchorType.parse(type).orElse(null);
            if (filterType == null) {
                source.sendFailure(Component.literal("类型不合法：" + type + "（可用："
                        + String.join(", ", RaidAnchorType.names()) + "）"));
                return 0;
            }
        }

        List<RaidAnchor> found = zone == null
                ? RaidAnchorService.catalog().anchors()
                : RaidAnchorService.catalog().byZone(zone);
        final RaidAnchorType effectiveType = filterType;
        if (effectiveType != null) {
            found = found.stream().filter(anchor -> anchor.type() == effectiveType).toList();
        }
        // lambda 里要用，必须是 final
        final List<RaidAnchor> anchors = found;

        source.sendSuccess(() -> Component.literal("锚点 " + anchors.size() + " 个"
                + (zone == null ? "" : "（区域 " + zone + "）")
                + (effectiveType == null ? "" : "（类型 " + effectiveType.name() + "）")), false);
        int shown = 0;
        for (RaidAnchor anchor : anchors) {
            if (shown++ >= 40) {
                source.sendSuccess(() -> Component.literal("  …（只显示前 40 条，用区域或类型收窄）"), false);
                break;
            }
            source.sendSuccess(() -> Component.literal("  " + anchor.describe()), false);
        }
        return anchors.size();
    }

    private static int validate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        RaidAnchorService.ensureLoaded(source.getServer());
        List<String> lines = RaidAnchorService.catalog().describe();
        lines.addAll(0, RaidAnchorService.loadProblems().stream().map(text -> "加载期问题 " + text).toList());
        reply(source, lines);
        return RaidAnchorService.catalog().problems().size();
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        reply(context.getSource(), RaidAnchorService.reload(context.getSource().getServer()));
        return 1;
    }

    private static int place(CommandContext<CommandSourceStack> context, String explicitId) {
        CommandSourceStack source = context.getSource();
        String zone = StringArgumentType.getString(context, "zone");
        String rawType = StringArgumentType.getString(context, "type");

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("放置锚点需要以玩家身份执行（需要玩家位置与朝向）"));
            return 0;
        }

        RaidAnchorType type = RaidAnchorType.parse(rawType).orElse(null);
        if (type == null) {
            source.sendFailure(Component.literal("类型不合法：" + rawType));
            return 0;
        }
        if (RaidAnchorZoneLookup.INSTANCE.exists(zone) == false) {
            source.sendFailure(Component.literal("区域不存在：" + zone + "（先用任务地点命令创建）"));
            return 0;
        }
        String zoneDimension = RaidAnchorZoneLookup.dimensionOf(zone).orElse("");
        String playerDimension = player.level().dimension().location().toString();
        if (!zoneDimension.equals(playerDimension)) {
            source.sendFailure(Component.literal("维度不一致：区域在 " + zoneDimension
                    + "，你在 " + playerDimension));
            return 0;
        }

        Vec3 position = resolvePlacementPosition(player);
        if (!RaidAnchorZoneLookup.INSTANCE.contains(zone, position.x, position.y, position.z)) {
            source.sendFailure(Component.literal("当前位置不在区域 " + zone + " 内，锚点必须放在它的区域里"));
            return 0;
        }

        RaidAnchorService.ensureLoaded(source.getServer());
        String id = explicitId == null || explicitId.isBlank()
                ? suggestId(zone, type) : explicitId;
        if (RaidAnchorService.catalog().byId(id).isPresent()) {
            source.sendFailure(Component.literal("id 已存在：" + id + "（换一个 id，或先 remove）"));
            return 0;
        }
        if (RaidAnchorService.overlay().removed().contains(id)) {
            source.sendFailure(Component.literal("该 id 曾被删除，仍未真正移除（覆盖层里还留着），换个 id"));

            return 0;
        }

        RaidAnchor anchor = new RaidAnchor(id, type, zone, position.x, position.y, position.z,
                new RaidAnchor.Rotation(0.0F, snapYaw(player.getYRot()), 0.0F),
                "", List.of(), RaidAnchor.DEFAULT_WEIGHT, true,
                RaidAnchor.DEFAULT_QUALITY_MULTIPLIER, RaidAnchor.Source.OVERLAY);

        List<String> messages = RaidAnchorService.applyOverlay(source.getServer(),
                RaidAnchorService.overlay().withPatch(anchor));
        source.sendSuccess(() -> Component.literal("已放置 " + anchor.describe()), true);
        reply(source, messages);
        DreamingFishCore.LOGGER.info("[raid_anchor] {} 放置了锚点 {}", source.getTextName(), anchor.describe());
        return 1;
    }

    /** 取玩家准心所指的方块表面（没指到方块就用玩家脚下），落点贴面 0.02 格避免与方块重叠。 */
    private static Vec3 resolvePlacementPosition(ServerPlayer player) {
        HitResult hit = player.pick(REACH, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() != HitResult.Type.MISS) {
            Vec3 normal = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            return blockHit.getLocation().add(normal.scale(0.02D));
        }
        return player.position();
    }

    /** 朝向按 45 度吸附：摆桌面物品时比随手转出来的角度整齐得多。 */
    private static float snapYaw(float yaw) {
        return Math.round(yaw / 45.0F) * 45.0F;
    }

    private static String suggestId(String zone, RaidAnchorType type) {
        String prefix = zone + "_" + type.name().toLowerCase(java.util.Locale.ROOT);
        for (int index = 1; index < 1000; index++) {
            String candidate = prefix + "_" + String.format(java.util.Locale.ROOT, "%02d", index);
            if (RaidAnchorService.catalog().byId(candidate).isEmpty()
                    && !RaidAnchorService.overlay().removed().contains(candidate)) {
                return candidate;
            }
        }
        return prefix + "_" + System.currentTimeMillis();
    }

    private static int remove(CommandContext<CommandSourceStack> context, String id) {
        CommandSourceStack source = context.getSource();
        RaidAnchorService.ensureLoaded(source.getServer());
        reply(source, RaidAnchorService.applyOverlay(source.getServer(),
                RaidAnchorService.overlay().withRemoved(id)));
        source.sendSuccess(() -> Component.literal("已删除锚点 " + id), true);
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> context, String id, boolean enabled) {
        CommandSourceStack source = context.getSource();
        RaidAnchorService.ensureLoaded(source.getServer());
        if (RaidAnchorService.catalog().byId(id).isEmpty()) {
            source.sendFailure(Component.literal("找不到锚点：" + id));
            return 0;
        }
        reply(source, RaidAnchorService.applyOverlay(source.getServer(),
                RaidAnchorService.overlay().withDisabled(id, !enabled)));
        source.sendSuccess(() -> Component.literal((enabled ? "已启用 " : "已禁用 ") + id), true);
        return 1;
    }

    private static int export(CommandContext<CommandSourceStack> context, String zone) {
        CommandSourceStack source = context.getSource();
        RaidAnchorService.ensureLoaded(source.getServer());
        Path target = RaidAnchorService.overlayPath(source.getServer()).getParent()
                .resolve("raid_anchors_export.json");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, RaidAnchorService.exportJson(zone) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            source.sendFailure(Component.literal("导出失败：" + exception.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已导出（可分发，含世界层合并结果）：" + target), false);
        return 1;
    }
}
