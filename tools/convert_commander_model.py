#!/usr/bin/env python3
"""把 `tools/zombie_commander.bbmodel` 转成 Java 几何 + 贴图，并写出四组动画 JSON。

用法：
  python tools/convert_commander_model.py
输出：
  src/main/java/com/hhy/dreamingfishcore/gameplay/zombie_system/boss/client/ZombieCommanderModel.java
  src/main/resources/assets/dreamingfishcore/textures/entity/zombie_commander/zombie_commander.png
  src/main/resources/assets/dreamingfishcore/neoforge/animations/entity/zombie_commander/*.json

⚠️ 与 `convert_archer_zombie.py` 的**坐标约定不同，别混用**
------------------------------------------------------------------
本脚本按 Minecraft 的原生 z 约定换算：`java = (x, 24 - y, z)`。
依据是「模型正面 = −z」在两边都成立（原版村民的鼻子在 java 的 −z 侧、皮肤模板的脸也在
−z 侧），所以 z **不能取反**。

`convert_archer_zombie.py` 用的是 `(x, 24 - y, -z)`（把 z 取了反）。那没问题 —— 因为
射手僵尸那份 bbmodel 本身就是**镜像着搭的**（它的箭袋放在 bbmodel 的 −z 侧，取反之后才落到
游戏里的背后）。两套约定各自自洽，但**不能把一份 bbmodel 换来换去地用**：
拿本脚本去转射手僵尸的模型，会把它的箭袋翻到胸前。
"""

from __future__ import annotations

import base64
import json
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bbmodel_kit as kit
import commander_animations as animations

ROOT = Path(__file__).resolve().parent.parent
BBMODEL = ROOT / "tools" / "zombie_commander.bbmodel"
MODEL_OUT = (ROOT / "src/main/java/com/hhy/dreamingfishcore/gameplay/"
             "zombie_system/boss/client/ZombieCommanderModel.java")
TEXTURE_OUT = (ROOT / "src/main/resources/assets/dreamingfishcore/"
               "textures/entity/zombie_commander/zombie_commander.png")
ANIM_OUT = (ROOT / "src/main/resources/assets/dreamingfishcore/"
            "neoforge/animations/entity/zombie_commander")


# ---------------------------------------------------------------------------
# 几何生成（复用射手僵尸那份转换器的骨架）
# ---------------------------------------------------------------------------
import convert_archer_zombie as cv  # noqa: E402


def mirror_z(doc: dict) -> dict:
    """把 bbmodel 的 z 整体镜像一份，再交给原来的转换器。

    为什么不直接改 `to_java_point`：转换器内部是**假设 z 会被取反**写的 ——
    它用 `to_java_point(frm.x, to.y, to.z)` 去算 java 的盒角，也就是拿「bedrock 的最大 z」
    当成「java 的最小 z」。把转换函数换成不取反的版本，那个配对就错位了
    （头部盒子会跑到 java 的 +z 侧，也就是背后）。

    镜一次 z 再让它照常取反，正好抵消：`-( -z ) = z` ✓。
    镜像时要连 from/to 一起交换，否则宽高会变成负数。
    """
    out = json.loads(json.dumps(doc))          # 深拷贝
    for el in out["elements"]:
        frm, to = el["from"], el["to"]
        el["from"] = [frm[0], frm[1], -to[2]]
        el["to"] = [to[0], to[1], -frm[2]]
        if "origin" in el:
            el["origin"] = [el["origin"][0], el["origin"][1], -el["origin"][2]]
        if "rotation" in el:
            # 绕 z 镜像时，绕 x / 绕 y 的转角要取反（绕 z 的不变）；再由转换器统一的
            # (rx, -ry, -rz) 折回来，净效果才是「同一个物理姿态」。
            el["rotation"] = [-el["rotation"][0], -el["rotation"][1], el["rotation"][2]]
    for g in out.get("groups", []):
        g["origin"] = [g["origin"][0], g["origin"][1], -g["origin"][2]]
        # **骨骼自身的旋转也要镜像**（漏了它会让转换器把军刀那 105° 算成反向，
        # 刀就朝背后去了）：转换器只认「已镜像」的这份文档。
        if g.get("rotation"):
            g["rotation"] = [-float(g["rotation"][0]), -float(g["rotation"][1]),
                             float(g["rotation"][2])]
    return out


JAVA_TEMPLATE = '''package com.hhy.dreamingfishcore.gameplay.zombie_system.boss.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.boss.ZombieCommanderEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.entity.animation.json.AnimationHolder;

/**
 * 尸潮指挥官的模型：由 {@code tools/convert_commander_model.py} 从 {@code zombie_commander.bbmodel} 生成，
 * <b>不要手改本文件</b>——改模型请改 bbmodel 后重跑脚本。
 *
 * <p>骨骼比原版僵尸多了三条：{@code cap}（大檐帽，挂在头下）、{@code flag}（背旗，挂在躯干下）、
 * {@code saber}（军刀，挂在右手下）。动画共六组，全部来自资源包 JSON
 * （{@code neoforge/animations/entity/zombie_commander/}），调动作不需要重新编译。</p>
 *
 * <p>坐标约定：Bedrock 是「y 向上、脚底 0、面朝 −z」，原版 Java 是「y 向下、颈部 0、面朝 −z」，
 * 相差 {@code (x, 24 - y, z)} —— 面朝方向两边都是 −z，所以 <b>z 不取反</b>。</p>
 */
public class ZombieCommanderModel extends HierarchicalModel<ZombieCommanderEntity> {
    private static final String PATH = "zombie_commander/";

    /**
     * 六组动画的句柄。
     *
     * <p>必须持有 {@link AnimationHolder} 本身，不能在类加载时 {@code .get()} 出
     * {@code AnimationDefinition} 缓存成常量 —— 动画是**资源重载之后**才绑定的，
     * 类加载那一刻拿到的只会是空动画（表现就是模型僵住）。holder 每次重载都会重新绑定。</p>
     */
    private static final AnimationHolder WALK = anim("walk");
    private static final AnimationHolder MELEE_1 = anim("melee_1");
    private static final AnimationHolder MELEE_2 = anim("melee_2");
    private static final AnimationHolder MELEE_3 = anim("melee_3");
    private static final AnimationHolder SHOOT = anim("shoot");
    private static final AnimationHolder SUMMON = anim("summon");

    /** 走路动画的驱动系数，取值与原版 {@code HumanoidModel} 一致（limbSwing * 50 * 系数）。 */
    private static final float WALK_SPEED = 2.0F;
    private static final float WALK_SCALE = 2.5F;
    private static final float ACTION_SPEED = 1.0F;

    private final ModelPart root;
    private final ModelPart head;

    public ZombieCommanderModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
    }

    private static AnimationHolder anim(String name) {
        return getAnimation(ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, PATH + name));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition root = meshdefinition.getRoot();
{geometry}
        return LayerDefinition.create(meshdefinition, {tex_width}, {tex_height});
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(
            ZombieCommanderEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        // 走路由肢体摆动驱动循环动画；四组主动作都是「播一遍」，由实体同步的状态决定播哪个。
        this.animateWalk(WALK, limbSwing, limbSwingAmount, WALK_SPEED, WALK_SCALE);
        this.animate(entity.melee1AnimationState, MELEE_1, ageInTicks, ACTION_SPEED);
        this.animate(entity.melee2AnimationState, MELEE_2, ageInTicks, ACTION_SPEED);
        this.animate(entity.melee3AnimationState, MELEE_3, ageInTicks, ACTION_SPEED);
        this.animate(entity.shootAnimationState, SHOOT, ageInTicks, ACTION_SPEED);
        this.animate(entity.summonAnimationState, SUMMON, ageInTicks, ACTION_SPEED);

        // 头部跟随视线。动画里的头是「额外叠加」的，所以这里用 += 而不是覆盖，
        // 否则召唤时那个仰头/低头的动作会被视线直接抹掉。
        this.head.yRot += netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot += headPitch * Mth.DEG_TO_RAD;
    }
}
'''


def collect_duplicates(geometry: str) -> list[str]:
    seen = set()
    duplicated = []
    for parent, part in re.findall(r'(\w+)\.addOrReplaceChild\("([^"]+)"', geometry):
        key = (parent, part)
        if key in seen:
            duplicated.append(f"{parent}/{part}")
        seen.add(key)
    return duplicated


def main() -> int:
    doc = mirror_z(json.loads(BBMODEL.read_text(encoding="utf-8")))
    print(f"读入 {BBMODEL.name}：{len(doc['elements'])} 个立方体、{len(doc.get('groups', []))} 条骨骼"
          "（已镜像 z 以对齐转换器的约定）")
    if doc.get("animations"):
        print("  [提示] bbmodel 里内嵌的动画被忽略：动画的唯一来源是 commander_animations.py")

    converter = cv.Converter(doc)
    geometry, skipped = converter.emit()

    duplicated = collect_duplicates(geometry)
    if duplicated:
        print(f"[错误] 部件重名会导致模型缺块：{duplicated}", file=sys.stderr)
        return 2
    cube_count = len(re.findall(r"\.addBox\(", geometry))
    print(f"  自检通过：{cube_count} 个立方体，无重名")

    # 自检二：bbmodel 里带**自身旋转**的骨骼，生成的代码里必须落到 offsetAndRotation。
    # 「旋转被静默丢掉」是这套链路最阴的一类 bug（模型能编译、能加载、就是角度不对），
    # 所以在这里做一次硬拦。
    rotated = [g["name"] for g in doc.get("groups", [])
               if g.get("rotation") and any(float(v) for v in g["rotation"])]
    dropped = [name for name in rotated
               if f'"{name}", CubeListBuilder.create(), PartPose.offset(' in geometry]
    if dropped:
        print(f"[错误] 这些骨骼的自转被丢掉了（应为 offsetAndRotation）：{dropped}",
              file=sys.stderr)
        return 3
    for name in rotated:
        print(f"  自检通过：骨骼 {name} 的自转已保留")

    width = int(doc["resolution"]["width"])
    height = int(doc["resolution"]["height"])
    MODEL_OUT.parent.mkdir(parents=True, exist_ok=True)
    MODEL_OUT.write_text(
        JAVA_TEMPLATE.replace("{geometry}", geometry)
                    .replace("{tex_width}", str(width))
                    .replace("{tex_height}", str(height)),
        encoding="utf-8")
    print(f"写出几何：{MODEL_OUT.relative_to(ROOT)}")
    for note in skipped:
        print(f"  [注意] {note}")

    # 贴图：直接用 bbmodel 内嵌的那张（本模型的贴图本来就是脚本生成的，不需要再烘焙）
    raw = base64.b64decode(doc["textures"][0]["source"].split(",", 1)[1])
    TEXTURE_OUT.parent.mkdir(parents=True, exist_ok=True)
    TEXTURE_OUT.write_bytes(raw)
    print(f"写出贴图：{TEXTURE_OUT.relative_to(ROOT)}（{width}x{height}）")

    for path in animations.write_all(str(ANIM_OUT)):
        print(f"写出动画：{Path(path).relative_to(ROOT)}")
    print("\nAI 对齐用的落点（tick）：" + "，".join(
        f"{k}={v}" for k, v in animations.IMPACT_TICKS.items()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
