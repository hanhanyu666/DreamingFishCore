#!/usr/bin/env python3
"""在原版僵尸基础上生成**僵尸指挥官**的 Blockbench 模型：细化关节 + 大檐帽 + 背旗 + 军刀。

相对 `vanilla_zombie.bbmodel` 做了五件事
----------------------------------------
1. **关节细化**：七个部件各自挂在以关节为 origin 的骨骼下（肩 / 髋 / 颈），
   另加三条可独立驱动的骨骼 —— `cap`（帽子，挂在头下）、`flag`（旗帜，挂在躯干下）、
   `saber`（军刀，挂在右手下）。在 Blockbench 里选骨骼旋转就是绕关节转，
   转换脚本会译成嵌套的 PartDefinition。
2. **左肢有个坑被填了**：原版左臂 / 左腿走的是 `CubeListBuilder.mirror()`（复用右肢 UV），
   所以贴图里 `32,48` / `16,48` 两块是**空的**——按标准 box UV 摆会渲染出一只白手。
   这里把左肢挪到新 UV 位置，并把右肢贴图**逐面水平翻转**搬过去，不依赖 mirror 语义。
3. **大檐帽**：帽檐 / 帽墙 / 帽冠 / 帽徽四个立方体，深蓝帽墙压金边、金色帽徽。
4. **背旗**：旗杆 + 金色旗顶 + 猩红旗面（金色上下压边 + 骨白骷髅徽）+ 下垂旗尾 + 皮革挂带。
   旗杆挂在模型左后侧（x 4.5~5.5、z 2.8~3.8），避开了头和手臂，也避开躯干背面。
5. **军刀**：握在右手（刀首 / 握把 / 护手 / 刀身 / 刀尖），刀尖朝下自然垂在腿旁。
   旗帜在左、军刀在右，一左一右。

贴图是 128x128：左上角 64x64 原样保留原版僵尸，其余空间放左肢与新增装备。
`tools/zombie_commander_texture.png` 是同一张图，等模型转成 Java 几何后放进 resources 用。

用法：
  python tools/make_commander_zombie_bbmodel.py [--zombie-pose]
输出：
  tools/zombie_commander.bbmodel
  tools/zombie_commander_texture.png
"""

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bbmodel_kit as kit
import make_vanilla_zombie_bbmodel as vanilla

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_MODEL = os.path.join(ROOT, "tools", "zombie_commander.bbmodel")
OUT_TEXTURE = os.path.join(ROOT, "tools", "zombie_commander_texture.png")

# ------------------------------------------------------------------ 配色

CRIMSON = (146, 38, 44, 255)
CRIMSON_DARK = (104, 24, 30, 255)
GOLD = (208, 168, 62, 255)
GOLD_LIGHT = (240, 208, 116, 255)
GOLD_DARK = (150, 116, 38, 255)
NAVY = (40, 48, 74, 255)
NAVY_DARK = (24, 30, 48, 255)
CROWN = (52, 60, 88, 255)
CROWN_TOP = (74, 82, 112, 255)
CROWN_DARK = (36, 42, 64, 255)
BRIM = (26, 26, 34, 255)
BRIM_TOP = (54, 54, 68, 255)
WOOD = (92, 64, 38, 255)
WOOD_DARK = (64, 44, 26, 255)
LEATHER = (62, 46, 32, 255)
BONE = (228, 224, 210, 255)
EYE_SOCKET = (24, 18, 18, 255)
STEEL = (196, 204, 218, 255)
STEEL_LIGHT = (240, 246, 255, 255)
STEEL_DARK = (148, 156, 172, 255)
GRIP = (56, 40, 28, 255)
GRIP_WIRE = (168, 138, 68, 255)

# ------------------------------------------------------------------ 贴图分区
# 全部集中在 64..127 这块空白区，互不重叠；左上角 64x64 留给原版僵尸本体。
SIZE = 128
UV_L_ARM = (64, 0)         # 16x16
UV_L_LEG = (80, 0)         # 16x16
UV_CAP_BRIM = (64, 16)     # 22x3
UV_CAP_BAND = (64, 24)     # 40x12
UV_CAP_TOP = (64, 40)      # 44x14
UV_CAP_BADGE = (64, 58)    # 10x3
UV_FLAG_FINIAL = (64, 64)  # 8x4
UV_FLAG_STRAP = (80, 64)   # 6x3
UV_FLAG_POLE = (64, 72)    # 4x25
UV_FLAG_CLOTH = (72, 72)   # 16x10
UV_FLAG_TAIL = (88, 72)    # 10x5
UV_SABER_GUARD = (96, 0)   # 12x4
UV_SABER_GRIP = (108, 0)   # 4x5
UV_SABER_POMMEL = (114, 0)  # 4x2
UV_SABER_BLADE = (96, 8)   # 6x10
UV_SABER_TIP = (108, 8)    # 4x2
UV_SABER_BOW = (114, 8)    # 4x6

# ------------------------------------------------------------------ 几何
PIVOT_CAP = (0, 32, 0)     # 头顶
PIVOT_FLAG = (4.5, 20, 3)  # 背部挂点
PIVOT_SABER = (-6, 14, 0)  # 右手握把中心

CAP_BRIM = ((-4.5, 30, -7), (4.5, 31, -5))        # 9x1x2   帽檐（前伸 2 格）
CAP_BAND = ((-5, 30, -5), (5, 32, 5))             # 10x2x10 帽墙（比 hat 层再大 0.5，避免共面）
CAP_TOP = ((-5.5, 32, -5.5), (5.5, 35, 5.5))      # 11x3x11 帽冠（比帽墙再外扩 0.5 = 大檐帽的檐）
CAP_BADGE = ((-2, 30, -6), (2, 32, -5))           # 4x2x1   帽徽

# 旗帜挂在模型的左后侧（x 正方向），整组刻意与躯干背面（z=2）错开，
# 免得共面 —— 共面会 z-fighting，从正面还能看见挂带穿出来。
FLAG_STRAP = ((3, 19, 2.7), (5, 21, 3.7))         # 2x2x1   皮革挂带（把旗杆绑在背上）
FLAG_POLE = ((4.5, 12, 2.8), (5.5, 36, 3.8))      # 1x24x1  旗杆
FLAG_FINIAL = ((4, 36, 2.3), (6, 38, 4.3))        # 2x2x2   旗顶金饰
FLAG_CLOTH = ((5.3, 26, 3), (12.3, 35, 4))        # 7x9x1   旗面（略微插进旗杆，避免贴面共面）
FLAG_TAIL = ((5.3, 22, 3), (9.3, 26, 4))          # 4x4x1   下垂旗尾

# 军刀握在**右手**（旗帜在左手那侧，一左一右）。刀尖朝下，刀身自然垂在腿旁。
# 整组挂在 right_arm 骨骼下，所以以后抬手臂它会跟着走。
# 握把/刀首会落在手臂立方体内部（x -8~-4、y 12~24 都在里面）—— 这是对的，
# 现实中它们本来就被拳头握住看不见；露在外面的是护手、刀身和刀尖。
# 刀身刻意做成「2 宽(x) × 1 厚(z)」：**刀面朝向正前方**，正面才看得出是一把刀；
# 反过来（1 宽 2 厚）正面只剩 1 像素宽的一条线，实测几乎看不见。
# 整体比手臂底面（y=12）再低 0.2，避免贴着共面。
SABER_POMMEL = ((-6.5, 15.8, -0.5), (-5.5, 16.8, 0.5))   # 1x1x1  刀首
SABER_GRIP = ((-6.5, 11.8, -0.5), (-5.5, 15.8, 0.5))     # 1x4x1  握把
SABER_BOW = ((-8.6, 11.8, -0.5), (-7.6, 16.8, 0.5))      # 1x5x1  护指（刀镡向外那道护手弧）
SABER_GUARD = ((-7.5, 10.8, -1.5), (-4.5, 11.8, 1.5))    # 3x1x3  护手（四面都看得出）
SABER_BLADE = ((-7, 1.8, -0.5), (-5, 10.8, 0.5))         # 2x9x1  刀身
SABER_TIP = ((-6.5, 0.8, -0.5), (-5.5, 1.8, 0.5))        # 1x1x1  刀尖（收窄成尖）

# 旗面上的骷髅徽：五格宽，必须留出四周的猩红底，否则会糊成一整块骨白
SKULL = [
    ".###.",
    "#####",
    "#.#.#",
    "#.#.#",
    "#####",
    ".#.#.",
]


# ------------------------------------------------------------------ 贴图绘制

def _rect(rows, bounds, face, color):
    x0, y0, x1, y1 = bounds[face]
    kit.fill(rows, x0, y0, x1, y1, color)
    return x0, y0, x1, y1


def paint_cap(rows) -> None:
    # 帽檐：整条深色，只有朝上的那面提亮，免得像一块黑板
    b = kit.face_bounds(*UV_CAP_BRIM, 9, 1, 2)
    kit.fill(rows, UV_CAP_BRIM[0], UV_CAP_BRIM[1],
             UV_CAP_BRIM[0] + 22, UV_CAP_BRIM[1] + 3, BRIM)
    _rect(rows, b, "up", BRIM_TOP)
    _rect(rows, b, "north", (44, 44, 56, 255))

    # 帽墙：深蓝 + 下沿一道金边；朝上/朝下的两面压暗（被子盖住 / 贴着头发）
    b = kit.face_bounds(*UV_CAP_BAND, 10, 2, 10)
    kit.fill(rows, UV_CAP_BAND[0], UV_CAP_BAND[1],
             UV_CAP_BAND[0] + 40, UV_CAP_BAND[1] + 12, NAVY)
    _rect(rows, b, "up", NAVY_DARK)
    _rect(rows, b, "down", NAVY_DARK)
    for face in ("east", "north", "west", "south"):
        x0, y0, x1, y1 = b[face]
        kit.fill(rows, x0, y1 - 1, x1, y1, GOLD)        # 帽墙下沿的金边

    # 帽冠：比帽墙再外扩 0.5（大檐帽的"檐"），底部一圈金环
    b = kit.face_bounds(*UV_CAP_TOP, 11, 3, 11)
    kit.fill(rows, UV_CAP_TOP[0], UV_CAP_TOP[1],
             UV_CAP_TOP[0] + 44, UV_CAP_TOP[1] + 14, CROWN_DARK)
    _rect(rows, b, "up", CROWN_TOP)
    _rect(rows, b, "down", CROWN_DARK)
    for face in ("east", "north", "west", "south"):
        x0, y0, x1, y1 = b[face]
        kit.fill(rows, x0, y0, x1, y1, CROWN)
        kit.fill(rows, x0, y1 - 1, x1, y1, GOLD)

    # 帽徽：金色方块，正面点一颗暗芯
    b = kit.face_bounds(*UV_CAP_BADGE, 4, 2, 1)
    kit.fill(rows, UV_CAP_BADGE[0], UV_CAP_BADGE[1],
             UV_CAP_BADGE[0] + 10, UV_CAP_BADGE[1] + 3, GOLD)
    for face in ("north", "south"):
        x0, y0, x1, y1 = b[face]
        kit.fill(rows, x0 + 1, y0, x1 - 1, y1, GOLD_DARK)


def paint_flag(rows) -> None:
    # 旗顶金饰
    b = kit.face_bounds(*UV_FLAG_FINIAL, 2, 2, 2)
    kit.fill(rows, UV_FLAG_FINIAL[0], UV_FLAG_FINIAL[1],
             UV_FLAG_FINIAL[0] + 8, UV_FLAG_FINIAL[1] + 4, GOLD)
    _rect(rows, b, "up", GOLD_LIGHT)

    # 挂带
    b = kit.face_bounds(*UV_FLAG_STRAP, 2, 2, 1)
    kit.fill(rows, UV_FLAG_STRAP[0], UV_FLAG_STRAP[1],
             UV_FLAG_STRAP[0] + 6, UV_FLAG_STRAP[1] + 3, LEATHER)
    for face in ("north", "south"):
        x0, y0, x1, y1 = b[face]
        kit.fill(rows, x0, y0 + 1, x0 + 1, y0 + 2, GOLD)

    # 旗杆：木色 + 每 5 像素一道深纹
    kit.fill(rows, UV_FLAG_POLE[0], UV_FLAG_POLE[1],
             UV_FLAG_POLE[0] + 4, UV_FLAG_POLE[1] + 25, WOOD)
    for j in range(0, 25, 5):
        kit.fill(rows, UV_FLAG_POLE[0], UV_FLAG_POLE[1] + j,
                 UV_FLAG_POLE[0] + 4, UV_FLAG_POLE[1] + j + 1, WOOD_DARK)

    # 旗面：猩红 + 上下金边 + 骨白骷髅
    b = kit.face_bounds(*UV_FLAG_CLOTH, 7, 9, 1)
    kit.fill(rows, UV_FLAG_CLOTH[0], UV_FLAG_CLOTH[1],
             UV_FLAG_CLOTH[0] + 16, UV_FLAG_CLOTH[1] + 10, CRIMSON_DARK)
    for face in ("up", "down"):
        _rect(rows, b, face, CRIMSON)
    for face in ("north", "south"):
        x0, y0, x1, y1 = b[face]
        kit.fill(rows, x0, y0, x1, y1, CRIMSON)
        kit.fill(rows, x0, y0, x1, y0 + 1, GOLD)       # 上压金边
        kit.fill(rows, x0, y1 - 1, x1, y1, GOLD)       # 下压金边
        kit.draw_text_pixels(rows, x0 + 1, y0 + 1, SKULL,
                             {"#": BONE, ".": CRIMSON})

    # 旗尾：猩红，底边故意咬掉几个像素做出撕裂
    b = kit.face_bounds(*UV_FLAG_TAIL, 4, 4, 1)
    kit.fill(rows, UV_FLAG_TAIL[0], UV_FLAG_TAIL[1],
             UV_FLAG_TAIL[0] + 10, UV_FLAG_TAIL[1] + 5, CRIMSON_DARK)
    for face in ("north", "south"):
        x0, y0, x1, y1 = b[face]
        kit.fill(rows, x0, y0, x1, y1, CRIMSON)
        kit.fill(rows, x0, y0, x1, y0 + 1, GOLD)
        for i in range(x0, x1, 2):
            kit.fill(rows, i, y1 - 1, i + 1, y1, (0, 0, 0, 0))


def paint_saber(rows) -> None:
    # 刀首 + 护手：金件，朝上的那面提亮
    b = kit.face_bounds(*UV_SABER_POMMEL, 1, 1, 1)
    kit.fill(rows, UV_SABER_POMMEL[0], UV_SABER_POMMEL[1],
             UV_SABER_POMMEL[0] + 4, UV_SABER_POMMEL[1] + 2, GOLD)
    _rect(rows, b, "up", GOLD_LIGHT)

    b = kit.face_bounds(*UV_SABER_GUARD, 3, 1, 3)
    kit.fill(rows, UV_SABER_GUARD[0], UV_SABER_GUARD[1],
             UV_SABER_GUARD[0] + 12, UV_SABER_GUARD[1] + 4, GOLD)
    _rect(rows, b, "up", GOLD_LIGHT)
    _rect(rows, b, "down", GOLD_DARK)

    # 护指（刀镡向外那道弧）：金件，朝外的那面提亮
    b = kit.face_bounds(*UV_SABER_BOW, 1, 5, 1)
    kit.fill(rows, UV_SABER_BOW[0], UV_SABER_BOW[1],
             UV_SABER_BOW[0] + 4, UV_SABER_BOW[1] + 6, GOLD)
    _rect(rows, b, "west", GOLD_LIGHT)

    # 握把：深皮革上隔一行缠一道金线（螺旋缠绕的错觉）
    b = kit.face_bounds(*UV_SABER_GRIP, 1, 4, 1)
    kit.fill(rows, UV_SABER_GRIP[0], UV_SABER_GRIP[1],
             UV_SABER_GRIP[0] + 4, UV_SABER_GRIP[1] + 5, GRIP)
    for face in ("east", "north", "west", "south"):
        x0, y0, x1, y1 = b[face]
        for y in range(y0, y1, 2):
            kit.fill(rows, x0, y, x1, y + 1, GRIP_WIRE)

    # 刀身：刀面（±z，两格宽）—— 一格刃口提亮、一格刀背略暗，做出"倒角"的观感；
    # 左右两条是刀锋（±x，一格厚），给中间调。
    b = kit.face_bounds(*UV_SABER_BLADE, 2, 9, 1)
    kit.fill(rows, UV_SABER_BLADE[0], UV_SABER_BLADE[1],
             UV_SABER_BLADE[0] + 6, UV_SABER_BLADE[1] + 10, STEEL)
    for face in ("north", "south"):
        x0, y0, x1, y1 = b[face]
        kit.fill(rows, x0, y0, x0 + 1, y1, STEEL_LIGHT)
    for face in ("east", "west"):
        x0, y0, x1, y1 = b[face]
        kit.fill(rows, x0, y0, x1, y1, STEEL_DARK)
    _rect(rows, b, "up", STEEL_DARK)
    _rect(rows, b, "down", STEEL_LIGHT)

    # 刀尖
    b = kit.face_bounds(*UV_SABER_TIP, 1, 1, 1)
    kit.fill(rows, UV_SABER_TIP[0], UV_SABER_TIP[1],
             UV_SABER_TIP[0] + 4, UV_SABER_TIP[1] + 2, STEEL_LIGHT)
    _rect(rows, b, "down", STEEL_DARK)


def commander_texture():
    """128x128：原版僵尸本体 + 补好的左肢 + 帽子/旗帜/军刀的贴图。"""
    _, _, rows = vanilla.base_texture(SIZE, left_arm_uv=UV_L_ARM, left_leg_uv=UV_L_LEG)
    paint_cap(rows)
    paint_flag(rows)
    paint_saber(rows)
    return SIZE, SIZE, rows


# ------------------------------------------------------------------ 组装

def commander_elements(zombie_pose: bool = False) -> list[dict]:
    arm_rot = (90, 0, 0) if zombie_pose else None
    v = vanilla
    els = [
        kit.element("head_box", v.HEAD_FROM, v.HEAD_TO, v.UV_HEAD, origin=v.PIVOT_HEAD),
        kit.element("hat", v.HEAD_FROM, v.HEAD_TO, v.UV_HAT, inflate=0.5,
                    origin=v.PIVOT_HEAD),
        kit.element("body_box", v.BODY_FROM, v.BODY_TO, v.UV_BODY, origin=v.PIVOT_BODY),
        kit.element("right_arm_box", v.R_ARM_FROM, v.R_ARM_TO, v.UV_R_ARM,
                    origin=v.PIVOT_R_ARM, rotation=arm_rot),
        kit.element("left_arm_box", v.L_ARM_FROM, v.L_ARM_TO, UV_L_ARM,
                    origin=v.PIVOT_L_ARM, rotation=arm_rot),
        kit.element("right_leg_box", v.R_LEG_FROM, v.R_LEG_TO, v.UV_R_LEG,
                    origin=v.PIVOT_R_LEG),
        kit.element("left_leg_box", v.L_LEG_FROM, v.L_LEG_TO, UV_L_LEG,
                    origin=v.PIVOT_L_LEG),
        # 大檐帽
        kit.element("cap_brim", CAP_BRIM[0], CAP_BRIM[1], UV_CAP_BRIM, origin=PIVOT_CAP),
        kit.element("cap_band", CAP_BAND[0], CAP_BAND[1], UV_CAP_BAND, origin=PIVOT_CAP),
        kit.element("cap_top", CAP_TOP[0], CAP_TOP[1], UV_CAP_TOP, origin=PIVOT_CAP),
        kit.element("cap_badge", CAP_BADGE[0], CAP_BADGE[1], UV_CAP_BADGE,
                    origin=PIVOT_CAP),
        # 背旗
        kit.element("flag_strap", FLAG_STRAP[0], FLAG_STRAP[1], UV_FLAG_STRAP,
                    origin=PIVOT_FLAG),
        kit.element("flag_pole", FLAG_POLE[0], FLAG_POLE[1], UV_FLAG_POLE,
                    origin=PIVOT_FLAG),
        kit.element("flag_finial", FLAG_FINIAL[0], FLAG_FINIAL[1], UV_FLAG_FINIAL,
                    origin=PIVOT_FLAG),
        kit.element("flag_cloth", FLAG_CLOTH[0], FLAG_CLOTH[1], UV_FLAG_CLOTH,
                    origin=PIVOT_FLAG),
        kit.element("flag_tail", FLAG_TAIL[0], FLAG_TAIL[1], UV_FLAG_TAIL,
                    origin=PIVOT_FLAG),
        # 军刀（挂在右手骨骼下）
        kit.element("saber_pommel", SABER_POMMEL[0], SABER_POMMEL[1], UV_SABER_POMMEL,
                    origin=PIVOT_SABER),
        kit.element("saber_grip", SABER_GRIP[0], SABER_GRIP[1], UV_SABER_GRIP,
                    origin=PIVOT_SABER),
        kit.element("saber_bow", SABER_BOW[0], SABER_BOW[1], UV_SABER_BOW,
                    origin=PIVOT_SABER),
        kit.element("saber_guard", SABER_GUARD[0], SABER_GUARD[1], UV_SABER_GUARD,
                    origin=PIVOT_SABER),
        kit.element("saber_blade", SABER_BLADE[0], SABER_BLADE[1], UV_SABER_BLADE,
                    origin=PIVOT_SABER),
        kit.element("saber_tip", SABER_TIP[0], SABER_TIP[1], UV_SABER_TIP,
                    origin=PIVOT_SABER),
    ]
    return els


def commander_tree() -> list:
    v = vanilla
    return [
        ("g", "root", (0, 0, 0), [
            ("g", "body", v.PIVOT_BODY, [
                ("e", "body_box"),
                ("g", "flag", PIVOT_FLAG, [
                    ("e", "flag_strap"), ("e", "flag_pole"), ("e", "flag_finial"),
                    ("e", "flag_cloth"), ("e", "flag_tail"),
                ]),
            ]),
            ("g", "head", v.PIVOT_HEAD, [
                ("e", "head_box"), ("e", "hat"),
                ("g", "cap", PIVOT_CAP, [
                    ("e", "cap_brim"), ("e", "cap_band"),
                    ("e", "cap_top"), ("e", "cap_badge"),
                ]),
            ]),
            ("g", "right_arm", v.PIVOT_R_ARM, [
                ("e", "right_arm_box"),
                ("g", "saber", PIVOT_SABER, [
                    ("e", "saber_pommel"), ("e", "saber_grip"), ("e", "saber_bow"),
                    ("e", "saber_guard"), ("e", "saber_blade"), ("e", "saber_tip"),
                ]),
            ]),
            ("g", "left_arm", v.PIVOT_L_ARM, [("e", "left_arm_box")]),
            ("g", "right_leg", v.PIVOT_R_LEG, [("e", "right_leg_box")]),
            ("g", "left_leg", v.PIVOT_L_LEG, [("e", "left_leg_box")]),
        ]),
    ]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--zombie-pose", action="store_true",
                    help="把僵尸的前伸手臂烘进几何（只当视觉参照；转换后会变成双倍旋转）")
    args = ap.parse_args()

    w, h, rows = commander_texture()
    png = kit.encode_png(w, h, rows)
    kit.write_png(OUT_TEXTURE, w, h, rows)

    doc = kit.build_doc(
        "zombie_commander", (w, h), commander_elements(args.zombie_pose), commander_tree(),
        kit.texture_entry(png, "zombie_commander.png", "", w, h))
    kit.save(OUT_MODEL, doc)

    print("写出 %s（%.0f KB）" % (OUT_MODEL, os.path.getsize(OUT_MODEL) / 1024))
    print("写出 %s（%.0f KB）" % (OUT_TEXTURE, os.path.getsize(OUT_TEXTURE) / 1024))
    print("提示：手臂默认**自然下垂**（僵尸前伸姿态由渲染代码加），%s"
          % ("本文件已改为烘焙姿态" if args.zombie_pose else "需要视觉参照可加 --zombie-pose"))
    kit.describe(doc)


if __name__ == "__main__":
    main()
