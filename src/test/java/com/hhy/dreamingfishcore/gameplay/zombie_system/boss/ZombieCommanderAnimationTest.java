package com.hhy.dreamingfishcore.gameplay.zombie_system.boss;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.gameplay.zombie_system.boss.client.ZombieCommanderModel;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 指挥官六组动画的资源一致性。
 *
 * <p>守两类**静默出错**的坑：</p>
 *
 * <ol>
 *   <li><b>骨骼名写错</b>：动画 JSON 是按名字找 {@code ModelPart} 的，名字对不上那条轨道会
 *       被悄悄忽略 —— 表现是"某条腿永远不动"，日志里一个字都没有。所以这里拿
 *       {@link ZombieCommanderModel#createBodyLayer()} 真的烘一遍，逐个名字去取。</li>
 *   <li><b>AI 与动画脱节</b>：伤害结算的 tick、插旗的 tick 都是照着动画关键帧定的
 *       （见 {@link ZombieCommanderRules}）。改了动画忘了改常量，就会出现"刀还没挥到就掉血"。
 *       这里反查：那几个 tick 必须真的落在某条关键帧上。</li>
 * </ol>
 *
 * <p>本测试不引导注册表：{@code createBodyLayer} 只搭数据，静态的 {@code AnimationHolder}
 * 也是惰性构造的（真正绑定要等资源重载）。</p>
 */
class ZombieCommanderAnimationTest {
    private static final String DIR =
            "/assets/dreamingfishcore/neoforge/animations/entity/zombie_commander/";
    private static final String[] NAMES =
            {"walk", "melee_1", "melee_2", "melee_3", "shoot", "summon"};

    /** 不在 root 下的骨骼 -> 它挂在谁下面。 */
    private static final Map<String, String> NESTED = Map.of(
            "cap", "head", "flag", "body", "saber", "right_arm");

    /** 动画里用到的骨骼；与 bbmodel 的骨骼表一致。 */
    private static final String[] BONES = {
            "body", "head", "right_arm", "left_arm", "right_leg", "left_leg",
            "cap", "flag", "saber"};

    private static JsonObject read(String name) throws IOException {
        InputStream stream = ZombieCommanderAnimationTest.class.getResourceAsStream(DIR + name + ".json");
        assertNotNull(stream, "缺少动画文件 " + name + ".json");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /** 骨骼名 -> 它的 (通道, 关键帧时间秒) 列表。 */
    private static Map<String, Map<String, double[]>> tracks(JsonObject doc) {
        Map<String, Map<String, double[]>> out = new LinkedHashMap<>();
        JsonArray animations = doc.getAsJsonArray("animations");
        for (var element : animations) {
            JsonObject entry = element.getAsJsonObject();
            JsonArray keyframes = entry.getAsJsonArray("keyframes");
            double[] times = new double[keyframes.size()];
            for (int i = 0; i < keyframes.size(); i++) {
                times[i] = keyframes.get(i).getAsJsonObject().get("timestamp").getAsDouble();
            }
            out.computeIfAbsent(entry.get("bone").getAsString(), key -> new LinkedHashMap<>())
                    .put(entry.get("target").getAsString().substring("minecraft:".length()), times);
        }
        return out;
    }

    private static boolean hasTimestamp(JsonObject doc, String bone, double seconds) {
        Map<String, double[]> byChannel = tracks(doc).get(bone);
        if (byChannel == null) return false;
        for (double[] times : byChannel.values()) {
            for (double time : times) {
                if (Math.abs(time - seconds) < 1.0E-6D) return true;
            }
        }
        return false;
    }

    @Test
    void everyAnimationFileExistsAndIsWellFormed() throws IOException {
        for (String name : NAMES) {
            JsonObject doc = read(name);
            assertTrue(doc.has("length"), name + " 缺少 length");
            assertTrue(doc.get("length").getAsDouble() > 0.0D, name + " 的 length 必须为正");
            assertTrue(doc.has("loop"), name + " 缺少 loop");
            assertEquals("walk".equals(name), doc.get("loop").getAsBoolean(),
                    "只有行走是循环动画，其余都是一次性动作");
            JsonArray animations = doc.getAsJsonArray("animations");
            assertTrue(animations.size() > 0, name + " 没有任何轨道");

            double length = doc.get("length").getAsDouble();
            for (var element : animations) {
                JsonObject entry = element.getAsJsonObject();
                String bone = entry.get("bone").getAsString();
                assertTrue(java.util.Arrays.asList(BONES).contains(bone),
                        name + " 引用了不存在的骨骼 " + bone);
                assertTrue(entry.get("target").getAsString().startsWith("minecraft:"),
                        name + " 的 target 必须带命名空间");
                JsonArray keyframes = entry.getAsJsonArray("keyframes");
                assertTrue(keyframes.size() >= 2, name + "/" + bone + " 至少要有两个关键帧");
                for (var keyframe : keyframes) {
                    JsonObject frame = keyframe.getAsJsonObject();
                    double time = frame.get("timestamp").getAsDouble();
                    assertTrue(time >= 0.0D && time <= length + 1.0E-6D,
                            name + "/" + bone + " 的关键帧 " + time + " 越过了 length=" + length);
                    assertEquals(3, frame.getAsJsonArray("target").size(),
                            name + "/" + bone + " 的 target 必须是三元组");
                }
            }
        }
    }

    @Test
    void animationBonesAllResolveAgainstTheGeneratedModel() {
        ModelPart root = ZombieCommanderModel.createBodyLayer().bakeRoot();
        for (String bone : BONES) {
            String parent = NESTED.get(bone);
            assertDoesNotThrow(() -> {
                if (parent == null) {
                    root.getChild(bone);
                } else {
                    root.getChild(parent).getChild(bone);
                }
            }, "骨骼 " + bone + " 在生成的模型里不存在，动画里那条轨道会被静默忽略");
        }
    }

    /** AI 的伤害结算 tick 必须落在动画的关键帧上，否则"刀还没挥到就已经掉血"。 */
    @Test
    void meleeImpactTicksLandOnRealKeyframes() throws IOException {
        for (int step = 1; step <= ZombieCommanderRules.MELEE_STEPS; step++) {
            JsonObject doc = read("melee_" + step);
            double impact = ZombieCommanderRules.meleeImpactTick(step) / 20.0D;
            assertTrue(hasTimestamp(doc, "right_arm", impact),
                    "第 " + step + " 段的命中 tick " + ZombieCommanderRules.meleeImpactTick(step)
                            + "（" + impact + "s）不是 right_arm 的关键帧");
            double length = doc.get("length").getAsDouble();
            assertEquals(Math.round(length * 20.0D), ZombieCommanderRules.meleeLengthTick(step),
                    "第 " + step + " 段时长与动画 length=" + length + " 不一致");
            assertTrue(ZombieCommanderRules.meleeImpactTick(step)
                            < ZombieCommanderRules.meleeLengthTick(step),
                    "命中必须发生在这一段结束之前");
        }
    }

    @Test
    void summonSlamAndShootReleaseLandOnRealKeyframes() throws IOException {
        JsonObject summon = read("summon");
        double slam = ZombieCommanderAnimationTest.IMPACT_SUMMON / 20.0D;
        assertTrue(hasTimestamp(summon, "flag", slam),
                "插旗的 tick " + ZombieCommanderAnimationTest.IMPACT_SUMMON
                        + "（" + slam + "s）不是 flag 骨骼的关键帧 —— "
                        + "改了动画就要同步改 summonChargeTicks");
        // 旗子插完还要把旗子拔回背上，所以动画必须比插地那一刻更长
        assertTrue(summon.get("length").getAsDouble() > slam,
                "summon 动画在插地之后还要留出收势时间");

        JsonObject shoot = read("shoot");
        double burst = ZombieCommanderAnimationTest.IMPACT_SHOOT / 20.0D;
        assertTrue(hasTimestamp(shoot, "right_arm", burst),
                "第一发弹丸的 tick " + ZombieCommanderAnimationTest.IMPACT_SHOOT
                        + "（" + burst + "s）不是 right_arm 的关键帧");
    }

    /** 与 {@code tools/commander_animations.py} 里的 IMPACT_TICKS 保持一致。 */
    private static final int IMPACT_SUMMON = 40;
    private static final int IMPACT_SHOOT = 24;
}
