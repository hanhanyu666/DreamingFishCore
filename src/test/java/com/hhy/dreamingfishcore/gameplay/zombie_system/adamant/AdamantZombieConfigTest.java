package com.hhy.dreamingfishcore.gameplay.zombie_system.adamant;

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

/** {@link AdamantZombieConfig} 的默认值、解析与校验。 */
class AdamantZombieConfigTest {

    @Test
    void missingFileWritesDocumentedDefaults() throws IOException {
        Path path = temp.resolve("adamant_zombie.json");

        AdamantZombieConfig loaded = AdamantZombieConfig.load(path);

        assertTrue(Files.exists(path), "缺失的配置文件应当被创建为默认文件");
        AdamantZombieConfig.Resolved resolved = loaded.resolve();
        assertTrue(resolved.enabled());
        assertEquals(40.0D, resolved.maxHealth(), 1.0E-9D);
        assertEquals(0.23D, resolved.movementSpeed(), 1.0E-9D);
        assertEquals(35.0D, resolved.followRange(), 1.0E-9D);
        assertEquals(3.0D, resolved.attackDamage(), 1.0E-9D);
        // 完全免疫击退：被打时纹丝不动，这是「它是堵墙」最主要的体感来源。
        assertEquals(1.0D, resolved.knockbackResistance(), 1.0E-9D);
        // 金属不该被日光烧死，也不该下水变溺尸（它的水反应是生锈）。
        assertFalse(resolved.burnInDaylight());
        assertFalse(resolved.convertInWater());
        assertEquals(3, resolved.maxRustStages());
        assertEquals(1, resolved.rustPerWaterHit());
        assertEquals(0.20D, resolved.damageBonusPerStage(), 1.0E-9D);
        assertTrue(resolved.rustInWaterOrRain());
        assertEquals(40, resolved.rustIntervalTicks());
        assertTrue(resolved.staggerEnabled());
        assertEquals(10, resolved.staggerDurationTicks());
        assertTrue(resolved.immuneFeedbackEnabled());
    }

    /**
     * 兜底通道默认只放开虚空。
     *
     * <p>火/岩浆/溺水都不能算兜底——那会让「必须先把它浇湿」这条机制被一个岩浆桶或一条河绕过。</p>
     */
    @Test
    void defaultFallbackOnlyAllowsTheVoid() throws IOException {
        AdamantZombieConfig.Resolved resolved = AdamantZombieConfig.load(
                temp.resolve("fallback.json")).resolve();

        assertEquals(1, resolved.alwaysEffectiveDamageTypes().size());
        assertTrue(resolved.alwaysEffectiveDamageTypes().contains(damageType("minecraft:out_of_world")));
    }

    @Test
    void partialJsonKeepsUntouchedDefaults() throws IOException {
        Path path = temp.resolve("partial.json");
        Files.writeString(path, """
                {
                  "maxHealth": 120.0,
                  "maxRustStages": 5,
                  "damageBonusPerStage": 0.5,
                  "alwaysEffectiveDamageTypes": []
                }
                """);

        AdamantZombieConfig.Resolved resolved = AdamantZombieConfig.load(path).resolve();

        assertEquals(120.0D, resolved.maxHealth(), 1.0E-9D);
        assertEquals(5, resolved.maxRustStages());
        assertEquals(0.5D, resolved.damageBonusPerStage(), 1.0E-9D);
        // 空数组是合法配置：「除了水以外，连虚空都弄不死它」。
        assertTrue(resolved.alwaysEffectiveDamageTypes().isEmpty());
        assertEquals(0.23D, resolved.movementSpeed(), 1.0E-9D);
    }

    @Test
    void invalidDamageTypeIdIsRejected() throws IOException {
        Path path = temp.resolve("bad_id.json");
        Files.writeString(path, """
                {
                  "alwaysEffectiveDamageTypes": ["不是合法 ID"]
                }
                """);

        assertThrows(IllegalStateException.class, () -> AdamantZombieConfig.reload(path));
    }

    @Test
    void reloadRejectsOutOfRangeValueAndKeepsLastKnownGood() throws IOException {
        Path path = temp.resolve("out_of_range.json");
        Files.writeString(path, """
                {
                  "knockbackResistance": 3.0
                }
                """);

        AdamantZombieConfig.Resolved before = AdamantZombieConfig.current().resolve();
        assertThrows(IllegalStateException.class, () -> AdamantZombieConfig.reload(path));
        assertEquals(before, AdamantZombieConfig.current().resolve());
    }

    @Test
    void wrongSchemaVersionIsRejected() throws IOException {
        Path path = temp.resolve("schema.json");
        Files.writeString(path, """
                {
                  "schemaVersion": 7
                }
                """);

        assertThrows(IllegalStateException.class, () -> AdamantZombieConfig.reload(path));
    }

    private static ResourceKey<DamageType> damageType(String id) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse(id));
    }

    @TempDir
    Path temp;
}
