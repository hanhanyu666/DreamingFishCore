package com.hhy.dreamingfishcore.gameplay.task_location_system;

import com.mojang.brigadier.ParseResults;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.event.CommandEvent;

/**
 * Boundary and destructive-action policy for task locations.
 *
 * <p>EconomySystem remains the owner of private territory data and permission checks. This class
 * only gates a claim when it touches a story location: claims wholly outside story locations pass
 * through unchanged, claims touching a buildable location are allowed with a reminder, and any
 * claim touching a protected location is rejected. BUILDABLE locations keep normal block
 * interaction and only apply the configured hazards (TNT, lava, and conditional Nether-portal
 * ignition); PROTECTED locations are still handled by the Adventure/scene protection layer.</p>
 */
public final class BuildableTerritoryPolicy {
    private static final String ECONOMY_NAMESPACE = "economy_system";
    private static final ResourceLocation CLAIM_WAND_ID =
            ResourceLocation.fromNamespaceAndPath(ECONOMY_NAMESPACE, "claim_wand");
    private static final long SELECTION_TIMEOUT_TICKS = 20L * 60L;

    /** Vanilla TNT items blocked in every active task location. */
    private static final Set<ResourceLocation> TNT_ITEM_IDS = Set.of(
            ResourceLocation.withDefaultNamespace("tnt"),
            ResourceLocation.withDefaultNamespace("tnt_minecart"));
    private static final ResourceLocation LAVA_BUCKET_ID =
            ResourceLocation.withDefaultNamespace("lava_bucket");

    private static final Map<MinecraftServer, Map<UUID, SelectionGuard>> SELECTIONS =
            new WeakHashMap<>();

    private enum ClaimScope {
        OUTSIDE_STORY,
        BUILDABLE_STORY,
        BLOCKED_STORY
    }

    private record ClaimClassification(ClaimScope scope, String locationId) {
    }

    private BuildableTerritoryPolicy() {
    }

    public static boolean isClaimWand(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return CLAIM_WAND_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Returns true only for the vanilla flint-and-steel item. */
    public static boolean isFlintAndSteel(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.FLINT_AND_STEEL);
    }

    /** Returns true for a vanilla or conventionally-named modded TNT item. */
    public static boolean isTntTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (TNT_ITEM_IDS.contains(id)) {
            return true;
        }
        String path = id == null ? "" : id.getPath().toLowerCase(Locale.ROOT);
        return path.contains("tnt");
    }

    /**
     * Compatibility name retained for integrations compiled against the first policy version.
     * Fire, crystals and other explosives are no longer blocked by task locations.
     */
    public static boolean isExplosiveOrFireTool(ItemStack stack) {
        return isTntTool(stack);
    }

    /** Lava buckets are allowed nowhere in an active story location. */
    public static boolean isLavaBucket(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return LAVA_BUCKET_ID.equals(id)
                || (id != null && id.getPath().equals("lava_bucket"));
    }

    /** Compatibility helper for callers that used the previous broad hazard predicate. */
    public static boolean isDestructiveTool(ItemStack stack) {
        return isExplosiveOrFireTool(stack) || isLavaBucket(stack);
    }

    /** Blocks TNT block forms even when a mod bypasses the normal item-use event. */
    public static boolean isTntBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) {
            return false;
        }
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.contains("tnt");
    }

    /** Compatibility name retained for the old broad hazard predicate. */
    public static boolean isExplosiveOrFireBlock(BlockState state) {
        return isTntBlock(state);
    }

    /** Legacy helper retained for integrations; task locations no longer use a broad fire rule. */
    public static boolean isFireBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) {
            return false;
        }
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.equals("fire") || path.equals("soul_fire")
                || path.equals("campfire") || path.equals("soul_campfire")
                || path.contains("fire");
    }

    /**
     * Returns whether an explosion was created by a TNT entity. Creepers, ghasts, beds, crystals
     * and other non-TNT explosions remain ordinary world behavior.
     */
    public static boolean isTntExplosion(Explosion explosion) {
        if (explosion == null) {
            return false;
        }
        Entity source = explosion.getDirectSourceEntity();
        if (isTntEntity(source)) {
            return true;
        }
        return isTntEntity(explosion.getIndirectSourceEntity());
    }

    private static boolean isTntEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && id.getPath().toLowerCase(Locale.ROOT).contains("tnt");
    }

    /** Returns true for both source and flowing lava block states. */
    public static boolean isLavaBlock(BlockState state) {
        return state != null && isLavaFluid(state.getFluidState());
    }

    public static boolean isLavaFluid(FluidState state) {
        return state != null && state.is(FluidTags.LAVA);
    }

    /** Compatibility helper for callers that used the previous broad block predicate. */
    public static boolean isDestructiveBlock(BlockState state) {
        return isExplosiveOrFireBlock(state) || isLavaBlock(state);
    }

    /**
     * Handles a claim-wand click. Returning {@code false} means the caller must cancel the
     * interaction event. Non-claim-wand interactions always pass through.
     */
    public static boolean allowClaimWandClick(
            ServerPlayer player, InteractionHand hand, BlockPos position) {
        if (player == null || position == null || !isClaimWand(player.getItemInHand(hand))) {
            return true;
        }
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }

        String dimensionId = player.serverLevel().dimension().location().toString();
        long tick = server.overworld().getGameTime();
        synchronized (SELECTIONS) {
            Map<UUID, SelectionGuard> byPlayer = state(server);
            SelectionGuard previous = byPlayer.get(player.getUUID());
            if (previous != null && previous.expired(tick)) {
                byPlayer.remove(player.getUUID());
                previous = null;
            }

            if (previous != null && previous.complete()) {
                // EconomySystem treats the third right-click as cancellation. Drop the mirror
                // state too, so the next click starts a fresh selection in both systems. This is
                // intentionally allowed even when the third click is outside a story location:
                // it only cancels the pending EconomySystem selection.
                byPlayer.remove(player.getUUID());
                return true;
            }

            if (previous == null) {
                TaskLocationDefinition clickedLocation = TaskLocationManager.findLocationAt(
                        player.serverLevel(), position).orElse(null);
                if (clickedLocation != null && !clickedLocation.isBuildable()) {
                    deny(player, "message.dreamingfishcore.territory.claim_protected");
                    return false;
                }
                ClaimScope scope = clickedLocation == null
                        ? ClaimScope.OUTSIDE_STORY : ClaimScope.BUILDABLE_STORY;
                if (scope == ClaimScope.BUILDABLE_STORY) {
                    remindBuildable(player);
                }
                byPlayer.put(player.getUUID(), new SelectionGuard(
                        dimensionId, position.immutable(), null,
                        clickedLocation == null ? null : clickedLocation.getId(),
                        scope, tick));
                return true;
            }

            // EconomySystem clears its first point on a dimension/Y mismatch. Let that event
            // through and discard our mirror so the following click starts afresh in both systems.
            if (!previous.dimensionId().equals(dimensionId)
                    || previous.first().getY() != position.getY()) {
                byPlayer.remove(player.getUUID());
                return true;
            }

            ClaimClassification classification = classifyClaim(
                    player.serverLevel(), previous.first(), position);
            if (classification.scope() == ClaimScope.BUILDABLE_STORY
                    && previous.scope() != ClaimScope.BUILDABLE_STORY) {
                remindBuildable(player);
            }
            if (classification.scope() == ClaimScope.BLOCKED_STORY) {
                // Let EconomySystem finish its own selection state; the confirmation gate below
                // rejects the claim. This avoids leaving EconomySystem with a stale first point
                // when a player clicks a forbidden second point.
                deny(player, "message.dreamingfishcore.territory.claim_story_boundary");
            }
            byPlayer.put(player.getUUID(),
                    new SelectionGuard(dimensionId, previous.first(), position.immutable(),
                            classification.locationId(), classification.scope(), tick));
            return true;
        }
    }

    /**
     * Rejects EconomySystem's confirmation command only when a tracked selection touches a story
     * location illegally. An untracked/outside-world selection is deliberately passed through so
     * EconomySystem keeps its normal free-world claiming behavior.
     */
    public static void onCommand(CommandEvent event) {
        if (event == null) {
            return;
        }
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        if (parse == null || !(parse.getContext().getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String input = parse.getReader().getString().trim();
        if (input.startsWith("/")) {
            input = input.substring(1).trim();
        }
        int separator = input.indexOf(' ');
        String root = (separator < 0 ? input : input.substring(0, separator))
                .toLowerCase(Locale.ROOT);
        if (!isConfirmationRoot(root)) {
            return;
        }

        SelectionGuard guard = getSelection(player);
        if (guard == null || !guard.complete()) {
            // Outside-story claims (and incomplete selections in any area) should retain
            // EconomySystem's own validation/messages. A completed forbidden selection is the
            // only case that must be stopped here.
            return;
        }
        if (!isAllowedCompletedSelection(player, guard)) {
            deny(player, "message.dreamingfishcore.territory.claim_story_boundary");
            event.setCanceled(true);
            return;
        }
        clear(player);
    }

    public static void clear(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        synchronized (SELECTIONS) {
            Map<UUID, SelectionGuard> byPlayer = SELECTIONS.get(player.getServer());
            if (byPlayer != null) {
                byPlayer.remove(player.getUUID());
                if (byPlayer.isEmpty()) {
                    SELECTIONS.remove(player.getServer());
                }
            }
        }
    }

    public static void clear(MinecraftServer server) {
        if (server == null) {
            return;
        }
        synchronized (SELECTIONS) {
            SELECTIONS.remove(server);
        }
    }

    public static void clearAll() {
        synchronized (SELECTIONS) {
            SELECTIONS.clear();
        }
    }

    private static SelectionGuard getSelection(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }
        long tick = server.overworld().getGameTime();
        synchronized (SELECTIONS) {
            Map<UUID, SelectionGuard> byPlayer = SELECTIONS.get(server);
            SelectionGuard guard = byPlayer == null ? null : byPlayer.get(player.getUUID());
            if (guard != null && guard.expired(tick)) {
                byPlayer.remove(player.getUUID());
                return null;
            }
            return guard;
        }
    }

    private static boolean isAllowedCompletedSelection(ServerPlayer player, SelectionGuard guard) {
        if (guard.scope() == ClaimScope.BLOCKED_STORY
                || !guard.dimensionId().equals(player.serverLevel().dimension().location().toString())) {
            return false;
        }
        ClaimClassification current = classifyClaim(
                player.serverLevel(), guard.first(), guard.second());
        return current.scope() == guard.scope();
    }

    private static ClaimClassification classifyClaim(
            net.minecraft.world.level.Level level, BlockPos first, BlockPos second) {
        // Protected locations have precedence: a rectangle touching both modes must not be able
        // to use the buildable reminder as an escape hatch.
        return TaskLocationManager.findProtectedLocationIntersectingClaim(level, first, second)
                .map(location -> new ClaimClassification(ClaimScope.BLOCKED_STORY, location.getId()))
                .orElseGet(() -> TaskLocationManager.findBuildableLocationIntersectingClaim(
                                level, first, second)
                        .map(location -> new ClaimClassification(ClaimScope.BUILDABLE_STORY, location.getId()))
                        .orElse(new ClaimClassification(ClaimScope.OUTSIDE_STORY, null)));
    }

    private static boolean isConfirmationRoot(String root) {
        return root.equals("confirm_claim")
                || root.equals("confirm_modify")
                || root.equals(ECONOMY_NAMESPACE + ":confirm_claim")
                || root.equals(ECONOMY_NAMESPACE + ":confirm_modify");
    }

    private static Map<UUID, SelectionGuard> state(MinecraftServer server) {
        return SELECTIONS.computeIfAbsent(server, ignored -> new HashMap<>());
    }

    private static void deny(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key));
    }

    private static void remindBuildable(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable(
                "message.dreamingfishcore.territory.claim_buildable_reminder"));
    }

    private record SelectionGuard(
            String dimensionId,
            BlockPos first,
            BlockPos second,
            String locationId,
            ClaimScope scope,
            long lastTick) {
        private boolean complete() {
            return second != null;
        }

        private boolean expired(long tick) {
            return tick - lastTick > SELECTION_TIMEOUT_TICKS;
        }
    }
}
