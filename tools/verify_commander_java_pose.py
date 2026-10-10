#!/usr/bin/env python3
"""把**生成的 Java 模型**反解回几何，与 bbmodel 的设计姿势逐立方体对照。

为什么需要这个
--------------
`convert_commander_model.py` 的输出是 Java 源码，编译能过就万事大吉 —— 但「骨骼自带的
rotation 被丢掉」这类 bug 恰恰是**能编译、能加载、日志一个字都不报**的。军刀那次就是这么
栽的：bbmodel 里 saber 绕 x 转了 105°（刀尖前指），进游戏后角度归零、刀身顺着小臂垂直
扎向地面，看着完全不像被手握着的。

所以这里做**反向解析**：从 Java 源码里把「部件树 + 每个盒子的绝对位置」读回来，再用
`preview_bbmodel` 渲一遍，和直接渲 bbmodel 的结果**逐个立方体比角点**。对不上就说明
转换链路丢了东西。

两个空间的换算（就是转换器用的那条，写成反向）：
    display = (x, 24 - y, z)      点（Bedrock/预览器的 y 向上；Java 的 y 向下）
    display = (-rx, ry, -rz)      旋转角（绕 x 的转角在 y 翻转下取反）
立方体尺寸/局部位移跟着取反，所以 from/to 的 y 要交换。

用法
----
    python tools/verify_commander_java_pose.py            # 对照 + 出对照图
    python tools/verify_commander_java_pose.py --views 180,270
"""

from __future__ import annotations

import argparse
import base64
import json
import math
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import preview_bbmodel as pv            # noqa: E402
from convert_archer_zombie import box_uv_region  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
JAVA = (ROOT / "src/main/java/com/hhy/dreamingfishcore/gameplay/zombie_system/"
        "boss/client/ZombieCommanderModel.java")
BBMODEL = ROOT / "tools" / "zombie_commander.bbmodel"
OUT = ROOT / "tools" / "commander_java_vs_bbmodel.png"

#: Java 绝对空间 -> 预览/设计空间
def to_display_point(x: float, y: float, z: float) -> tuple[float, float, float]:
    return (x, 24.0 - y, z)


PART_RE = re.compile(
    r'PartDefinition (\w+) = (\w+)\.addOrReplaceChild\("([^"]+)",\s*'
    r'CubeListBuilder\.create\(\),\s*'
    r'PartPose\.(offset|offsetAndRotation)\(([^)]*)\)\);')
CUBE_RE = re.compile(
    r'^(\s*)(\w+)\.addOrReplaceChild\("([^"]+)",\s*(.*),\s*PartPose\.ZERO\);$')
FLOATS = re.compile(r'-?\d+(?:\.\d+)?F')


def parse_java(path: Path):
    """返回 (parts, cubes)。

    parts: 名字 -> dict(var, name, parent, abs=(x,y,z) 的 display 绝对枢轴, rot=(度)) 
    cubes: [dict(parent_var, name, from/to(display 局部), tex, inflate, mirror)]
    """
    parts: dict[str, dict] = {}
    cubes: list[dict] = []

    for line in path.read_text(encoding="utf-8").splitlines():
        match = PART_RE.search(line)
        if match:
            var, parent_var, name, kind, args = match.groups()
            values = [float(v[:-1]) for v in FLOATS.findall(args)]
            # PartPose 的角度是**弧度**，bbmodel 是度 —— 别忘换算（忘了会得到 1.8° 而不是 105°）
            rot = [math.degrees(v) for v in values[3:6]] if kind == "offsetAndRotation" \
                else [0.0, 0.0, 0.0]
            parts[var] = {"var": var, "name": name, "parent": parent_var,
                          "local": values[:3], "rot": rot}
            continue
        match = CUBE_RE.match(line)
        if match:
            _pad, parent_var, name, builder = match.groups()
            tex = re.search(r"\.texOffs\((\d+), (\d+)\)", builder)
            box = re.search(r"\.addBox\(([^)]*)\)", builder)
            inflate = re.search(r"new CubeDeformation\((-?[\d.]+)F\)", builder)
            values = [float(v[:-1]) for v in FLOATS.findall(box.group(1))]
            cubes.append({
                "parent": parent_var, "name": name,
                "box": values[:6], "tex": (int(tex.group(1)), int(tex.group(2))),
                "inflate": float(inflate.group(1)) if inflate else 0.0,
                "mirror": ".mirror()" in builder,
            })
    return parts, cubes


def abs_origins(parts: dict) -> dict:
    """按 Java 语义把局部位移累加成绝对枢轴（再换算进 display 空间）。"""
    out: dict[str, tuple[float, float, float]] = {}

    def walk(var: str) -> tuple[float, float, float]:
        if var in out:
            return out[var]
        part = parts[var]
        parent = part["parent"]
        base = walk(parent) if parent in parts else (0.0, 0.0, 0.0)
        acc = tuple(base[i] + part["local"][i] for i in range(3))
        out[var] = acc
        return acc

    for var in parts:
        walk(var)
    return {var: to_display_point(*value) for var, value in out.items()}


def to_display_doc(parts: dict, cubes: list[dict]) -> dict:
    """合成一份「和 bbmodel 同构」的文档，交给 preview_bbmodel 渲染。"""
    abs_pt = abs_origins(parts)
    children: dict[str, list] = {var: [] for var in parts}
    roots: list[str] = []
    for var, part in parts.items():
        if part["parent"] in parts:
            children[part["parent"]].append(var)
        else:
            roots.append(var)

    groups = []
    for var, part in parts.items():
        x, y, z = abs_pt[var]
        rx, ry, rz = part["rot"]
        groups.append({"uuid": var, "name": part["name"],
                       "origin": [x, y, z],
                       # 反向换算：绕 x 的转角取反（y 翻转），y / z 保持 -> 见文件头
                       "rotation": [round(-rx, 6), round(ry, 6), round(-rz, 6)]})

    elements = []
    for cube in cubes:
        fx, fy, fz, sx, sy, sz = cube["box"]
        # Java 的 addBox 坐标是**相对父骨骼枢轴**的；bbmodel/预览器里的 from/to 是**全局**坐标。
        # 少了这一步「加上枢轴」，整棵树会整体平移一个骨骼偏移量（实测偏差 22~44 像素）。
        ox, oy, oz = abs_pt[cube["parent"]]
        inflate = cube["inflate"]
        frm = [ox + fx, oy - fy, oz + fz]
        to = [ox + fx + sx, oy - (fy + sy), oz + fz + sz]
        # y 翻转会让 from/to 的上下端点互换，归一成「from = 逐轴最小值」——
        # 不然盒子还是那个盒子，但两条对角线不同，逐角点比对会凭空虚报一个「盒子高」的偏差。
        frm, to = [min(a, b) for a, b in zip(frm, to)], [max(a, b) for a, b in zip(frm, to)]
        # 膨胀在 display 空间对称展开（和 bbmodel 的 inflate 语义一致）
        frm = [v - inflate for v in frm]
        to = [v + inflate for v in to]
        dx, dy, dz = (int(round(abs(v))) for v in (sx, sy, sz))
        u0, v0 = cube["tex"]
        # texOffs 是 box UV 的左上角；mirror() 在 Java 里只交换 UV 顶点顺序，
        # 采样用的矩形不变，所以这里两种都按标准展开写。
        regions = box_uv_region(u0, v0, dx, dy, dz)
        elements.append({
            "uuid": cube["name"], "name": cube["name"], "parent": cube["parent"],
            "from": frm, "to": to,
            "faces": {face: {"uv": list(rect), "texture": 0}
                      for face, rect in regions.items() if rect},
        })

    def node(var: str) -> dict:
        return {"uuid": var, "isOpen": True,
                "children": [node(c) for c in children[var]]
                            + [c["uuid"] for c in elements
                               if c["parent"] == var]}

    return {"groups": groups, "elements": elements,
            "outliner": [node(var) for var in roots], "resolution": {"width": 128,
                                                                    "height": 128}}


def canonical(name: str) -> str:
    """Java 里的部件名带 uuid 后缀，bbmodel 里没有 —— 比对时抹掉。"""
    return re.sub(r"_[0-9a-f]{8}$", "", name)


def corners(model) -> dict[str, list[tuple[float, float, float]]]:
    out: dict[str, list[tuple[float, float, float]]] = {}
    for cube in model["cubes"]:
        key = canonical(str(cube["name"]))
        a, b = cube["min"], cube["max"]
        out.setdefault(key, []).extend(
            pv.mat_apply(cube["matrix"], point)
            for point in ((a[0], a[1], a[2]), (b[0], b[1], b[2])))
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--views", default="180,270", help="方位角，逗号分隔（180=正面）")
    ap.add_argument("--size", type=int, default=420)
    args = ap.parse_args()

    parts, cubes = parse_java(JAVA)
    print(f"从 Java 反解：{len(parts)} 条骨骼、{len(cubes)} 个立方体")
    rotated = [p["name"] for p in parts.values() if any(abs(v) > 1e-9 for v in p["rot"])]
    print(f"  带自转的骨骼：{rotated or '（无）'}")

    bb = json.loads(BBMODEL.read_text(encoding="utf-8"))
    derived = to_display_doc(parts, cubes)

    design = corners(pv.build(bb, "ZYX"))
    rebuilt = corners(pv.build(derived, "ZYX"))

    print("\n逐个立方体比对（角点在同一个设计空间里的偏差，单位：像素）")
    bad = 0
    for key in sorted(set(design) | set(rebuilt)):
        if key not in design or key not in rebuilt:
            print(f"  [缺失] {key}: 只在 {'bbmodel' if key in design else 'Java'} 里")
            bad += 1
            continue
        delta = max(abs(a[i] - b[i])
                    for a, b in zip(sorted(design[key]), sorted(rebuilt[key]))
                    for i in range(3))
        mark = "OK " if delta < 0.05 else "！！"
        if delta >= 0.05:
            bad += 1
        print(f"  {mark} {key:18s} 最大偏差 {delta:7.3f}")
    print(f"\n结论：{'一致（转换没有丢东西）' if bad == 0 else f'{bad} 个部件对不上'}")

    views = [float(v) for v in args.views.split(",")]
    tex = pv.read_png_bytes(base64.b64decode(
        bb["textures"][0]["source"].split(",", 1)[1]))
    model_a = pv.build(bb, "ZYX")
    model_b = pv.build(derived, "ZYX")
    # 两套模型共用一份取景参数，比例才可比
    fit = pv.measure(model_a, "ZYX", views, args.size)
    _, _, buf_design = pv.render(model_a, tex, "ZYX", views, size=args.size, fit=fit)
    _, _, buf_java = pv.render(model_b, tex, "ZYX", views, size=args.size, fit=fit)

    size = args.size
    W, H = size * len(views), size * 2
    strip = [[(16, 16, 20, 255) for _ in range(W)] for _ in range(H)]
    for row, buf in enumerate((buf_design, buf_java)):
        for y in range(size):
            for x in range(W):
                strip[row * size + y][x] = buf[y][x]
    pv.write_png(OUT, W, H, strip)
    print(f"\n对照图：{OUT}（上排 = bbmodel 设计，下排 = 从生成的 Java 反解）")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
