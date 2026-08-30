package com.hhy.dreamingfishcore.gameplay.zombie_system;

import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombieTaskLocationRulesTest {
    @Test
    void destructiveProtectionCoversActorAndAffectedBlock() {
        assertFalse(ZombieTaskLocationRules.shouldBlockDestructiveAction(
                false, true, true));
        assertFalse(ZombieTaskLocationRules.shouldBlockDestructiveAction(
                true, false, false));
        assertTrue(ZombieTaskLocationRules.shouldBlockDestructiveAction(
                true, true, false));
        assertTrue(ZombieTaskLocationRules.shouldBlockDestructiveAction(
                true, false, true));
    }

    @Test
    void regenerationRequiresEnabledRuleAndPlayerInsideLocation() {
        assertFalse(ZombieTaskLocationRules.shouldApplyRegeneration(
                false, true, true));
        assertFalse(ZombieTaskLocationRules.shouldApplyRegeneration(
                true, false, true));
        assertFalse(ZombieTaskLocationRules.shouldApplyRegeneration(
                true, true, false));
        assertTrue(ZombieTaskLocationRules.shouldApplyRegeneration(
                true, true, true));
    }

    @Test
    void spawnProtectionOnlyBlocksMonstersInsideForAutomaticSources() {
        assertFalse(ZombieTaskLocationRules.shouldBlockMonsterSpawn(true, false, true));
        assertFalse(ZombieTaskLocationRules.shouldBlockMonsterSpawn(true, true, false));
        assertFalse(ZombieTaskLocationRules.shouldBlockMonsterSpawn(false, true, true));
        assertTrue(ZombieTaskLocationRules.shouldBlockMonsterSpawn(true, true, true));

        assertTrue(ZombieTaskLocationRules.isAutomaticSpawnType(MobSpawnType.NATURAL));
        assertTrue(ZombieTaskLocationRules.isAutomaticSpawnType(MobSpawnType.SPAWNER));
        assertTrue(ZombieTaskLocationRules.isAutomaticSpawnType(MobSpawnType.REINFORCEMENT));
        assertFalse(ZombieTaskLocationRules.isAutomaticSpawnType(MobSpawnType.SPAWN_EGG));
        assertFalse(ZombieTaskLocationRules.isAutomaticSpawnType(MobSpawnType.COMMAND));
        assertFalse(ZombieTaskLocationRules.isAutomaticSpawnType(MobSpawnType.MOB_SUMMONED));
        assertFalse(ZombieTaskLocationRules.isAutomaticSpawnType(null));
    }
}
