package com.hhy.dreamingfishcore.gameplay.task_location_system;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class TaskLocationDefinitionTest {
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));

    @Test
    void legacyConstructorKeepsProtectedMode() {
        TaskLocationDefinition location = new TaskLocationDefinition(
                "dreamingfishcore:legacy", "旧场景", OVERWORLD,
                new BlockPos(0, 0, 0), new BlockPos(10, 100, 10));

        assertTrue(location.forcesAdventure());
        assertTrue(location.protectsBlocks());
        assertFalse(location.isBuildable());
    }

    @Test
    void strictContainmentHelperStillRequiresBuildableClaimInsideTheSameCuboid() {
        TaskLocationDefinition location = new TaskLocationDefinition(
                "dreamingfishcore:settlement", "聚居地", OVERWORLD,
                new BlockPos(-10, 0, -10), new BlockPos(10, 100, 10),
                TaskLocationMode.BUILDABLE);

        assertTrue(location.isBuildable());
        assertFalse(location.forcesAdventure());
        assertFalse(location.protectsBlocks());
        assertTrue(location.protectsEntities());
        assertTrue(location.containsClaim(
                OVERWORLD, new BlockPos(-8, 64, -8), new BlockPos(8, 64, 8)));
        assertTrue(location.containsClaim(
                OVERWORLD, new BlockPos(-8, 200, -8), new BlockPos(8, 200, 8)));
        assertFalse(location.containsClaim(
                OVERWORLD, new BlockPos(-8, 64, -8), new BlockPos(11, 64, 8)));
        assertFalse(location.containsClaim(
                OVERWORLD, new BlockPos(-8, 64, -8), new BlockPos(8, 65, 8)));
        assertFalse(location.containsClaim(
                ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.withDefaultNamespace("the_nether")),
                new BlockPos(-8, 64, -8), new BlockPos(8, 64, 8)));
    }

    @Test
    void storyIntersectionAllowsBuildableCrossingButCanDetectProtectedCoverage() {
        TaskLocationDefinition buildable = new TaskLocationDefinition(
                "dreamingfishcore:settlement", "聚居地", OVERWORLD,
                new BlockPos(0, 0, 0), new BlockPos(10, 100, 10),
                TaskLocationMode.BUILDABLE);
        TaskLocationDefinition protectedLocation = new TaskLocationDefinition(
                "dreamingfishcore:scene", "剧情场景", OVERWORLD,
                new BlockPos(20, 0, 20), new BlockPos(30, 100, 30),
                TaskLocationMode.PROTECTED);

        assertTrue(buildable.intersectsClaim(
                OVERWORLD, new BlockPos(-10, 64, 5), new BlockPos(5, 64, 5)));
        assertTrue(protectedLocation.intersectsClaim(
                OVERWORLD, new BlockPos(10, 64, 25), new BlockPos(25, 64, 25)));
        assertTrue(protectedLocation.intersectsClaim(
                OVERWORLD, new BlockPos(10, -60, 25), new BlockPos(25, -60, 25)));
        assertFalse(protectedLocation.intersectsClaim(
                OVERWORLD, new BlockPos(-10, 64, -10), new BlockPos(10, 64, 10)));
    }

    @Test
    void modeParserAcceptsCaseInsensitiveConfigAndRejectsUnknownValues() {
        assertTrue(TaskLocationMode.parse("buildable") == TaskLocationMode.BUILDABLE);
        assertTrue(TaskLocationMode.parse(" PROTECTED ") == TaskLocationMode.PROTECTED);
        assertThrows(IllegalStateException.class, () -> TaskLocationMode.parse("arena"));
    }
}
