#!/usr/bin/env python3
"""拼装 Blockbench `.bbmodel`（bedrock 格式）的公共零件。

字段名与取值**照抄**项目里那个真·Blockbench 导出的
`src/main/resources/assets/dreamingfishcore/bbmodel/zombie.bbmodel`：
自己猜出来的结构不是打不开，就是贴图错位（这个坑已经踩过一次）。

坐标约定
--------
Bedrock / Blockbench：y 轴**向上**、原点在脚底、模型正面朝**-z**。
Java 模型空间：y 轴**向下**、原点在脚底上方 24 像素、正面朝 +z。
换算就是 `y_java = 24 - y_bedrock`、`z_java = -z_bedrock`，x 两边相同。

box UV
------
一个 (w,h,d) 的立方体占 `2*(w+d)` 宽、`h+d` 高的贴图区域，六个面按下图摆放；
**up / down 两个面的 UV 角点顺序是反的**（起点在右下），这是 Blockbench 的写法，
按常序写会让顶面/底面的贴图整体转 180°：

    +---------+---------+  <- v
    |  up     |  down   |
    +----+----+----+----+
    | e  | n  | w  | s  |
    +----+----+----+----+
"""

from __future__ import annotations

import base64
import json
import struct
import uuid as _uuid
import zlib
from pathlib import Path

FORMAT_VERSION = "5.0"


# ---------------------------------------------------------------- PNG 读写

def read_png_bytes(data: bytes):
    """解出 (宽, 高, rows)；rows[y][x] 是每像素的原始通道元组（可能是 3 或 4 字节）。"""
    pos = 8
    idat = b""
    w = h = bd = ct = 0
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + ln]
        pos += 12 + ln
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
    p = 0
    for _ in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if f == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out += line
        prev = line
    return w, h, [[tuple(out[y * stride + x * bpp: y * stride + x * bpp + bpp])
                   for x in range(w)] for y in range(h)]


def read_png(path) -> tuple[int, int, list]:
    return read_png_bytes(Path(path).read_bytes())


def encode_png(w: int, h: int, rows) -> bytes:
    """把像素矩阵编成 PNG 字节（bbmodel 里的贴图是内嵌 base64，用得到这步）。"""
    raw = b"".join(b"\x00" + bytes(c for px in r for c in px) for r in rows)

    def chunk(tag: bytes, payload: bytes) -> bytes:
        return (struct.pack(">I", len(payload)) + tag + payload
                + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 6))
            + chunk(b"IEND", b""))


def write_png(path, w: int, h: int, rows) -> None:
    Path(path).write_bytes(encode_png(w, h, rows))


# ---------------------------------------------------------------- 画布操作

def blank(w: int, h: int, color=(0, 0, 0, 0)) -> list:
    return [[color for _ in range(w)] for _ in range(h)]


def fill(rows, x0: int, y0: int, x1: int, y1: int, color) -> None:
    """左闭右开的矩形填充；超出画布的部分自动裁掉。"""
    h = len(rows)
    w = len(rows[0])
    for y in range(max(0, y0), min(h, y1)):
        for x in range(max(0, x0), min(w, x1)):
            rows[y][x] = color


def flip_blit(dst, src, sx: int, sy: int, sw: int, sh: int, dx: int, dy: int) -> None:
    """把 src 的矩形**水平翻转**后贴到 dst —— 做左肢贴图用它（等价于 .mirror()）。"""
    for j in range(sh):
        for i in range(sw):
            dst[dy + j][dx + (sw - 1 - i)] = src[sy + j][sx + i]


def box_blit(dst, src, sx: int, sy: int, sw: int, sh: int, dx: int, dy: int) -> None:
    """原样搬运一块矩形。"""
    for j in range(sh):
        for i in range(sw):
            dst[dy + j][dx + i] = src[sy + j][sx + i]


def draw_text_pixels(rows, x0: int, y0: int, art: list[str], palette: dict) -> None:
    """按字符画逐像素落色；art 里 '.' 表示跳过（保留底色）。"""
    for j, line in enumerate(art):
        for i, ch in enumerate(line):
            if ch == ".":
                continue
            rows[y0 + j][x0 + i] = palette[ch]


# ---------------------------------------------------------------- UV 展开

def faces_for(u: int, v: int, w: int, h: int, d: int) -> dict:
    """标准 box UV 展开，角点顺序与 Blockbench 一致（up/down 是反的）。"""
    return {
        "east":  [u, v + d, u + d, v + d + h],
        "north": [u + d, v + d, u + d + w, v + d + h],
        "west":  [u + d + w, v + d, u + d + w + d, v + d + h],
        "south": [u + d + w + d, v + d, u + d + w + d + w, v + d + h],
        "up":    [u + d + w, v + d, u + d, v],
        "down":  [u + d + w + w, v, u + d + w, v + d],
    }


def face_bounds(u: int, v: int, w: int, h: int, d: int) -> dict:
    """同样的展开，但每个面给成 (x0, y0, x1, y1) 的归一化矩形（x0<x1、y0<y1）。

    贴图烘焙要按矩形搬运，角点顺序反着来会翻错方向，所以单独给一份。
    """
    out = {}
    for face, pts in faces_for(u, v, w, h, d).items():
        xs, ys = sorted((pts[0], pts[2])), sorted((pts[1], pts[3]))
        out[face] = (xs[0], ys[0], xs[1], ys[1])
    return out


def region_size(w: int, h: int, d: int) -> tuple[int, int]:
    """(w,h,d) 立方体占用的贴图区域大小。"""
    return 2 * (w + d), h + d


# ---------------------------------------------------------------- 文档组装

def _uid() -> str:
    return str(_uuid.uuid4())


def element(name: str, frm, to, uv, *, inflate: float = 0.0,
            rotation=None, origin=None, color: int = 0, uuid: str | None = None) -> dict:
    """一个立方体。尺寸必须是整数，否则 box UV 展开对不上。"""
    size = [to[i] - frm[i] for i in range(3)]
    for i, axis in enumerate("xyz"):
        if abs(size[i] - round(size[i])) > 1e-6:
            raise ValueError(f"{name}: {axis} 方向尺寸 {size[i]} 不是整数，box UV 对不上")
    w, h, d = (round(s) for s in size)
    u, v = uv
    el = {
        "name": name,
        "box_uv": True,
        "render_order": "default",
        "locked": False,
        "export": True,
        "scope": 0,
        "allow_mirror_modeling": True,
        "from": [round(x, 2) for x in frm],
        "to": [round(x, 2) for x in to],
        "autouv": 0,
        "color": color,
        "origin": [round(x, 2) for x in (origin if origin is not None else frm)],
    }
    if u or v:
        el["uv_offset"] = [u, v]
    if rotation and any(abs(r) > 1e-9 for r in rotation):
        el["rotation"] = [round(r, 2) for r in rotation]
    if inflate:
        el["inflate"] = round(inflate, 2)
    el["faces"] = {face: {"uv": pts, "texture": 0}
                   for face, pts in faces_for(u, v, w, h, d).items()}
    el["type"] = "cube"
    el["uuid"] = uuid or _uid()
    return el


def group(name: str, origin, uuid: str | None = None) -> dict:
    return {
        "name": name,
        "uuid": uuid or _uid(),
        "export": True,
        "locked": False,
        "scope": 0,
        "selected": False,
        "visibility": True,
        "_static": {"properties": {}, "temp_data": {}},
        "origin": [round(x, 2) for x in origin],
        "rotation": [0, 0, 0],
        "bedrock_binding": "",
        "color": 0,
        "children": [],
        "reset": False,
        "shade": True,
        "mirror_uv": False,
        "autouv": 0,
        "isOpen": True,
        "primary_selected": False,
    }


def texture_entry(png_bytes: bytes, name: str, relative_path: str,
                  width: int, height: int) -> dict:
    return {
        "name": name,
        "relative_path": relative_path,
        "folder": "",
        "namespace": "",
        "id": "0",
        "group": "",
        "scope": 0,
        "width": width,
        "height": height,
        "uv_width": width,
        "uv_height": height,
        "particle": False,
        "use_as_default": False,
        "layers_enabled": False,
        "sync_to_project": "",
        "file_format": "png",
        "render_mode": "default",
        "render_sides": "auto",
        "wrap_mode": "limited",
        "pbr_channel": "color",
        "fps": 7,
        "frame_time": 1,
        "frame_order_type": "loop",
        "frame_order": "",
        "frame_interpolate": False,
        "visible": True,
        "internal": True,
        "saved": True,
        "uuid": _uid(),
        "source": "data:image/png;base64," + base64.b64encode(png_bytes).decode("ascii"),
    }


def build_doc(name: str, resolution: tuple[int, int], elements: list,
              tree: list, texture: dict) -> dict:
    """把立方体 + 骨骼树拼成一份完整文档。tree 是嵌套结构：

        ("g", 组名, [x,y,z], [子节点...])
        ("e", 元素名)

    顶层约定放一个名叫 `root` 的组 —— 项目的转换脚本靠它对齐 Java 的 mesh 根节点。
    """
    groups: list[dict] = []
    by_name = {el["name"]: el["uuid"] for el in elements}

    def walk(node):
        """返回 outliner 节点：立方体是裸 uuid 字符串，骨骼是带 children 的字典。"""
        if node[0] == "e":
            if node[1] not in by_name:
                raise KeyError(f"元素 {node[1]} 不在 elements 里")
            return by_name[node[1]]
        _, gname, origin, children = node
        g = group(gname, origin)
        groups.append(g)
        kids = [walk(child) for child in children]
        g["children"] = [k if isinstance(k, str) else k["uuid"] for k in kids]
        return {"uuid": g["uuid"], "isOpen": True, "children": kids}

    outliner = [walk(node) for node in tree]

    return {
        "meta": {"format_version": FORMAT_VERSION, "model_format": "bedrock",
                 "box_uv": True},
        "name": name,
        "model_identifier": name,
        "visible_box": [1, 1, 1],
        "variable_placeholders": "",
        "multi_file_ruleset": "",
        "variable_placeholder_buttons": [],
        "bedrock_animation_mode": "entity",
        "timeline_setups": [],
        "unhandled_root_fields": {},
        "resolution": {"width": resolution[0], "height": resolution[1]},
        "elements": elements,
        "groups": groups,
        "outliner": outliner,
        "textures": [texture],
    }


def save(path, doc) -> None:
    Path(path).write_text(json.dumps(doc, ensure_ascii=False, separators=(",", ":")),
                          encoding="utf-8")


def describe(doc) -> None:
    """打印一份人类可读的清单，方便对照检查。"""
    print("%s：%d 个立方体、%d 条骨骼、贴图 %dx%d"
          % (doc["name"], len(doc["elements"]), len(doc["groups"]),
             doc["resolution"]["width"], doc["resolution"]["height"]))
    gname = {g["uuid"]: g["name"] for g in doc["groups"]}
    parent = {}

    def walk(node, p):
        if isinstance(node, str):
            parent[node] = p
            return
        for c in node.get("children", []):
            walk(c, node.get("uuid"))

    for node in doc["outliner"]:
        walk(node, None)
    for el in doc["elements"]:
        w = round(el["to"][0] - el["from"][0])
        h = round(el["to"][1] - el["from"][1])
        d = round(el["to"][2] - el["from"][2])
        print("  %-13s 挂在 %-10s from %-20s size (%2d,%2d,%2d)%s"
              % (el["name"], gname.get(parent.get(el["uuid"]), "-"),
                 str(el["from"]), w, h, d,
                 "  rot=%s" % el["rotation"] if "rotation" in el else ""))
