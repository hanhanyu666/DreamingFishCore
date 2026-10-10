#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 Blockbench 的射手僵尸工程转成 DreamingFishCore 用的三类资源。

输入
----
  <模型目录>/azombie.bbmodel          Blockbench 工程（几何 + 内嵌贴图）
  <模型目录>/azombie.animation.json   Blockbench 导出的 Bedrock 动画

输出
----
  src/main/java/com/hhy/dreamingfishcore/gameplay/zombie_system/archer/client/ArcherZombieModel.java
  src/main/resources/assets/dreamingfishcore/neoforge/animations/entity/archer_zombie/{walk,shoot}.json
  src/main/resources/assets/dreamingfishcore/textures/entity/archer_zombie/archer_zombie.png

坐标转换
--------
Bedrock 用「y 向上、脚底为 0、面朝 +z」，原版 Java 模型用「y 向下、颈部为 0、面朝 -z」。
二者相差 ``(x, 24 - y, -z)``，写成矩阵是 ``diag(1, -1, -1)``——**这是绕 x 轴转 180° 的纯旋转**
（行列式为 +1），不是镜像。因此旋转角可以做共轭映射，结论是：

    xRot 保持原值，yRot 与 zRot 取反

欧拉角的应用顺序两边都是 ZYX（Bedrock 官方定义 == Java 的 rotationZYX），无需额外处理。
立方体自带的旋转在 Java 里无法表达（CubeListBuilder 只支持整条骨骼旋转），
所以脚本会把每个带旋转的立方体提升成一条子骨骼，pivot 取该立方体中心——数学等价。
"""

from __future__ import annotations

import base64
import json
import math
import re
import struct
import sys
import zlib
from pathlib import Path

MODEL_DIR = Path(r"C:\叽叽团\工程\模型")
PROJECT_DIR = Path(r"C:\叽叽团\工程\neoforge1.21.1模组开发")
BBMODEL = MODEL_DIR / "azombie.bbmodel"
ANIMATION_JSON = MODEL_DIR / "azombie.animation.json"

JAVA_OUT = (PROJECT_DIR / "src/main/java/com/hhy/dreamingfishcore/gameplay/"
            "zombie_system/archer/client/ArcherZombieModel.java")
ANIM_OUT_DIR = (PROJECT_DIR / "src/main/resources/assets/dreamingfishcore/"
                "neoforge/animations/entity/archer_zombie")
TEXTURE_OUT = (PROJECT_DIR / "src/main/resources/assets/dreamingfishcore/"
               "textures/entity/archer_zombie/archer_zombie.png")

MC_Y_SPAN = 24.0          # bedrock 的 24 像素对应 java 的 0
DEG = math.pi / 180.0


# ----------------------------------------------------------------------------
# PNG 读写（只处理我们需要的 8bit RGBA / filter 0-4）
# ----------------------------------------------------------------------------

def read_png(path: Path):
    data = path.read_bytes()
    pos, idat = 8, b""
    w = h = bd = ct = 0
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            w, h, bd, ct = struct.unpack(">IIBB", chunk[:10])
        elif tag == b"IDAT":
            idat += chunk
    raw = zlib.decompress(idat)
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    bpp = channels * (bd // 8)
    stride = w * bpp
    out = bytearray()
    prev = bytearray(stride)
    ptr = 0
    for _ in range(h):
        filt = raw[ptr]
        ptr += 1
        line = bytearray(raw[ptr:ptr + stride])
        ptr += stride
        if filt == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif filt == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filt == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif filt == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out += line
        prev = line
    pixels = []
    for y in range(h):
        row = []
        for x in range(w):
            start = y * stride + x * bpp
            row.append(tuple(out[start:start + bpp]))
        pixels.append(row)
    return w, h, ct, pixels


def write_png(path: Path, w: int, h: int, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for px in row:
            raw += bytes(px)
    def chunk(tag: bytes, payload: bytes) -> bytes:
        return (struct.pack(">I", len(payload)) + tag + payload
                + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))
    path.write_bytes(b"\x89PNG\r\n\x1a\n"
                     + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
                     + chunk(b"IEND", b""))


# ----------------------------------------------------------------------------
# 几何
# ----------------------------------------------------------------------------

def to_java_point(x, y, z):
    """bedrock 绝对坐标 -> java 绝对坐标。"""
    return (x, MC_Y_SPAN - y, -z)


def box_uv_region(u0, v0, dx, dy, dz):
    """标准 Minecraft box UV 展开：由起点与尺寸推出六个面的像素区域。

    注意 `down` 的位置：标准皮肤布局里，第一行是「顶面 + 底面」并排（高 dz），
    第二行才是东南西北四个侧面（高 dy）。曾经这里把 down 写到了侧面行的下方，
    于是一切正常模型都会被判成「底面被挪走了」，再被烘到错误的位置上去。
    判据是项目里那份真·Blockbench 导出的 bbmodel：head_box 的 down 是 [24,0,16,8]。
    """
    return {
        "east":  (u0, v0 + dz, u0 + dz, v0 + dz + dy),
        "north": (u0 + dz, v0 + dz, u0 + dz + dx, v0 + dz + dy),
        "west":  (u0 + dz + dx, v0 + dz, u0 + 2 * dz + dx, v0 + dz + dy),
        "south": (u0 + 2 * dz + dx, v0 + dz, u0 + 2 * dz + 2 * dx, v0 + dz + dy),
        "up":    (u0 + dz, v0, u0 + dz + dx, v0 + dz),
        "down":  (u0 + dz + dx, v0, u0 + dz + 2 * dx, v0 + dz),
    }


def fmt(value: float) -> str:
    text = f"{value:.4f}".rstrip("0").rstrip(".")
    return text if text not in ("", "-0") else "0"


class Converter:
    def __init__(self, doc: dict):
        self.doc = doc
        self.groups = {g["uuid"]: g for g in doc.get("groups", [])}
        self.elements = {e["uuid"]: e for e in doc["elements"]}
        self.baked_moves: list[tuple] = []   # (src_region, dst_region)
        self.skipped_faces: list[str] = []
        self.mirrored: list[str] = []        # 贴图被左右翻转（.mirror()）的部件

    # -- 树 -----------------------------------------------------------------

    def children_of(self, node):
        for child in node.get("children", []):
            yield child if isinstance(child, dict) else {"uuid": child, "children": []}

    def is_group(self, node) -> bool:
        return node.get("uuid") in self.groups

    # -- 生成 Java ----------------------------------------------------------

    def emit(self) -> tuple[str, list[str]]:
        lines: list[str] = []
        # Java 的 mesh 根节点位于「脚底上方 24 像素」处（原版 LivingEntityRenderer 里的
        # translate(0, -1.501, 0) 就是这 24 像素），所以根节点的 java 绝对坐标是 (0,0,0)。
        #
        # Blockbench 工程里通常还有一个原点在**脚底**的顶层 root 骨骼，它对应的正是 Java 的
        # mesh 根节点本身——多生成一层会让它下面所有骨骼整体偏移 -24，所以这里跳过它，
        # 直接把它的子骨骼挂到 Java 根上。
        children = list(self.doc["outliner"])
        if len(children) == 1:
            only = children[0]
            node = only if isinstance(only, dict) else {"uuid": only, "children": []}
            if self.is_group(node) and self.groups[node["uuid"]]["name"] == "root":
                children = list(node.get("children", []))
        self._emit_children(children, lines, "root", parent_abs=(0.0, 0.0, 0.0), indent=2)
        return "\n".join(lines), self.skipped_faces

    def _emit_children(self, outliner, lines, parent_var, parent_abs, indent):
        pad = "    " * indent
        for raw in outliner:
            # Blockbench 的 outliner 里，叶子节点可能写成裸 uuid 字符串
            node = raw if isinstance(raw, dict) else {"uuid": raw, "children": []}
            if self.is_group(node):
                group = self.groups[node["uuid"]]
                name = group["name"]
                origin = group["origin"]
                abs_pt = to_java_point(*origin)
                local = tuple(abs_pt[i] - parent_abs[i] for i in range(3))
                var = self._safe(name)
                lines.append(
                    f"{pad}PartDefinition {var} = {parent_var}.addOrReplaceChild("
                    f"\"{name}\", CubeListBuilder.create(), "
                    f"{self._group_pose(local, group.get('rotation'))});")
                self._emit_children(node.get("children", []), lines, var, abs_pt, indent)
            else:
                self._emit_element(self.elements[node["uuid"]], lines, parent_var, parent_abs, indent)

    def _emit_element(self, element, lines, parent_var, parent_abs, indent):
        pad = "    " * indent
        frm, to = element["from"], element["to"]
        size = tuple(to[i] - frm[i] for i in range(3))
        inflate = element.get("inflate", 0.0) or 0.0
        rotation = element.get("rotation")
        u0, v0 = element.get("uv_offset", [0, 0])

        # 部件名必须唯一：addOrReplaceChild 是「同名替换」，而 Blockbench 里多个立方体默认都叫
        # "cube"——直接用原名的话，后面的会把前面的整块顶掉，表现就是「模型少了一块」。
        tag = str(element["uuid"])[:8]
        part_name = f"{element['name']}_{tag}"
        var = self._safe(part_name)

        if rotation:
            # Java 的立方体不能自转：把它提升成一条子骨骼，pivot 取立方体中心。
            pivot_abs = to_java_point(*element["origin"])
            local = tuple(pivot_abs[i] - parent_abs[i] for i in range(3))
            jx, jy, jz = (a * DEG for a in self._to_java_deg(rotation))
            lines.append(
                f"{pad}// 带自身旋转的立方体 -> 提升为子骨骼（pivot 为其中心）")
            lines.append(
                f"{pad}PartDefinition {var} = {parent_var}.addOrReplaceChild("
                f"\"{part_name}\", CubeListBuilder.create(), "
                f"PartPose.offsetAndRotation({self._v(local[0])}F, {self._v(local[1])}F, "
                f"{self._v(local[2])}F, {fmt(jx)}F, {fmt(jy)}F, {fmt(jz)}F));")
            target_var, target_abs = var, pivot_abs
        else:
            target_var, target_abs = parent_var, parent_abs

        # java 绝对包围盒（y / z 翻转会让 min/max 对调）
        jf = to_java_point(frm[0], to[1], to[2])
        jt = to_java_point(to[0], frm[1], frm[2])
        rel = tuple(jf[i] - target_abs[i] for i in range(3))
        builder = (f"CubeListBuilder.create().texOffs({int(u0)}, {int(v0)})")
        # 缠在右臂上的那几个大块（把 4x4 的手臂整圈包住的那些）贴图要左右翻转。
        # mirror() 的语义刚好合适：它只交换盒子的 x 两端（这些块左右本来就对称 → 几何原地不动）
        # 并把 UV 顶点顺序反过来，也就是「盒子不动、贴图左右镜像」。灰模看不出正反，只有贴图能看出来。
        if self._needs_mirror(parent_var, size):
            builder += ".mirror()"
            self.mirrored.append(f"{element['name']}[{tag}]")
        if inflate:
            builder += f".addBox({self._v(rel[0])}F, {self._v(rel[1])}F, {self._v(rel[2])}F, "
            builder += (f"{fmt(size[0])}F, {fmt(size[1])}F, {fmt(size[2])}F, "
                        f"new CubeDeformation({fmt(inflate)}F))")
        else:
            builder += (f".addBox({self._v(rel[0])}F, {self._v(rel[1])}F, {self._v(rel[2])}F, "
                        f"{fmt(size[0])}F, {fmt(size[1])}F, {fmt(size[2])}F)")
        lines.append(f"{pad}{target_var}.addOrReplaceChild(\"{part_name}\", "
                     f"{builder}, PartPose.ZERO);")

        self._check_bottom_face(element, u0, v0, size)

    def _group_pose(self, local, rotation=None) -> str:
        """骨骼的 PartPose。

        **骨骼自身的 rotation 必须走 offsetAndRotation**：早期版本只读了 origin、
        把 rotation 整条丢掉，得到的是一条「位置对、角度归零」的骨骼 —— 表现是模型里
        某个零件明明挂在手上却朝错方向（指挥官那把军刀就是栽在这：bbmodel 里绕 x 转了
        105°，进游戏后刀身顺着小臂直直朝下、看着不像被握着）。
        这种丢失完全静默：不报错、不警告，只有对着模型看才发现。
        """
        base = f"{self._v(local[0])}F, {self._v(local[1])}F, {self._v(local[2])}F"
        if rotation and any(float(v) for v in rotation):
            jx, jy, jz = (a * DEG for a in self._to_java_deg(rotation))
            return f"PartPose.offsetAndRotation({base}, {fmt(jx)}F, {fmt(jy)}F, {fmt(jz)}F)"
        return f"PartPose.offset({base})"

    def _needs_mirror(self, parent_var: str, size) -> bool:
        """
        右臂上那些「把整条手臂包住」的大块，贴图需要左右翻转。

        判据用尺寸而不是 uuid：uuid 在 Blockbench 重新导出后会变，写死 uuid 会在下次重导时
        静默失效。而「缠在 4x4 的手臂上」等价于「x、z 两个方向都比手臂粗」——目前命中的正是
        6x2x6、6x2x6、5x4x6 这三块（手臂本身是 4x4，2x2x4 与 4x1x1 那几块不算）。
        """
        return parent_var == "right_arm" and size[0] >= 5.0 and size[2] >= 5.0

    def _check_bottom_face(self, element, u0, v0, size):
        """底面若与标准展开不符，就把它搬回去（标准位置空着时）或记下来。"""
        # 贴图区域永远是整数像素：from/to 允许带小数（为了错开共面），算区域时要先取整，
        # 否则 range() 会直接抛 "float object cannot be interpreted as an integer"。
        dx, dy, dz = (int(round(v)) for v in size)
        u0, v0 = int(round(u0)), int(round(v0))
        std = box_uv_region(u0, v0, dx, dy, dz)["down"]
        actual_raw = element["faces"]["down"]["uv"]
        actual = (min(actual_raw[0], actual_raw[2]), min(actual_raw[1], actual_raw[3]),
                  max(actual_raw[0], actual_raw[2]), max(actual_raw[1], actual_raw[3]))
        if tuple(std) == actual:
            return
        occupied = False
        for other in self.doc["elements"]:
            for face, payload in other["faces"].items():
                raw = payload["uv"]
                region = (min(raw[0], raw[2]), min(raw[1], raw[3]),
                          max(raw[0], raw[2]), max(raw[1], raw[3]))
                if region == actual:
                    continue
                if not (std[2] <= region[0] or region[2] <= std[0]
                        or std[3] <= region[1] or region[3] <= std[1]):
                    occupied = True
                    break
            if occupied:
                break
        name = f"{element.get('name')}[{str(element.get('uuid'))[:8]}]"
        if occupied:
            self.skipped_faces.append(f"{name} 标准底面被占用，底面将显示空白")
        else:
            self.baked_moves.append((actual, tuple(std), name))

    @staticmethod
    def _to_java_deg(rotation):
        """bedrock 角度 -> java 角度：x 保持，y / z 取反。"""
        rx, ry, rz = (float(v) for v in rotation)
        return (rx, -ry, -rz)

    @staticmethod
    def _safe(name: str) -> str:
        cleaned = "".join(ch if ch.isalnum() else "_" for ch in name)
        if cleaned and cleaned[0].isdigit():
            cleaned = "_" + cleaned
        return cleaned or "part"

    @staticmethod
    def _v(value: float) -> str:
        return fmt(float(value))

    # -- 贴图烘焙 -----------------------------------------------------------

    def bake(self, width: int, height: int, pixels):
        out = [row[:] for row in pixels]
        for src, dst, _name in self.baked_moves:
            sw, sh = int(round(src[2] - src[0])), int(round(src[3] - src[1]))
            dw, dh = int(round(dst[2] - dst[0])), int(round(dst[3] - dst[1]))
            sx, sy = int(round(src[0])), int(round(src[1]))
            dx, dy = int(round(dst[0])), int(round(dst[1]))
            if (sw, sh) != (dw, dh):
                print(f"  [跳过] 尺寸不匹配 {src} -> {dst}")
                continue
            for y in range(dh):
                for x in range(dw):
                    src_x, src_y = sx + x, sy + y
                    if 0 <= src_y < height and 0 <= src_x < width:
                        out[dy + y][dx + x] = pixels[src_y][src_x]
        return out


# ----------------------------------------------------------------------------
# 动画
# ----------------------------------------------------------------------------

def convert_animations(doc: dict) -> dict[str, dict]:
    out = {}
    for name, body in doc["animations"].items():
        short = name.split(".")[-1]              # animation.zombie.walk -> walk
        channels = []
        for bone, chans in body.get("bones", {}).items():
            for channel, payload in chans.items():
                if channel not in ("rotation", "position", "scale"):
                    continue
                frames = []
                for stamp, value in payload.items():
                    if isinstance(value, list):
                        vec = [float(v) for v in value]
                    elif isinstance(value, dict):
                        vec = [float(value.get(k, 0)) for k in ("x", "y", "z")]
                    else:
                        continue
                    if channel == "rotation":
                        vec = [vec[0], -vec[1], -vec[2]]     # 坐标变换的共轭映射
                    elif channel == "position":
                        # 位移只翻 z：NeoForge 的 KeyframeAnimations.posVec 自己会把 y 取反
                        # （new Vector3f(x, -y, z)），我们再翻一次就翻回去了。
                        vec = [vec[0], vec[1], -vec[2]]
                    elif channel == "scale":
                        vec = [v - 1.0 for v in vec]         # java 的 scale 以 1 为基准
                    frames.append({
                        "timestamp": float(stamp),
                        "target": [round(v, 5) for v in vec],
                        "interpolation": "minecraft:linear",
                    })
                if not frames:
                    continue
                channels.append({
                    "bone": bone,
                    "target": f"minecraft:{channel}",
                    "keyframes": sorted(frames, key=lambda f: f["timestamp"]),
                })
        out[short] = {
            "length": float(body.get("animation_length", 1.0)),
            "loop": bool(body.get("loop", False)),
            "animations": channels,
        }
    return out


# ----------------------------------------------------------------------------

JAVA_TEMPLATE = '''package com.hhy.dreamingfishcore.gameplay.zombie_system.archer.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieEntity;
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
 * 射手僵尸的模型：由 {@code tools/convert_archer_zombie.py} 从作者的 Blockbench 工程生成，
 * <b>不要手改本文件</b>——改模型请改 bbmodel 后重跑脚本。
 *
 * <p>几何来自 {@code azombie.bbmodel}，两个动画来自资源包 JSON
 * （{@code neoforge/animations/entity/archer_zombie/}），所以调动画不需要重新编译。</p>
 *
 * <p>坐标约定：Bedrock 是「y 向上、脚底 0、面朝 +z」，原版 Java 是「y 向下、颈部 0、面朝 -z」，
 * 两者相差 {@code (x, 24-y, -z)} —— 这是绕 x 轴 180° 的纯旋转，所以骨骼偏移可直接换算、
 * 旋转角按「x 保持、y/z 取反」映射。</p>
 */
public class ArcherZombieModel extends HierarchicalModel<ArcherZombieEntity> {
    private static final ResourceLocation WALK_ID =
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "archer_zombie/walk");
    private static final ResourceLocation SHOOT_ID =
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "archer_zombie/shoot");

    /**
     * 走路与射击动画。
     *
     * <p>这里必须持有 {@link AnimationHolder} 本身，而不是在类加载时 {@code .get()} 出
     * {@code AnimationDefinition} 缓存成常量——动画是**资源重载之后**才绑定的，
     * 类加载那一刻拿到的只会是空动画，表现就是「模型僵住、永远不播动画」。
     * holder 每次重载都会被重新绑定，所以要在每帧取。</p>
     */
    private static final AnimationHolder WALK = getAnimation(WALK_ID);
    private static final AnimationHolder SHOOT = getAnimation(SHOOT_ID);

    /**
     * 走路动画的两个驱动系数，取值与原版 {@code HumanoidModel} 一致
     * （它就是 {@code animateWalk(WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F)}）。
     *
     * <p>第一个系数把 {@code limbSwing} 映射成动画时间轴的推进速度（{@code limbSwing * 50 * 系数}）：
     * 配小了腿摆得比实际移动慢，看上去就是在"滑步"。第二个是摆幅系数。</p>
     */
    private static final float WALK_SPEED = 2.0F;
    private static final float WALK_SCALE = 2.5F;
    private static final float SHOOT_SPEED = 1.0F;

    private final ModelPart root;
    private final ModelPart head;

    public ArcherZombieModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition root = meshdefinition.getRoot();
{geometry}
        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(
            ArcherZombieEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        // 走路：由肢体摆动驱动循环动画（原版 animateWalk 会把 limbSwing 映射到动画时间轴）。
        this.animateWalk(WALK, limbSwing, limbSwingAmount, WALK_SPEED, WALK_SCALE);
        // 射击：抬手蓄力的整个过程播一遍单次动画，播完停住。
        this.animate(entity.shootAnimationState, SHOOT, ageInTicks, SHOOT_SPEED);

        // 头部跟随视线。这两个角度是原版模型空间（y 向下）的，直接沿用原版约定。
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
    }
}
'''


def main() -> int:
    for path in (BBMODEL, ANIMATION_JSON):
        if not path.exists():
            print(f"缺少输入文件：{path}", file=sys.stderr)
            return 1

    doc = json.loads(BBMODEL.read_text(encoding="utf-8"))
    print(f"读入 {BBMODEL.name}：{len(doc['elements'])} 个立方体、"
          f"{len(doc.get('groups', []))} 条骨骼、{len(doc.get('animations', []))} 个动画")

    converter = Converter(doc)
    geometry, skipped = converter.emit()

    # 自检：同一父骨骼下的部件名必须唯一。addOrReplaceChild 是「同名替换」语义，
    # Blockbench 里多个立方体默认都叫 "cube"，重名会让后面的把前面的顶掉（模型缺块）。
    seen = {}
    duplicated = []
    for parent, part in re.findall(r'(\w+)\.addOrReplaceChild\("([^"]+)"', geometry):
        key = (parent, part)
        if key in seen:
            duplicated.append(f"{parent}/{part}")
        seen[key] = True
    if duplicated:
        print(f"[错误] 部件重名会导致模型缺块：{duplicated}", file=sys.stderr)
        return 2
    cube_count = len(re.findall(r'\.addBox\(', geometry))
    print(f"  自检通过：{cube_count} 个立方体、{len(seen)} 个部件名，无重名")

    JAVA_OUT.parent.mkdir(parents=True, exist_ok=True)
    JAVA_OUT.write_text(JAVA_TEMPLATE.replace("{geometry}", geometry), encoding="utf-8")
    print(f"写出几何：{JAVA_OUT.relative_to(PROJECT_DIR)}")
    print(f"  底面搬回标准位置：{len(converter.baked_moves)} 个")
    for _src, _dst, name in converter.baked_moves:
        print(f"    - {name}")
    print(f"  贴图左右翻转（.mirror()）：{len(converter.mirrored)} 个")
    for _name in converter.mirrored:
        print(f"    - {_name}")
    for note in skipped:
        print(f"  [注意] {note}")

    # 贴图
    src = doc["textures"][0]["source"]
    raw = base64.b64decode(src.split(",", 1)[1])
    tmp = PROJECT_DIR / "build/_azombie_raw.png"
    tmp.parent.mkdir(parents=True, exist_ok=True)
    tmp.write_bytes(raw)
    width, height, _ct, pixels = read_png(tmp)
    baked = converter.bake(width, height, pixels)
    TEXTURE_OUT.parent.mkdir(parents=True, exist_ok=True)
    write_png(TEXTURE_OUT, width, height, baked)
    tmp.unlink(missing_ok=True)
    print(f"写出贴图：{TEXTURE_OUT.relative_to(PROJECT_DIR)}（{width}x{height}）")

    # 动画
    animations = convert_animations(json.loads(ANIMATION_JSON.read_text(encoding="utf-8")))
    ANIM_OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, payload in animations.items():
        out = ANIM_OUT_DIR / f"{name}.json"
        out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"写出动画：{out.relative_to(PROJECT_DIR)}"
              f"（{len(payload['animations'])} 条通道，loop={payload['loop']}，"
              f"{payload['length']}s）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
