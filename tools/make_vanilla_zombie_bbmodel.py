#!/usr/bin/env python3
"""生成一个**标准原版僵尸**的 Blockbench 模型（.bbmodel），可直接双击打开。

为什么要这个：项目里已有的 zombie.bbmodel 除了 6 个基础部件外还挂着 9 个带旋转的装饰 cube，
想从头改点什么很容易被那些多余的东西绊住。这份是干净的骨架 + 原版 UV + 原版贴图。

三件必须知道的事
----------------
1. **骨骼是真骨骼**。每个部件都挂在自己的 group 下，group 的 origin 就是关节位置
   （肩 / 髋 / 颈），在 Blockbench 里选 group 旋转就是"抬胳膊"；转换脚本会把它译成
   嵌套的 PartDefinition。
2. **手臂是自然下垂的**。僵尸那条标志性的前伸手臂是**渲染时由代码加的**
   （`AbstractZombieRenderer` 会在每帧把上臂转到 -90°）。把 90° 烘进模型里，转换之后
   会叠成 180°——手臂朝后。要纯当视觉参照，用 `--zombie-pose`。
3. **贴图的左臂 / 左腿是补过的**。原版左肢走的是 `CubeListBuilder.mirror()`（复用右肢
   UV），所以贴图里 `32,48` / `16,48` 两块是**空的**；直接按标准 box UV 摆会渲染成空白。
   这里把右肢的贴图逐面水平翻转搬过去，左肢就有皮了，而且不依赖任何 mirror 语义。

用法：
  python tools/make_vanilla_zombie_bbmodel.py [--zombie-pose]
输出：
  tools/vanilla_zombie.bbmodel
"""

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bbmodel_kit as kit

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_TEXTURE = os.path.join(ROOT, "src", "main", "resources", "assets", "dreamingfishcore",
                           "textures", "entity", "zombie", "zombie.png")
OUT = os.path.join(ROOT, "tools", "vanilla_zombie.bbmodel")

# 原版 HumanoidModel 的 UV 起点（64x64 贴图）
# 名字: (uv, 盒子的 bedrock 绝对坐标 from/to, 关节 pivot)
#   关节位置抄自 HumanoidModel.createMesh 的 PartPose.offset：
#     head/body (0,0,0)java -> (0,24,0)、上臂 (±5,2,0)java -> (±5,22,0)、
#     腿 (±1.9,12,0)java -> (±1.9,12,0)。
HEAD_FROM, HEAD_TO = (-4, 24, -4), (4, 32, 4)
BODY_FROM, BODY_TO = (-4, 12, -2), (4, 24, 2)
R_ARM_FROM, R_ARM_TO = (-8, 12, -2), (-4, 24, 2)
L_ARM_FROM, L_ARM_TO = (4, 12, -2), (8, 24, 2)
R_LEG_FROM, R_LEG_TO = (-3.9, 0, -2), (0.1, 12, 2)
L_LEG_FROM, L_LEG_TO = (-0.1, 0, -2), (3.9, 12, 2)

PIVOT_HEAD = (0, 24, 0)
PIVOT_BODY = (0, 24, 0)
PIVOT_R_ARM = (-5, 22, 0)
PIVOT_L_ARM = (5, 22, 0)
PIVOT_R_LEG = (-1.9, 12, 0)
PIVOT_L_LEG = (1.9, 12, 0)

# 左肢复用右肢贴图（照 HumanoidModel 的 .mirror()），已逐面水平翻转搬到了下面这两个位置
UV_HEAD = (0, 0)
UV_HAT = (32, 0)
UV_BODY = (16, 16)
UV_R_ARM = (40, 16)
UV_L_ARM = (32, 48)
UV_R_LEG = (0, 16)
UV_L_LEG = (16, 48)

LIMB_BOX = (4, 12, 4)   # 四肢的盒子尺寸，搬贴图时要用


def base_texture(size: int = 64, left_arm_uv=UV_L_ARM, left_leg_uv=UV_L_LEG):
    """在原版僵尸贴图基础上补齐左肢、清掉 hat 层那点杂色，返回 (w, h, rows)。

    左肢：把右臂 / 右腿那块贴图**逐面水平翻转**搬到左肢的 UV 位置。
    逐面翻（而不是整块翻）才对——整块翻会把 east/south 这些面互相调换，六个面全错位。

    左肢落在哪个 UV 由 `left_arm_uv` / `left_leg_uv` 决定：默认用原版位置（32,48）/（16,48），
    指挥官那份因为要 128x128 的额外空间，会挪到右边空白区。
    """
    w, h, rows = kit.read_png(SRC_TEXTURE)
    canvas = kit.blank(size, size)
    kit.box_blit(canvas, rows, 0, 0, 64, 64, 0, 0)

    # hat 层在原版僵尸贴图里本该整块透明，这份文件里残留了一个白点，会渲染成白斑
    kit.fill(canvas, UV_HAT[0], UV_HAT[1], UV_HAT[0] + 32, UV_HAT[1] + 16, (0, 0, 0, 0))

    w1, h1, d1 = LIMB_BOX
    for src_uv, dst_uv in ((UV_R_ARM, left_arm_uv), (UV_R_LEG, left_leg_uv)):
        src_faces = kit.face_bounds(src_uv[0], src_uv[1], w1, h1, d1)
        dst_faces = kit.face_bounds(dst_uv[0], dst_uv[1], w1, h1, d1)
        for face, (sx0, sy0, sx1, sy1) in src_faces.items():
            dx0, dy0, _, _ = dst_faces[face]
            kit.flip_blit(canvas, rows, sx0, sy0, sx1 - sx0, sy1 - sy0, dx0, dy0)
    return size, size, canvas


def base_elements(zombie_pose: bool = False) -> list[dict]:
    """七个原版部件的立方体。zombie_pose=True 时把前伸手臂烘进几何（仅供视觉参照）。"""
    arm_rot = (90, 0, 0) if zombie_pose else None
    return [
        kit.element("head_box", HEAD_FROM, HEAD_TO, UV_HEAD, origin=PIVOT_HEAD),
        kit.element("hat", HEAD_FROM, HEAD_TO, UV_HAT, inflate=0.5, origin=PIVOT_HEAD),
        kit.element("body_box", BODY_FROM, BODY_TO, UV_BODY, origin=PIVOT_BODY),
        kit.element("right_arm_box", R_ARM_FROM, R_ARM_TO, UV_R_ARM,
                    origin=PIVOT_R_ARM, rotation=arm_rot),
        kit.element("left_arm_box", L_ARM_FROM, L_ARM_TO, UV_L_ARM,
                    origin=PIVOT_L_ARM, rotation=arm_rot),
        kit.element("right_leg_box", R_LEG_FROM, R_LEG_TO, UV_R_LEG, origin=PIVOT_R_LEG),
        kit.element("left_leg_box", L_LEG_FROM, L_LEG_TO, UV_L_LEG, origin=PIVOT_L_LEG),
    ]


def base_tree() -> list:
    return [
        ("g", "root", (0, 0, 0), [
            ("g", "body", PIVOT_BODY, [("e", "body_box")]),
            ("g", "head", PIVOT_HEAD, [("e", "head_box"), ("e", "hat")]),
            ("g", "right_arm", PIVOT_R_ARM, [("e", "right_arm_box")]),
            ("g", "left_arm", PIVOT_L_ARM, [("e", "left_arm_box")]),
            ("g", "right_leg", PIVOT_R_LEG, [("e", "right_leg_box")]),
            ("g", "left_leg", PIVOT_L_LEG, [("e", "left_leg_box")]),
        ]),
    ]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--zombie-pose", action="store_true",
                    help="把僵尸的前伸手臂烘进几何（只当视觉参照；会给转换后的模型带上双倍旋转）")
    args = ap.parse_args()

    size, _, rows = base_texture(64)
    png = kit.encode_png(size, size, rows)
    doc = kit.build_doc(
        "vanilla_zombie", (size, size), base_elements(args.zombie_pose), base_tree(),
        kit.texture_entry(png, "zombie.png",
                          "../src/main/resources/assets/dreamingfishcore/textures/entity/zombie/zombie.png",
                          size, size))
    kit.save(OUT, doc)
    size_kb = os.path.getsize(OUT) / 1024
    print("写出 %s（%.0f KB）%s"
          % (OUT, size_kb, "  [已烘焙僵尸姿态]" if args.zombie_pose else ""))
    kit.describe(doc)


if __name__ == "__main__":
    main()
