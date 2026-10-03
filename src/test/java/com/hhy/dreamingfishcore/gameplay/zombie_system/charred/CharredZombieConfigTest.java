package com.hhy.dreamingfishcore.gameplay.zombie_system.charred;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CharredZombieConfig} 的默认值、解析与校验。
 *
 * <p>只用显式临时路径，不碰 {@code getConfigPath()}——那个走 FML 的配置目录，在纯 JUnit 里
 * 没有引导上下文。</p>
 */
class CharredZombieConfigTest {

    @Test
    void missingFileWritesDocumentedDefaults() throws IOException {
        Path path = temp.resolve("charred_zombie.json");

        CharredZombieConfig loaded = CharredZombieConfig.load(path);

        assertTrue(Files.exists(path), "缺失的配置文件应当被创建为默认文件");
        assertEquals(CharredZombieConfig.CURRENT_SCHEMA_VERSION, loaded.getSchemaVersion());
        CharredZombieConfig.Resolved resolved = loaded.resolve();
        assertTrue(resolved.enabled());
        assertEquals(22.0D, resolved.maxHealth(), 1.0E-9D);
        assertEquals(0.20D, resolved.movementSpeed(), 1.0E-9D);
        assertEquals(35.0D, resolved.followRange(), 1.0E-9D);
        // 日光自燃默认关掉，否则白天就是免费的火焰伤害。
        assertFalse(resolved.burnInDaylight());
        // 下水腐化默认保留，当作「水把它浇灭了」的收场。
        assertTrue(resolved.convertInWater());
        assertTrue(resolved.immuneFeedbackEnabled());
        // 易燃体质与易损。
        assertEquals(2.0D, resolved.fireDamageMultiplier(), 1.0E-9D);
        assertTrue(resolved.burningBoostEnabled());
        assertTrue(resolved.vulnerableEnabled());
        assertEquals(100, resolved.vulnerableDurationTicks());
        assertEquals(0, resolved.vulnerableAmplifier());
        assertEquals(0.10D, resolved.vulnerableDamageBonusPerLevel(), 1.0E-9D);
        assertTrue(resolved.vulnerableGlowing());
    }

    /** 火球的伤害类型不在原版 {@code is_fire} 标签里，默认必须显式补进「额外算作火」的名单。 */
    @Test
    void defaultExtraFireTypesCoverVanillaFireballs() throws IOException {
        CharredZombieConfig.Resolved resolved = CharredZombieConfig.load(
                temp.resolve("defaults.json")).resolve();

        assertEquals(2, resolved.extraFireDamageTypes().size());
        assertTrue(resolved.extraFireDamageTypes().contains(damageType("minecraft:fireball")));
        assertTrue(resolved.extraFireDamageTypes().contains(damageType("minecraft:unattributed_fireball")));
    }

    /** 兜底通道默认放开虚空/溺水/摔落，且解析成的是真正的伤害类型键。 */
    @Test
    void defaultFallbackTypesCoverVoidDrownAndFall() throws IOException {
        CharredZombieConfig.Resolved resolved = CharredZombieConfig.load(
                temp.resolve("fallback.json")).resolve();

        assertEquals(3, resolved.alwaysEffectiveDamageTypes().size());
        assertTrue(resolved.alwaysEffectiveDamageTypes().contains(damageType("minecraft:out_of_world")));
        assertTrue(resolved.alwaysEffectiveDamageTypes().contains(damageType("minecraft:drown")));
        assertTrue(resolved.alwaysEffectiveDamageTypes().contains(damageType("minecraft:fall")));
    }

    @Test
    void partialJsonKeepsUntouchedDefaults() throws IOException {
        Path path = temp.resolve("partial.json");
        Files.writeString(path, """
                {
                  "maxHealth": 60.0,
                  "fireDamageMultiplier": 3.0,
                  "burnInDaylight": true,
                  "extraFireDamageTypes": []
                }
                """);

        CharredZombieConfig.Resolved resolved = CharredZombieConfig.load(path).resolve();

        assertEquals(60.0D, resolved.maxHealth(), 1.0E-9D);
        assertEquals(3.0D, resolved.fireDamageMultiplier(), 1.0E-9D);
        assertTrue(resolved.burnInDaylight());
        // 空数组是合法配置：「严格只认原版 is_fire 标签」。
        assertTrue(resolved.extraFireDamageTypes().isEmpty());
        // 未出现在 JSON 里的字段继承默认值。
        assertEquals(0.20D, resolved.movementSpeed(), 1.0E-9D);
        assertEquals(100, resolved.vulnerableDurationTicks());
    }

    @Test
    void nullListsAreNormalizedToEmptyInsteadOfCrashing() throws IOException {
        Path path = temp.resolve("null_lists.json");
        Files.writeString(path, """
                {
                  "extraFireDamageTypes": null,
                  "alwaysEffectiveDamageTypes": null
                }
                """);

        CharredZombieConfig.Resolved resolved = CharredZombieConfig.load(path).resolve();

        assertTrue(resolved.extraFireDamageTypes().isEmpty());
        assertTrue(resolved.alwaysEffectiveDamageTypes().isEmpty());
    }

    @Test
    void invalidDamageTypeIdIsRejected() throws IOException {
        Path path = temp.resolve("bad_id.json");
        Files.writeString(path, """
                {
                  "extraFireDamageTypes": ["没有命名空间也没有冒号"]
                }
                """);

        assertThrows(IllegalStateException.class, () -> CharredZombieConfig.reload(path));
    }

    @Test
    void reloadRejectsOutOfRangeValueAndKeepsLastKnownGood() throws IOException {
        Path path = temp.resolve("out_of_range.json");
        Files.writeString(path, """
                {
                  "vulnerableAmplifier": 40
                }
                """);

        CharredZombieConfig.Resolved before = CharredZombieConfig.current().resolve();
        assertThrows(IllegalStateException.class, () -> CharredZombieConfig.reload(path));
        // 一份坏配置不能污染正在跑的运行时快照。
        assertEquals(before, CharredZombieConfig.current().resolve());
    }

    @Test
    void wrongSchemaVersionIsRejected() throws IOException {
        Path path = temp.resolve("schema.json");
        Files.writeString(path, """
                {
                  "schemaVersion": 99
                }
                """);

        assertThrows(IllegalStateException.class, () -> CharredZombieConfig.reload(path));
    }

    private static ResourceKey<DamageType> damageType(String id) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse(id));
    }

    @TempDir
    Path temp;
}
