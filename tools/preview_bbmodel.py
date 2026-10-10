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
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# PNG 读写与 UV 展开都放在 bbmodel_kit 里，和生成脚本共用一份，免得两边跑偏
from bbmodel_kit import read_png_bytes, write_png

MODEL = Path(r"C:\叽叽团\工程\模型\azombie.bbmodel")


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

# 上面每个面的四点顺序，对应贴图矩形的哪个角（TL/TR/BL/BR）。
# 这层对应关系不能瞎猜：写错了贴图会在面上左右翻转或上下颠倒，而灰模完全看不出来。
FACE_UV_ORDER = {
    "north": ("BL", "TL", "TR", "BR"),
    "east":  ("BL", "TL", "TR", "BR"),
    "south": ("BR", "BL", "TL", "TR"),
    "west":  ("BR", "BL", "TL", "TR"),
    # box UV 里 up / down 的矩形角点顺序是反的（Blockbench 的存法），
    # 等价于贴图在这两个面上转了 180°。
    "up":    ("TL", "BL", "BR", "TR"),
    "down":  ("BL", "BR", "TR", "TL"),
}
_ROT180 = {"TL": "BR", "TR": "BL", "BL": "TR", "BR": "TL"}

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
        return None      # 整面全透明：调用方会把这一面整个跳过，而不是画成灰色
    return tuple(min(255, int(c / n)) for c in total)


def uv_corners(rect, face):
    """把贴图矩形 (x0,y0,x1,y1) 的四个角，按 FACES 里那个面的点序排好。"""
    x0, y0, x1, y1 = rect
    corner = {"TL": (x0, y0), "TR": (x1, y0), "BL": (x0, y1), "BR": (x1, y1)}
    order = FACE_UV_ORDER[face]
    if face in ("up", "down"):
        order = tuple(_ROT180[c] for c in order)
    return [corner[c] for c in order]


def fill_quad_textured(buf, w, h, pts, uvs, tex, shade):
    """按像素把贴图糊到投影后的四边形上（三角形重心插值，够用了）。

    之前这里是「取整块贴图的平均色填一个色块」，于是带图案的部件（旗面、帽墙）
    只能渲染出一坨糊色。改成逐像素采样，离线就能看出图案对不对。
    """
    tw, th, trows = tex
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    x_lo = max(0, int(math.floor(min(xs))))
    x_hi = min(w - 1, int(math.ceil(max(xs))))
    y_lo = max(0, int(math.floor(min(ys))))
    y_hi = min(h - 1, int(math.ceil(max(ys))))
    tris = ((0, 1, 2), (0, 2, 3))
    for y in range(y_lo, y_hi + 1):
        py = y + 0.5
        for x in range(x_lo, x_hi + 1):
            px = x + 0.5
            hit = None
            for ia, ib, ic in tris:
                ax, ay = pts[ia][0], pts[ia][1]
                bx, by = pts[ib][0], pts[ib][1]
                cx, cy = pts[ic][0], pts[ic][1]
                den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
                if abs(den) < 1e-9:
                    continue
                wa = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den
                wb = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den
                wc = 1.0 - wa - wb
                if wa < -0.003 or wb < -0.003 or wc < -0.003:
                    continue
                u = wa * uvs[ia][0] + wb * uvs[ib][0] + wc * uvs[ic][0]
                v = wa * uvs[ia][1] + wb * uvs[ib][1] + wc * uvs[ic][1]
                hit = (u, v)
                break
            if hit is None:
                continue
            tx = min(tw - 1, max(0, int(hit[0])))
            ty = min(th - 1, max(0, int(hit[1])))
            rgba = trows[ty][tx]
            alpha = rgba[3] if len(rgba) >= 4 else 255
            if alpha < 10:
                continue                    # 透明像素：留出背景，不画
            buf[y][x] = tuple(min(255, int(rgba[i] * shade)) for i in range(3)) + (255,)


def measure(model, order, azimuths, size):
    """只算取景参数（每格的居中点 + 统一缩放），用来让动画分镜各帧比例一致。

    不这么做的话每帧都会各自重新取景 —— 动作里角色一抬手，那一格就整体缩小，
    分镜看起来像"忽大忽小"，根本没法比较。
    """
    quads = []
    for cube in model["cubes"]:
        a, b = cube["min"], cube["max"]
        for face, mk in FACES.items():
            if cube["faces"].get(face) is None:
                continue
            quads.append([mat_apply(cube["matrix"], p) for p in mk(a, b)])
    if not quads:
        return None
    xs = [p[0] for q in quads for p in q]
    ys = [p[1] for q in quads for p in q]
    zs = [p[2] for q in quads for p in q]
    cx = (min(xs) + max(xs)) / 2.0
    cy = (min(ys) + max(ys)) / 2.0
    cz = (min(zs) + max(zs)) / 2.0
    radius = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs), 1.0) / 2.0
    cam_z = radius * 2.6 + 8.0
    proj = 1.0 * size * 0.5
    boxes = []
    for az_deg in azimuths:
        az = math.radians(az_deg)
        px, py = [], []
        for q in quads:
            for (x, y, z) in q:
                dx, dy, dz = x - cx, y - cy, z - cz
                xr = dx * math.cos(az) - dz * math.sin(az)
                zr = dx * math.sin(az) + dz * math.cos(az)
                s = proj / max(zr + cam_z, 1.0)
                px.append(-s * xr)
                py.append(-s * dy)
        boxes.append(((min(px) + max(px)) / 2.0, (min(py) + max(py)) / 2.0,
                      max(px) - min(px), max(py) - min(py)))
    span_w = max(b[2] for b in boxes) or 1.0
    span_h = max(b[3] for b in boxes) or 1.0
    margin = 0.08 * size
    k = min((size - 2 * margin) / span_w, (size - 2 * margin) / span_h)
    return boxes, k


def render(model, tex, order, azimuths, size=460, scale=8.0, fov=1.0, fit=None):
    """azimuths: 视图方位角列表（度）。**180 = 正面（能看到脸），0 = 背面**。

    取景是自适应的：先按模型包围盒算出相机距离，投影完再统一缩放居中。
    早先写死了 y 偏移 -16 和相机距离 34，模型一高（比如加了旗杆）就会被切掉。
    给了 `fit`（`measure` 的返回值）就用它，不再自己算 —— 动画分镜靠这个保持比例一致。
    """
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
            if color is None:
                # 整面全透明 —— 这一面在游戏里根本不存在（原版僵尸的 hat 层就是这样）
                continue
            pts = [mat_apply(cube["matrix"], p) for p in mk(a, b)]
            nrm = NORMALS[face]
            r = cube["matrix"][0]
            n = tuple(sum(r[i][k] * nrm[k] for k in range(3)) for i in range(3))
            quads.append({"pts": pts, "color": color, "n": n, "uv": uv,
                          "uvc": uv_corners((min(uv[0], uv[2]), min(uv[1], uv[3]),
                                             max(uv[0], uv[2]), max(uv[1], uv[3])), face)})
    if not quads:
        return W, H, buf

    xs = [p[0] for q in quads for p in q["pts"]]
    ys = [p[1] for q in quads for p in q["pts"]]
    zs = [p[2] for q in quads for p in q["pts"]]
    cx = (min(xs) + max(xs)) / 2.0
    cy = (min(ys) + max(ys)) / 2.0
    cz = (min(zs) + max(zs)) / 2.0
    radius = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs), 1.0) / 2.0
    cam_z = radius * 2.6 + 8.0
    proj = fov * size * 0.5

    for q in quads:
        q["views"] = {}
        q["depths"] = {}
        for view in range(cols):
            az = math.radians(azimuths[view])
            out = []
            for (x, y, z) in q["pts"]:
                dx, dy, dz = x - cx, y - cy, z - cz
                xr = dx * math.cos(az) - dz * math.sin(az)
                zr = dx * math.sin(az) + dz * math.cos(az)
                depth = cam_z - zr            # 相机在 +z 侧朝 -z 看：zr 越大越近
                s = proj / max(depth, 1.0)
                # 屏幕 x 取负：相机在 -z 侧朝 +z 看时，模型的 +x 落在画面左边。
                # 不取负的话整张预览是**左右镜像**的 —— 实测把右臂涂蓝、渲染正视图，
                # 它会跑到画面右半，与游戏里看到的相反。
                out.append((-s * xr, -s * dy, depth))
            q["views"][view] = out
            nz = q["n"][0] * math.sin(az) + q["n"][2] * math.cos(az)
            q["depths"][view] = (sum(p[2] for p in out) / 4.0, nz)

    # 每个视图各自居中，但共用同一个缩放 —— 各视图大小一致才方便对比
    if fit is not None:
        boxes, k = fit
    else:
        boxes = []
        for view in range(cols):
            vx = [p[0] for q in quads for p in q["views"][view]]
            vy = [p[1] for q in quads for p in q["views"][view]]
            boxes.append(((min(vx) + max(vx)) / 2.0, (min(vy) + max(vy)) / 2.0,
                          max(vx) - min(vx), max(vy) - min(vy)))
        span_w = max(b[2] for b in boxes) or 1.0
        span_h = max(b[3] for b in boxes) or 1.0
        margin = 0.08 * size
        k = min((size - 2 * margin) / span_w, (size - 2 * margin) / span_h)

    light = (-0.35, 0.55, 0.76)
    for view in range(cols):
        ox = view * size
        bcx, bcy = boxes[view][0], boxes[view][1]
        ordered = sorted(range(len(quads)), key=lambda i: -quads[i]["depths"][view][0])
        for qi in ordered:
            q = quads[qi]
            _, camz = q["depths"][view]
            # 背面剔除：法线在相机空间朝后 -> 跳过
            if camz <= 0.02:
                continue
            pts = [((p[0] - bcx) * k + size / 2, (p[1] - bcy) * k + size / 2)
                   for p in q["views"][view]]
            lam = max(0.0, q["n"][0] * light[0] + q["n"][1] * light[1] + q["n"][2] * light[2])
            b = 0.35 + 0.75 * lam
            shifted = [(p[0] + ox, p[1]) for p in pts]
            fill_quad_textured(buf, W, H, shifted, q["uvc"], tex, b)
            c = tuple(min(255, int(v * b)) for v in q["color"])
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


IDENT_ROT = [[1.0 if i == j else 0.0 for j in range(3)] for i in range(3)]


# --------------------------------------------------------------------------
# 动画采样（读 NeoForge 的动画 JSON）
# --------------------------------------------------------------------------

def _lerp(a, b, u):
    return [a[i] + (b[i] - a[i]) * u for i in range(3)]


def _sample(keyframes, time: float, loop: bool, length: float):
    """按线性插值取某一时刻的通道值。loop 时把时间绕回 [0, length)。"""
    if loop and length > 0:
        time = time % length
    pts = [(kf["timestamp"], kf["target"]) for kf in keyframes]
    if time <= pts[0][0]:
        return list(pts[0][1])
    if time >= pts[-1][0]:
        if loop:
            # 循环动画的尾巴要接回开头，最后一帧之后按「末帧 -> 首帧」补一段
            t0, v0 = pts[-1]
            t1, v1 = pts[0][0] + length, pts[0][1]
            if t1 > t0:
                return _lerp(v0, v1, (time - t0) / (t1 - t0))
        return list(pts[-1][1])
    for i in range(len(pts) - 1):
        t0, v0 = pts[i]
        t1, v1 = pts[i + 1]
        if t0 <= time <= t1:
            return _lerp(v0, v1, 0.0 if t1 == t0 else (time - t0) / (t1 - t0))
    return list(pts[-1][1])


def load_anim(path, time: float) -> dict:
    """读一份动画 JSON，取 time 秒时的姿势，返回 {骨骼名: {"rotation":…, "position":…}}。

    JSON 里的数值是 **Java 模型语义**（原版 `degreeVec` 就是恒等，`posVec` 只翻 y），
    而预览是在 Bedrock 坐标里摆模型，所以要换回去：

      * rotation：`(-x, y, -z)` —— 两个空间的「前」都在 −z，但 y 一上一下，
        所以绕 x 的转向相反；绕 y 相同；绕 z 相反。
      * position：**恒等**。`posVec` 内部已经把 y 翻过一次（`Vector3f(x,-y,z)`），
        所以 JSON 里的 y 就是 Bedrock 的 y（正 = 向上），x / z 两边本来就同向。
    """
    doc = json.loads(Path(path).read_text(encoding="utf-8"))
    loop = bool(doc.get("loop"))
    length = float(doc.get("length") or 0.0)
    out: dict[str, dict[str, list]] = {}
    for entry in doc["animations"]:
        channel = str(entry["target"]).split(":")[-1]
        v = _sample(entry["keyframes"], time, loop, length)
        if channel == "rotation":
            v = [-v[0], v[1], -v[2]]
        elif channel == "position":
            v = [v[0], v[1], v[2]]
        else:
            continue
        slot = out.setdefault(entry["bone"], {})
        if channel in slot:
            slot[channel] = [slot[channel][i] + v[i] for i in range(3)]
        else:
            slot[channel] = v
    return out


# --------------------------------------------------------------------------
# 模型构建
# --------------------------------------------------------------------------

def build(bb, order, anim=None):
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
        pose = (anim or {}).get(g.get("name"))
        if pose:
            for i, axis in enumerate("xyz"):
                rot[axis] = rot.get(axis, 0.0) + pose.get("rotation", (0, 0, 0))[i]
        local = about_pivot(euler(rot, order), g.get("origin") or [0, 0, 0])
        if pose and "position" in pose:
            # 位置通道是「相对静止姿势的位移」，作用在旋转之前
            local = mat_mul((IDENT_ROT, list(pose["position"])), local)
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
    ap.add_argument("model", nargs="?", default=None,
                    help="要渲染的 bbmodel 路径；不给就用脚本里写死的那个默认路径")
    ap.add_argument("--order", default="ZYX", help="欧拉角应用顺序，如 ZYX / XYZ / ZXY")
    ap.add_argument("--out", default=None)
    ap.add_argument("--views", default="0,180,90,270", help="方位角度数，逗号分隔；180=正面、0=背面")
    ap.add_argument("--anim", default=None,
                    help="动画 JSON 路径（NeoForge 格式）；配合 --times 逐帧渲染")
    ap.add_argument("--times", default=None,
                    help="要渲染的时刻（秒），逗号分隔；给了就按每个时刻出一列")
    ap.add_argument("--size", type=int, default=460, help="单列边长（像素）")
    args = ap.parse_args()

    bb = json.loads((Path(args.model) if args.model else MODEL).read_text(encoding="utf-8"))
    src = bb["textures"][0]["source"]
    tex = read_png_bytes(base64.b64decode(src.split(",", 1)[1]))

    order = args.order.upper()
    out = Path(args.out) if args.out else Path(f"build/_bbmodel_preview_{args.order.lower()}.png")
    out.parent.mkdir(parents=True, exist_ok=True)

    if args.times:
        if not args.anim:
            raise SystemExit("--times 需要同时给 --anim")
        views = [float(v) for v in args.views.split(",")]
        frames = [float(t) for t in args.times.split(",")]
        # 先用**静止姿势**量一次取景，所有帧共用 —— 否则每帧各自缩放，分镜忽大忽小没法比。
        fit = measure(build(bb, order), order, views, args.size)
        cols = []
        for t in frames:
            model = build(bb, order, load_anim(args.anim, t))
            W, H, buf = render(model, tex, order, views, size=args.size, fit=fit)
            cols.append((t, W, H, buf))
        # 横向拼成一条分镜
        W = sum(c[1] for c in cols)
        H = max(c[2] for c in cols)
        strip = [[(20, 20, 24, 255) for _ in range(W)] for _ in range(H)]
        xoff = 0
        for _t, cw, ch, buf in cols:
            for y in range(ch):
                for x in range(cw):
                    strip[y][xoff + x] = buf[y][x]
            xoff += cw
        write_png(out, W, H, strip)
        print(f"\n已写出动画分镜：{out}")
        print("  时刻（秒）：" + "  ".join(f"{c[0]:g}" for c in cols))
        return

    model = build(bb, order)

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
    W, H, buf = render(model, tex, order, views, size=args.size)
    out.parent.mkdir(parents=True, exist_ok=True)
    write_png(out, W, H, buf)
    print(f"\n已写出预览：{out}   视图方位角={views}（180=正面）")


if __name__ == "__main__":
    main()
