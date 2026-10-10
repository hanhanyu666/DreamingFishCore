package com.hhy.dreamingfishcore.gameplay.zombie_system.boss;

import com.hhy.dreamingfishcore.gameplay.zombie_system.boss.client.ZombieCommanderModel;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 指挥官的**静止姿势**：军刀是不是真的被握在手上。
 *
 * <p>守的坑很小、很隐蔽：转换脚本（{@code tools/convert_commander_model.py} → 复用的
 * {@code convert_archer_zombie.py}）早期只读了骨骼的 {@code origin}、把骨骼自带的
 * {@code rotation} 整条丢掉，于是 {@code saber} 变成一条「位置对、角度归零」的骨骼 ——
 * bbmodel 里那把军刀绕 x 转了 105°（刀尖前指），进游戏后却顺着小臂直直朝下扎到脚底，
 * 看起来就不像被手握着。这种丢失完全静默：能编译、能加载、日志一个字都没有。</p>
 *
 * <p>所以这里<b>不看源码文本</b>，而是把 {@link ZombieCommanderModel#createBodyLayer()}
 * 真的烘一遍，直接检查烘出来的 {@link ModelPart} 姿势。{@code createBodyLayer} 只搭数据，
 * 不需要引导注册表。参考数值来自 bbmodel（{@code tools/zombie_commander.bbmodel}：
 * {@code saber} 的 origin {@code [-6,14,0]}、rotation {@code [105,0,0]}；{@code saber_tip}
 * 中心在枢轴下方 12.7 像素）。</p>
 */
class ZombieCommanderPoseTest {
    /** bbmodel 里 saber 骨骼的 origin = [-6,14,0]（Bedrock，脚底为 0）。 */
    private static final float SABER_PIVOT_JAVA_Y = 24.0F - 14.0F;
    /** 小臂枢轴 = [-5,22,0] → java (-5, 2, 0)。 */
    private static final float ARM_JAVA_Y = 2.0F;
    /** 刀身长：枢轴 → saber_tip 中心的距离（像素）。 */
    private static final float BLADE_LENGTH = 12.7F;
    /** bbmodel 的 105° 在 Java 侧应当是 −105°（y 轴翻转使绕 x 的转角取反）。 */
    private static final float EXPECTED_X_ROT = (float) Math.toRadians(-105.0D);

    private static ModelPart bakedRoot() {
        return ZombieCommanderModel.createBodyLayer().bakeRoot();
    }

    @Test
    void saberKeepsItsOwnRotation() {
        ModelPart saber = bakedRoot().getChild("right_arm").getChild("saber");

        assertEquals(EXPECTED_X_ROT, saber.xRot, 1.0E-3F,
                "军刀的自转被丢掉了：bbmodel 里它绕 x 转了 105°，生成 Java 时必须落到 offsetAndRotation");
        assertEquals(0.0F, saber.yRot, 1.0E-3F, "军刀没有绕 y 的自转");
        assertEquals(0.0F, saber.zRot, 1.0E-3F, "军刀没有绕 z 的自转");
    }

    @Test
    void saberPivotSitsAtTheHand() {
        ModelPart arm = bakedRoot().getChild("right_arm");
        ModelPart saber = arm.getChild("saber");

        // 小臂的盒子在它自己的局部空间里是 (-3,-2,-2)..(1,10,2)（4x12x4），枢轴必须落在里面。
        assertTrue(saber.x > -3.0F && saber.x < 1.0F && saber.z > -2.0F && saber.z < 2.0F,
                "军刀的枢轴跑到小臂盒子外面了：" + saber.x + "/" + saber.z);
        assertTrue(saber.y > 6.0F && saber.y < 10.0F,
                "军刀的枢轴必须落在小臂的下半段（手的位置），实际 y=" + saber.y);

        // 枢轴（java 绝对 y）应当和 bbmodel 的 (24 - 14) 对上。
        assertEquals(SABER_PIVOT_JAVA_Y, ARM_JAVA_Y + saber.y, 1.0E-3F,
                "军刀枢轴的高度与 bbmodel 对不上");
    }

    /**
     * 刀尖必须伸到身前 —— 这是「握在手上」与「挂在手上」的分水岭。
     *
     * <p>把旋转丢掉时 {@code xRot=0}，刀身顺着小臂垂直向下：刀尖落在 {@code z≈0}（贴着躯干）、
     * {@code y≈22.7}（脚底附近）。这条用例就是照那个失败形态写的。</p>
     */
    @Test
    void bladePointsForwardInsteadOfStraightDown() {
        ModelPart arm = bakedRoot().getChild("right_arm");
        ModelPart saber = arm.getChild("saber");

        // saber 的局部 +y 是刀身方向（Java 的 y 向下），自转把它转到 (0, cos, sin)。
        float tipY = arm.y + saber.y + (float) (BLADE_LENGTH * Math.cos(saber.xRot));
        float tipZ = arm.z + saber.z + (float) (BLADE_LENGTH * Math.sin(saber.xRot));

        assertTrue(tipZ < -8.0F,
                "刀尖应当指到身前（模型正面是 −z），实际 z=" + tipZ
                        + "；旋转被丢掉时它是 0，刀会垂直扎向地面");
        assertTrue(tipY < 12.0F,
                "刀尖高度应当在上半身（Java 的 y 向下，越小越高），实际 y=" + tipY
                        + "；旋转被丢掉时它会顺着小臂落到脚底（y≈22.7）");
    }
}
