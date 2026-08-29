package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Vanilla's zombie attack goal with one tracking extension: it keeps
 * recalculating a path while an alert target is hidden behind a wall. The
 * navigation-ended condition is retained so the obstruction goals can still
 * take over when a real dead end is reached.
 */
public final class SiegeZombieAttackGoal extends ZombieAttackGoal {
    private static final int HIDDEN_PATH_REFRESH_TICKS = 20;

    private final SiegeZombieEntity zombie;
    private BlockPos hiddenPathAnchor;
    private int hiddenPathRefreshTicks;

    public SiegeZombieAttackGoal(SiegeZombieEntity zombie) {
        // A true value lets the inherited tick() refresh a path without LOS;
        // melee damage itself still requires LOS in MeleeAttackGoal.
        super(zombie, 1.0D, true);
        this.zombie = zombie;
    }

    @Override
    public void start() {
        super.start();
        hiddenPathAnchor = null;
        hiddenPathRefreshTicks = 0;
        zombie.setMeleeAttackGoalRunning(true);
    }

    @Override
    public void tick() {
        LivingEntity target = zombie.getTarget();
        if (target instanceof Player
                && !zombie.getSensing().hasLineOfSight(target)) {
            BlockPos anchor = zombie.getActiveAlertAnchor();
            if (anchor != null) {
                zombie.getLookControl().setLookAt(
                        anchor.getX() + 0.5D,
                        anchor.getY() + 1.0D,
                        anchor.getZ() + 0.5D,
                        30.0F,
                        30.0F);
                if (hiddenPathRefreshTicks-- <= 0
                        || hiddenPathAnchor == null
                        || !hiddenPathAnchor.equals(anchor)) {
                    hiddenPathAnchor = anchor.immutable();
                    hiddenPathRefreshTicks = HIDDEN_PATH_REFRESH_TICKS;
                    zombie.getNavigation().moveTo(
                            anchor.getX() + 0.5D,
                            anchor.getY(),
                            anchor.getZ() + 0.5D,
                            1.0D);
                }
                // A hidden target cannot be hit. More importantly, do not run
                // MeleeAttackGoal's live-entity re-path every 4-10 ticks: that
                // is what made a horde outside a 3x3 room shuffle constantly.
                zombie.setAggressive(false);
                return;
            }
        }
        hiddenPathAnchor = null;
        hiddenPathRefreshTicks = 0;
        super.tick();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = zombie.getTarget();
        if (target instanceof Player) {
            boolean visible = zombie.getSensing().hasLineOfSight(target);
            if (!zombie.hasPursuitLock(target)
                    || (!visible
                    && zombie.distanceToSqr(target) > zombie.getAlertPursuitRange()
                    * zombie.getAlertPursuitRange())) {
                // Sight and sound feed the same pursuit lock. A sound-supplied
                // lock differs only in having a finite memory/range; while it
                // remains valid, the normal attack/path logic below is shared.
                return false;
            }
        }
        // ZombieAttackGoal's true branch checks target validity, restrictions,
        // and creative/spectator immunity. Add back the vanilla zombie's
        // navigation-ended hand-off so digging/placing/stacking can run.
        return super.canContinueToUse() && !zombie.getNavigation().isDone();
    }

    @Override
    public void stop() {
        LivingEntity target = zombie.getTarget();
        boolean hadAlertMarker = zombie.hasAlertMarker();
        boolean alreadyAlerted = zombie.hasActiveAlertTarget();
        boolean shouldKeepHiddenTarget = target instanceof Player player
                && player.isAlive()
                && !player.isSpectator()
                && !player.isCreative()
                && !zombie.getSensing().hasLineOfSight(player)
                && zombie.distanceToSqr(player) <= zombie.getAlertPursuitRange()
                * zombie.getAlertPursuitRange()
                && zombie.runtimeSettings().enabled();
        super.stop();
        hiddenPathAnchor = null;
        hiddenPathRefreshTicks = 0;
        zombie.setMeleeAttackGoalRunning(false);
        if (shouldKeepHiddenTarget
                && !hadAlertMarker
                && !alreadyAlerted
                && target instanceof Player player) {
            // MeleeAttackGoal clears its target when navigation ends. Convert
            // a hidden target into the same short-lived alert marker used by
            // sound/broadcast tracking, so the fallback gate can decide
            // whether a genuine dead end needs mining/stacking instead of
            // losing the target before that gate gets a chance to run.
            zombie.acceptTrackingTarget(
                    player,
                    zombie.runtimeSettings().alertMemoryTicks());
        }
    }
}
