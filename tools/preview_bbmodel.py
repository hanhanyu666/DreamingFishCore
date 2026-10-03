"""把 Blockbench 的 .bbmodel 直接渲染成 PNG，用来离线核对「源模型长什么样」。

为什么不靠游戏截图：截图受朝向、光照、镜头距离影响，而且模型在游戏里是被
转换脚本改过的。这里直接读 bbmodel（真值），按 Bedrock 的层级/旋转规则摆位，
所以渲染出来的就是 Blockbench 里看到的东西。

用法：
    python tools/preview_bbmodel.py [--order ZYX|XYZ|ZXY|...] [--out xx.png]

`--order` 用来试欧拉角的应用顺序（Bedrock 到底是哪一种，离线无法百分百确定，
所以做成可切换：哪种顺序下枪件是"拼成一把枪"而不是散开的，哪种就是对的）。
"""
from __future__ import annotations

import argparse
import base64
import json
import math
import struct
import zlib
from pathlib import Path

MODEL = Path(r"C:\叽叽团\工程\模型\azombie.bbmodel")

# --------------------------------------------------------------------------
# PNG
# --------------------------------------------------------------------------


def read_png_bytes(data: bytes):
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
    rows = [[tuple(out[y * stride + x * bpp: y * stride + x * bpp + bpp]) for x in range(w)]
            for y in range(h)]
    return w, h, rows


def write_png(path: Path, w: int, h: int, rows) -> None:
    raw = b"".join(b"\x00" + bytes(c for px in r for c in px) for r in rows)

    def chunk(tag: bytes, payload: bytes) -> bytes:
        return (struct.pack(">I", len(payload)) + tag + payload
                + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))

    path.write_bytes(b"\x89PNG\r\n\x1a\n"
                     + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 6))
                     + chunk(b"IEND", b""))


# --------------------------------------------------------------------------
# 线性代数（3x3 旋转 + 平移）
# --------------------------------------------------------------------------

def mat_mul(a, b):
    ra, ta = a
    rb, tb = b
    r = [[sum(ra[i][k] * rb[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    t = [sum(ra[i][k] * tb[k] for k in range(3)) + ta[i] for i in range(3)]
    return (r, t)


def mat_apply(m, p):
    r, t = m
    return tuple(sum(r[i][k] * p[k] for k in range(3)) + t[i] for i in range(3))


IDENT = ([[1.0 if i == j else 0.0 for j in range(3)] for i in range(3)], [0.0, 0.0, 0.0])


def rot_axis(axis: str, deg: float):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        r = [[1, 0, 0], [0, c, -s], [0, s, c]]
    elif axis == "y":
        r = [[c, 0, s], [0, 1, 0], [-s, 0, c]]
    else:
        r = [[c, -s, 0], [s, c, 0], [0, 0, 1]]
    return (r, [0.0, 0.0, 0.0])


def euler(rot, order: str):
    """按给定顺序依次应用各轴旋转（左乘 = 后应用）。order 形如 'ZYX' = 先 X 再 Y 再 Z。"""
    m = IDENT
    for axis in order:
        deg = rot.get(axis.lower(), 0.0)
        if abs(deg) > 1e-9:
            m = mat_mul(rot_axis(axis.lower(), deg), m)
    return m


def about_pivot(m, pivot):
    """把变换改成绕 pivot 旋转：T(p) · M · T(-p)。"""
    r, t = m
    return (r, [t[i] + pivot[i] - sum(r[i][k] * pivot[k] for k in range(3)) for i in range(3)])


# --------------------------------------------------------------------------
# 渲染
# --------------------------------------------------------------------------

FACES = {
    # 方向 -> 组成该面的 8 个角里用到的 4 个（以 min/max 组合表示）
    "east":  lambda a, b: [(b[0], a[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2])],
    "west":  lambda a, b: [(a[0], a[1], a[2]), (a[0], a[1], b[2]), (a[0], b[1], b[2]), (a[0], b[1], a[2])],
    "up":    lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], b[1], a[2])],
    "down":  lambda a, b: [(a[0], a[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    "north": lambda a, b: [(a[0], a[1], a[2]), (a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2])],
    "south": lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])],
}

NORMALS = {
    "east": (1, 0, 0), "west": (-1, 0, 0),
    "up": (0, 1, 0), "down": (0, -1, 0),
    "north": (0, 0, -1), "south": (0, 0, 1),
}


def sample_avg(tex, uv):
    w, h, rows = tex
    x1, y1, x2, y2 = uv
    xa, xb = sorted((int(round(x1)), int(round(x2))))
    ya, yb = sorted((int(round(y1)), int(round(y2))))
    total = [0, 0, 0]
    n = 0
    for y in range(max(0, ya), min(h, yb)):
        for x in range(max(0, xa), min(w, xb)):
            px = rows[y][x]
            if len(px) >= 4 and px[3] == 0:
                continue
            total[0] += px[0]
            total[1] += px[1]
            total[2] += px[2]
            n += 1
    if n == 0:
        return (150, 150, 150)
    return tuple(min(255, int(c / n)) for c in total)


def fill_quad(buf, w, h, pts, color):
    ys = [p[1] for p in pts]
    y0, y1 = max(0, int(math.floor(min(ys)))), min(h - 1, int(math.ceil(max(ys))))
    for y in range(y0, y1 + 1):
        xs = []
        for i in range(4):
            x1, y1v = pts[i]
            x2, y2v = pts[(i + 1) % 4]
            if (y1v > y) != (y2v > y):
                xs.append(x1 + (y - y1v) * (x2 - x1) / (y2v - y1v))
        if len(xs) < 2:
            continue
        xs.sort()
        a, b = int(math.ceil(min(xs))), int(math.floor(max(xs)))
        for x in range(max(0, a), min(w - 1, b) + 1):
            buf[y][x] = color


def render(model, tex, order, azimuths, size=460, scale=8.0, fov=0.85):
    """azimuths: 视图方位角列表（度）。0 = 从 +z 看（模型正前方）。"""
    cols = len(azimuths)
    W, H = size * cols, size
    buf = [[(28, 28, 32, 255) for _ in range(W)] for _ in range(H)]
    quads = []

    for cube in model["cubes"]:
        a, b = cube["min"], cube["max"]
        for face, mk in FACES.items():
            uv = cube["faces"].get(face)
            if uv is None:
                continue
            color = sample_avg(tex, uv)
            pts = [mat_apply(cube["matrix"], p) for p in mk(a, b)]
            nrm = NORMALS[face]
            r = cube["matrix"][0]
            n = tuple(sum(r[i][k] * nrm[k] for k in range(3)) for i in range(3))
            quads.append({"pts": pts, "color": color, "n": n})
    if not quads:
        return W, H, buf

    # 垂直/水平居中：模型 y 范围 0..32，x 用包围盒中心
    xs = [p[0] for q in quads for p in q["pts"]]
    shift_y = -16.0
    shift_x = (min(xs) + max(xs)) / 2.0
    for qi, q in enumerate(quads):
        for view in range(cols):
            az = math.radians(azimuths[view])
            out = []
            for (x, y, z) in q["pts"]:
                y += shift_y
                x -= shift_x
                xr = x * math.cos(az) - z * math.sin(az)
                zr = x * math.sin(az) + z * math.cos(az)
                depth = zr + 34.0            # 相机在 +z 侧
                s = fov * size * 0.5 / max(depth, 1.0)
                out.append((s * xr + size / 2, -s * y + size / 2, depth))
            q.setdefault("views", {})[view] = out
            nz = (q["n"][0] * math.sin(az) + q["n"][2] * math.cos(az))
            q.setdefault("depths", {})[view] = (sum(p[2] for p in out) / 4.0, nz)

    light = (-0.35, 0.55, 0.76)
    for view in range(cols):
        ox = view * size
        ordered = sorted(range(len(quads)), key=lambda i: -quads[i]["depths"][view][0])
        for qi in ordered:
            q = quads[qi]
            _, camz = q["depths"][view]
            pts = q["views"][view]
            # 背面剔除：法线在相机空间朝后 -> 跳过
            if camz <= 0.02:
                continue
            lam = max(0.0, q["n"][0] * light[0] + q["n"][1] * light[1] + q["n"][2] * light[2])
            b = 0.35 + 0.75 * lam
            c = tuple(min(255, int(v * b)) for v in q["color"])
            fill_quad(buf, W, H, [(p[0] + ox, p[1]) for p in pts], c + (255,))
            # 描边
            for i in range(4):
                x1, y1 = pts[i][0] + ox, pts[i][1]
                x2, y2 = pts[(i + 1) % 4][0] + ox, pts[(i + 1) % 4][1]
                steps = int(max(abs(x2 - x1), abs(y2 - y1))) + 1
                for t in range(steps + 1):
                    px = int(x1 + (x2 - x1) * t / steps)
                    py = int(y1 + (y2 - y1) * t / steps)
                    if 0 <= px < W and 0 <= py < H:
                        buf[py][px] = (max(0, c[0] - 70), max(0, c[1] - 70), max(0, c[2] - 70), 255)
    return W, H, buf


# --------------------------------------------------------------------------
# 模型构建
# --------------------------------------------------------------------------

def build(bb, order):
    groups = {str(g["uuid"]): g for g in bb.get("groups", [])}
    elements = {str(e["uuid"]): e for e in bb["elements"]}

    parent_of = {}

    def walk(node, parent):
        if isinstance(node, str):
            parent_of[node] = parent
            return
        for c in node.get("children", []):
            walk(c, str(node.get("uuid")))

    walk({"uuid": "ROOT", "children": bb["outliner"]}, None)

    # 骨骼矩阵（Bedrock 里 group 的 origin 就是旋转枢轴）
    def bone_matrix(uuid, depth=0):
        if uuid == "ROOT" or uuid not in groups:
            return IDENT
        g = groups[uuid]
        m = bone_matrix(parent_of.get(uuid) or "ROOT", depth + 1)
        rot = dict(zip("xyz", g.get("rotation") or [0, 0, 0]))
        local = about_pivot(euler(rot, order), g.get("origin") or [0, 0, 0])
        return mat_mul(m, local)

    cubes = []
    for e in bb["elements"]:
        uid = str(e["uuid"])
        parent = parent_of.get(uid)
        chain = bone_matrix(parent)
        a = list(e["from"])
        b = list(e["to"])
        inflate = e.get("inflate") or 0.0
        if inflate:
            a = [v - inflate for v in a]
            b = [v + inflate for v in b]
        m = chain
        if e.get("rotation"):
            rot = dict(zip("xyz", e["rotation"]))
            own = about_pivot(euler(rot, order), e["origin"])
            m = mat_mul(m, own)
        faces = {}
        for face, data in (e.get("faces") or {}).items():
            uv = data.get("uv")
            if uv:
                faces[face] = uv
        cubes.append({"min": tuple(a), "max": tuple(b), "faces": faces, "matrix": m,
                      "name": e.get("name"), "uuid": uid[:8],
                      "parent": groups.get(parent, {}).get("name")})
    return {"cubes": cubes, "groups": groups, "elements": elements}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--order", default="ZYX", help="欧拉角应用顺序，如 ZYX / XYZ / ZXY")
    ap.add_argument("--out", default=None)
    ap.add_argument("--views", default="0,180,90,270", help="方位角度数，逗号分隔；0=正前方")
    args = ap.parse_args()

    bb = json.loads(MODEL.read_text(encoding="utf-8"))
    src = bb["textures"][0]["source"]
    tex = read_png_bytes(base64.b64decode(src.split(",", 1)[1]))

    model = build(bb, args.order.upper())

    # 先报一下头部各面的 UV，用来确认「正面」到底是哪个方向
    for e in bb["elements"]:
        if e.get("name") == "head_box":
            print("head_box 各面 UV：")
            for face, data in (e.get("faces") or {}).items():
                print(f"   {face:6} {data.get('uv')}")
            break

    print(f"\n部件清单（order={args.order}）：")
    for c in model["cubes"]:
        size = tuple(round(c["max"][i] - c["min"][i], 2) for i in range(3))
        mn = tuple(round(v, 2) for v in c["min"])
        print(f"   {c['uuid']} {str(c['name']):>14} 父={str(c['parent']):>10} "
              f"min={mn} size={size}")

    views = [float(v) for v in args.views.split(",")]
    W, H, buf = render(model, tex, args.order.upper(), views)
    out = Path(args.out) if args.out else Path(f"build/_bbmodel_preview_{args.order.lower()}.png")
    out.parent.mkdir(parents=True, exist_ok=True)
    write_png(out, W, H, buf)
    print(f"\n已写出预览：{out}   视图方位角={views}（0=正前方）")


if __name__ == "__main__":
    main()
