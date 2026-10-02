"""生成区域提示（明信片横幅）的风景剪影贴图。

每张贴图都是白色 + 透明度的剪影遮罩，游戏里按区域、时间着色后分层叠放：
远景（山、丘陵、沙丘、海岛）、中景（树林、村庄、灯塔……）、前景地面。
远景与中景贴图横向无缝平铺，游戏里可以随机取一段并缓慢平移。

运行：python tools/generate_region_scenes.py [--preview 输出目录]
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'dreamingfishcore', 'textures', 'gui', 'region')
SS = 4
W = 1280
H = 160
GROUND_H = 64

# 中景里需要在游戏中再加动态效果的位置（u、v 均为 0~1 的贴图坐标），会写进 anchors.json 方便核对
ANCHORS = {}


class Mask:
    """超采样绘制的剪影遮罩；tile=True 时每个图形在左右各画一份，保证横向无缝。"""

    def __init__(self, w, h, tile=True):
        self.w, self.h, self.tile = w, h, tile
        self.img = Image.new('L', (w * SS, h * SS), 0)
        self.d = ImageDraw.Draw(self.img)

    def _shifts(self):
        return (-self.w, 0, self.w) if self.tile else (0,)

    def poly(self, pts, fill=255):
        for s in self._shifts():
            self.d.polygon([((x + s) * SS, y * SS) for x, y in pts], fill=fill)

    def ellipse(self, cx, cy, rx, ry, fill=255):
        for s in self._shifts():
            self.d.ellipse([(cx - rx + s) * SS, (cy - ry) * SS, (cx + rx + s) * SS, (cy + ry) * SS], fill=fill)

    def circle(self, cx, cy, r, fill=255):
        self.ellipse(cx, cy, r, r, fill)

    def rect(self, x0, y0, x1, y1, fill=255):
        x0, x1 = min(x0, x1), max(x0, x1)
        y0, y1 = min(y0, y1), max(y0, y1)
        if x1 - x0 < 0.3 or y1 - y0 < 0.3:
            return
        for s in self._shifts():
            self.d.rectangle([(x0 + s) * SS, y0 * SS, (x1 + s) * SS - 1, y1 * SS - 1], fill=fill)

    def line(self, pts, width, fill=255):
        """圆头粗线；width 可以是数或与 pts 等长的列表（逐段变细）。"""
        widths = width if isinstance(width, (list, tuple)) else [width] * len(pts)
        for s in self._shifts():
            for i in range(len(pts) - 1):
                (x0, y0), (x1, y1) = pts[i], pts[i + 1]
                w0, w1 = widths[i], widths[i + 1]
                # 用多边形画梯形段，再在端点补圆，粗细平滑过渡
                dx, dy = x1 - x0, y1 - y0
                length = math.hypot(dx, dy) or 1.0
                nx, ny = -dy / length, dx / length
                quad = [(x0 + nx * w0 / 2, y0 + ny * w0 / 2), (x1 + nx * w1 / 2, y1 + ny * w1 / 2),
                        (x1 - nx * w1 / 2, y1 - ny * w1 / 2), (x0 - nx * w0 / 2, y0 - ny * w0 / 2)]
                self.d.polygon([((x + s) * SS, y * SS) for x, y in quad], fill=fill)
                for (cx, cy), r in (((x0, y0), w0 / 2), ((x1, y1), w1 / 2)):
                    self.d.ellipse([(cx - r + s) * SS, (cy - r) * SS, (cx + r + s) * SS, (cy + r) * SS], fill=fill)

    def fill_below(self, tops):
        """tops：每个超采样列的顶边 y（最终像素），其下全部填满。"""
        arr = np.array(self.img)
        ys = (np.arange(self.h * SS)[:, None] + 0.5) / SS
        arr = np.maximum(arr, np.where(ys >= np.asarray(tops)[None, :], 255, 0).astype(np.uint8))
        self.img = Image.fromarray(arr)
        self.d = ImageDraw.Draw(self.img)

    def fill_above(self, bottoms):
        arr = np.array(self.img)
        ys = (np.arange(self.h * SS)[:, None] + 0.5) / SS
        arr = np.maximum(arr, np.where(ys < np.asarray(bottoms)[None, :], 255, 0).astype(np.uint8))
        self.img = Image.fromarray(arr)
        self.d = ImageDraw.Draw(self.img)

    def alpha(self):
        return self.img.reduce(SS)

    def save(self, name):
        small = self.alpha()
        rgba = Image.new('RGBA', (self.w, self.h), (255, 255, 255, 0))
        rgba.putalpha(small)
        rgba.save(os.path.join(OUT, name + '.png'), optimize=True)
        with open(os.path.join(OUT, name + '.png.mcmeta'), 'w', encoding='utf-8') as f:
            f.write('{"texture":{"blur":true}}\n')
        return small


def columns(w=W):
    return (np.arange(w * SS) + 0.5) / (w * SS)


def tri(u, k, phase):
    f = (u * k + phase) % 1.0
    return 1.0 - np.abs(2.0 * f - 1.0)


def norm(a):
    return (a - a.min()) / (a.max() - a.min())


def ground_line(x, base=0.11, amp=0.022, k=3, phase=0.2):
    """中景地面的顶边 y（最终像素）。"""
    return H - H * (base + amp * math.sin(2 * math.pi * (k * x / W + phase)))


def fill_ground(m, base=0.11, amp=0.022, k=3, phase=0.2):
    u = columns()
    m.fill_below(H - H * (base + amp * np.sin(2 * np.pi * (k * u + phase))))


# ==================== 远景 ====================

def far_mountains():
    u = columns()
    h = 0.34 * tri(u, 3, 0.08) ** 1.5 + 0.24 * tri(u, 5, 0.41) ** 1.3 + 0.1 * tri(u, 11, 0.63) \
        + 0.05 * tri(u, 23, 0.17) + 0.025 * tri(u, 53, 0.71)
    h = norm(h)
    top = H * (1 - (0.2 + 0.74 * h))
    m = Mask(W, H)
    m.fill_below(top)
    m.save('far_mountains')

    # 雪顶：高于雪线的部分，从山脊往下一段锯齿状的带子
    snow = Mask(W, H)
    above = np.clip((h - 0.5) / 0.35, 0.0, 1.0)
    depth = H * (0.05 + 0.16 * above) * (0.7 + 0.3 * tri(u, 41, 0.3)) * (0.75 + 0.25 * tri(u, 97, 0.6))
    arr = np.array(snow.img)
    ys = (np.arange(H * SS)[:, None] + 0.5) / SS
    band = (ys >= top[None, :]) & (ys < (top + depth * (above > 0.02))[None, :])
    arr[band] = 255
    snow.img = Image.fromarray(arr)
    snow.save('far_mountains_snow')


def far_hills():
    u = columns()
    h = 0.5 + 0.26 * np.sin(2 * np.pi * (2 * u) + 0.4) + 0.15 * np.sin(2 * np.pi * (5 * u) + 1.3) \
        + 0.05 * np.sin(2 * np.pi * (11 * u) + 2.1)
    m = Mask(W, H)
    m.fill_below(H * (1 - (0.22 + 0.42 * norm(h))))
    m.save('far_hills')


def far_dunes():
    u = columns()

    def dune(k, phase):
        s = (u * k + phase) % 1.0
        return np.where(s < 0.72, np.sin(np.pi / 2 * s / 0.72), np.cos(np.pi / 2 * (s - 0.72) / 0.28)) ** 1.6

    h = 0.6 * dune(3, 0.1) + 0.3 * dune(7, 0.55) + 0.1 * dune(13, 0.2)
    m = Mask(W, H)
    m.fill_below(H * (1 - (0.18 + 0.44 * norm(h))))
    m.save('far_dunes')


def far_sea():
    rng = random.Random(11)
    u = columns()
    h = np.full_like(u, 0.1)
    for _ in range(7):
        c = rng.random()
        width = rng.uniform(0.012, 0.05)
        height = rng.uniform(0.06, 0.26)
        d = np.minimum(np.abs(u - c), 1 - np.abs(u - c))
        h = np.maximum(h, 0.1 + height * np.exp(-(d / width) ** 2) * (0.85 + 0.15 * tri(u, 61, c)))
    m = Mask(W, H)
    m.fill_below(H * (1 - h))
    m.save('far_sea')


def far_cave():
    u = columns()
    ceil = 0.12 + 0.16 * tri(u, 4, 0.2) ** 2 + 0.06 * tri(u, 13, 0.5) + 0.03 * tri(u, 37, 0.1)
    floor = 0.16 + 0.12 * tri(u, 3, 0.65) ** 2 + 0.05 * tri(u, 11, 0.3) + 0.02 * tri(u, 41, 0.8)
    m = Mask(W, H)
    m.fill_above(H * ceil)
    m.fill_below(H * (1 - floor))
    m.save('far_cave')


# ==================== 中景 ====================

def round_tree(m, rng, x, gy, height, puff=1.0):
    crown_r = height * rng.uniform(0.25, 0.32) * puff
    cy = gy - height + crown_r
    m.line([(x, gy + 3), (x + rng.uniform(-1.5, 1.5), cy + crown_r * 0.3)], [height * 0.06, height * 0.04])
    for _ in range(8):
        a = rng.uniform(0, 2 * math.pi)
        d = rng.uniform(0.0, crown_r * 0.6)
        m.circle(x + math.cos(a) * d * 1.25, cy + math.sin(a) * d * 0.75, crown_r * rng.uniform(0.5, 0.82))


def pine(m, rng, x, gy, height, width):
    tiers = rng.choice((4, 5, 5, 6))
    m.rect(x - height * 0.02 - 0.6, gy - height * 0.25, x + height * 0.02 + 0.6, gy + 3)
    for i in range(tiers):
        f = (i + 1) / tiers
        apex = gy - height + i * height * 0.75 / tiers
        base = apex + height * (0.26 + 0.06 * f)
        half = width * (0.25 + 0.75 * f) / 2
        droop = height * 0.035
        m.poly([(x, apex), (x + half * 0.55, apex + (base - apex) * 0.6), (x + half, base),
                (x + half * 0.45, base - droop), (x, base - droop * 0.4),
                (x - half * 0.45, base - droop), (x - half, base), (x - half * 0.55, apex + (base - apex) * 0.6)])


def mid_forest():
    rng = random.Random(21)
    m = Mask(W, H)
    fill_ground(m)
    x = 0.0
    while x < W:
        h = rng.uniform(0.58, 0.97) * H
        if rng.random() < 0.18:
            # 低矮灌木丛
            for _ in range(4):
                m.circle(x + rng.uniform(-10, 10), ground_line(x) - rng.uniform(4, 9), rng.uniform(5, 9))
            x += rng.uniform(14, 24)
            continue
        round_tree(m, rng, x, ground_line(x), h)
        x += rng.uniform(30, 58)
    m.save('mid_forest')


def mid_pines():
    rng = random.Random(33)
    m = Mask(W, H)
    fill_ground(m, base=0.1)
    x = 0.0
    while x < W:
        h = rng.uniform(0.42, 0.95) * H
        pine(m, rng, x, ground_line(x, base=0.1), h, h * rng.uniform(0.34, 0.42))
        x += rng.uniform(12, 30)
    m.save('mid_pines')


def torii(m, x, gy, height):
    """鸟居：两根略收分的柱子、两端上翘的笠木、贯与中间的额束。"""
    span = height * 0.7
    left, right = x - span / 2, x + span / 2
    pw = height * 0.075
    for px in (left, right):
        m.poly([(px - pw * 0.6, gy + 2), (px + pw * 0.6, gy + 2), (px + pw * 0.45, gy - height * 0.86),
                (px - pw * 0.45, gy - height * 0.86)])
    top = gy - height
    over = span * 0.2
    lift = height * 0.08
    thick = height * 0.085
    m.poly([(left - over, top - lift), (left - over * 0.5, top + thick * 0.35), (x, top + thick * 0.55),
            (right + over * 0.5, top + thick * 0.35), (right + over, top - lift),
            (right + over * 0.55, top + thick * 1.15), (x, top + thick * 1.25), (left - over * 0.55, top + thick * 1.15)])
    m.rect(left - span * 0.08, top + thick * 1.25, right + span * 0.08, top + thick * 1.9)
    nuki = gy - height * 0.66
    m.rect(left - span * 0.1, nuki, right + span * 0.1, nuki + height * 0.06)
    m.rect(x - height * 0.035, top + thick * 1.9, x + height * 0.035, nuki)


def stone_lantern(m, x, gy, height):
    m.rect(x - height * 0.18, gy - height * 0.25, x + height * 0.18, gy + 2)
    m.rect(x - height * 0.08, gy - height * 0.55, x + height * 0.08, gy - height * 0.25)
    m.rect(x - height * 0.2, gy - height * 0.72, x + height * 0.2, gy - height * 0.55)
    m.poly([(x - height * 0.32, gy - height * 0.72), (x + height * 0.32, gy - height * 0.72), (x, gy - height)])


def cherry_tree(m, rng, x, gy, height):
    lean = rng.uniform(-1, 1) * height * 0.18
    trunk = [(x, gy + 3), (x + lean * 0.25, gy - height * 0.25), (x + lean * 0.65, gy - height * 0.5),
             (x + lean, gy - height * 0.62)]
    m.line(trunk, [height * 0.075, height * 0.06, height * 0.045, height * 0.03])
    cx, cy = x + lean * 0.9, gy - height * 0.7
    for side in (-1, 1):
        m.line([(x + lean * 0.55, gy - height * 0.47), (cx + side * height * 0.28, cy + height * 0.02)],
               [height * 0.035, height * 0.018])
    for _ in range(16):
        m.circle(cx + rng.uniform(-0.5, 0.5) * height, cy + rng.uniform(-0.16, 0.1) * height,
                 height * rng.uniform(0.09, 0.16))


def mid_cherry():
    rng = random.Random(45)
    m = Mask(W, H)
    fill_ground(m)
    gate_x = W * 0.5
    torii(m, gate_x, ground_line(gate_x), H * 0.74)
    stone_lantern(m, gate_x - 88, ground_line(gate_x - 88), H * 0.22)
    stone_lantern(m, gate_x + 88, ground_line(gate_x + 88), H * 0.22)
    ANCHORS['mid_cherry'] = {'focal': 0.5}
    x = gate_x + 165
    while x < gate_x + W - 150:
        cherry_tree(m, rng, x % W, ground_line(x % W), rng.uniform(0.62, 0.95) * H)
        x += rng.uniform(85, 125)
    m.save('mid_cherry')


def house(m, rng, x, gy, lights, chimneys):
    w = rng.uniform(52, 76)
    h = rng.uniform(30, 40)
    roof = rng.uniform(22, 30)
    left = x - w / 2
    m.rect(left, gy - h, left + w, gy + 2)
    m.poly([(left - 4, gy - h + 1), (x, gy - h - roof), (left + w + 4, gy - h + 1)])
    cx = x + w * rng.uniform(0.15, 0.3)
    top = gy - h - roof * 0.75 - 4
    m.rect(cx - 3.5, top, cx + 3.5, gy - h - roof * 0.3)
    chimneys.append((cx, top))
    for wx in (left + w * 0.26, left + w * 0.7):
        lights.append((wx - 3.5, gy - h * 0.68, wx + 3.5, gy - h * 0.68 + 8))


def windmill(m, x, gy, lights):
    tower_h = H * 0.6
    m.poly([(x - 17, gy + 2), (x + 17, gy + 2), (x + 9, gy - tower_h), (x - 9, gy - tower_h)])
    m.poly([(x - 13, gy - tower_h + 3), (x + 13, gy - tower_h + 3), (x + 6, gy - tower_h - 12), (x, gy - tower_h - 17),
            (x - 6, gy - tower_h - 12)])
    hub = (x, gy - tower_h - 4)
    lights.append((x - 3, gy - tower_h * 0.42, x + 3, gy - tower_h * 0.42 + 8))
    lights.append((x - 4, gy - 12, x + 4, gy + 1))
    return hub


def fence(m, x0, x1, gy_fn):
    x = x0
    while x <= x1:
        gy = gy_fn(x)
        m.rect(x - 0.8, gy - 8, x + 0.8, gy + 2)
        x += 7
    for offset in (5.5, 2.5):
        pts = [(xx, gy_fn(xx) - offset) for xx in np.linspace(x0, x1, 12)]
        m.line(pts, 1.1)


def mid_village():
    rng = random.Random(57)
    m = Mask(W, H)
    fill_ground(m, base=0.1)
    lights = []
    chimneys = []
    mill_x = W * 0.5
    gy = lambda xx: ground_line(xx % W, base=0.1)
    hub = windmill(m, mill_x, gy(mill_x), lights)
    for hx in (mill_x - 115, mill_x + 95, mill_x + 330, mill_x - 330, mill_x + 520, mill_x - 520):
        house(m, rng, hx % W, gy(hx), lights, chimneys)
    for tx in (mill_x - 205, mill_x + 175, mill_x + 225, mill_x + 430, mill_x - 245, mill_x - 430, mill_x + 610):
        round_tree(m, rng, tx % W, gy(tx), rng.uniform(0.45, 0.68) * H)
    fence(m, mill_x + 30, mill_x + 62, gy)
    fence(m, mill_x - 75, mill_x - 34, gy)
    m.save('mid_village')

    glow = Image.new('L', (W, H), 0)
    sharp = Image.new('L', (W * SS, H * SS), 0)
    gd = ImageDraw.Draw(glow)
    sd = ImageDraw.Draw(sharp)
    for x0, y0, x1, y1 in lights:
        for s in (-W, 0, W):
            gd.ellipse([x0 + s - 5, y0 - 5, x1 + s + 5, y1 + 5], fill=150)
            sd.rectangle([(x0 + s) * SS, y0 * SS, (x1 + s) * SS, y1 * SS], fill=255)
    glow = glow.filter(ImageFilter.GaussianBlur(4))
    combined = Image.fromarray(np.maximum(np.array(glow), np.array(sharp.reduce(SS))))
    rgba = Image.new('RGBA', (W, H), (255, 255, 255, 0))
    rgba.putalpha(combined)
    rgba.save(os.path.join(OUT, 'mid_village_lights.png'), optimize=True)
    with open(os.path.join(OUT, 'mid_village_lights.png.mcmeta'), 'w', encoding='utf-8') as f:
        f.write('{"texture":{"blur":true}}\n')
    ANCHORS['mid_village'] = {'focal': 0.5, 'hub': [hub[0] / W, hub[1] / H],
                              'chimneys': [[(cx % W) / W, cy / H] for cx, cy in chimneys]}


def saguaro(m, rng, x, gy, height):
    w = height * 0.16
    m.line([(x, gy + 3), (x, gy - height + w / 2)], w)
    for side in (-1, 1):
        if rng.random() < 0.8:
            y0 = gy - height * rng.uniform(0.35, 0.55)
            reach = height * rng.uniform(0.22, 0.3)
            up = height * rng.uniform(0.25, 0.4)
            m.line([(x, y0), (x + side * reach, y0), (x + side * reach, y0 - up)], w * 0.75)


def palm(m, rng, x, gy, height):
    lean = rng.uniform(-1, 1) * height * 0.25
    pts = [(x + lean * (t ** 1.6), gy + 3 - height * t) for t in np.linspace(0, 1, 8)]
    m.line(pts, [height * w for w in np.linspace(0.07, 0.04, 8)])
    tx, ty = pts[-1]
    for i in range(7):
        a = -math.pi / 2 + (i - 3) * 0.48 + rng.uniform(-0.1, 0.1)
        length = height * rng.uniform(0.32, 0.42)
        frond = []
        for t in np.linspace(0, 1, 7):
            frond.append((tx + math.cos(a) * length * t, ty + math.sin(a) * length * t + (length * 0.55) * t * t))
        m.line(frond, [height * w for w in np.linspace(0.045, 0.012, 7)])


def mid_desert():
    rng = random.Random(69)
    m = Mask(W, H)
    fill_ground(m, base=0.12, amp=0.03, k=2)
    gy = lambda xx: ground_line(xx % W, base=0.12, amp=0.03, k=2)
    px = W * 0.5
    base_y = gy(px) + 2
    for step in range(7):
        half = 104 - step * 14
        m.rect(px - half, base_y - (step + 1) * 14, px + half, base_y - step * 14 + 1)
    m.rect(px - 9, base_y - 108, px + 9, base_y - 97)
    ANCHORS['mid_desert'] = {'focal': 0.5}
    for ox in (175, 215, 250):
        palm(m, rng, (px + ox) % W, gy(px + ox), rng.uniform(0.6, 0.82) * H)
    for cx in (px - 170, px - 300, px + 380, px + 470, px - 450, px + 580):
        saguaro(m, rng, cx % W, gy(cx), rng.uniform(0.34, 0.58) * H)
    m.save('mid_desert')


def lighthouse(m, x, gy):
    height = H * 0.5
    m.poly([(x - 11, gy + 2), (x + 11, gy + 2), (x + 7, gy - height), (x - 7, gy - height)])
    m.rect(x - 12, gy - height - 4, x + 12, gy - height)
    m.rect(x - 7.5, gy - height - 17, x + 7.5, gy - height - 4)
    m.ellipse(x, gy - height - 17, 8.5, 6)
    m.rect(x - 0.9, gy - height - 30, x + 0.9, gy - height - 20)
    return x, gy - height - 10


def rock(m, rng, x, gy, width, height):
    pts = [(x - width / 2, gy + 6)]
    n = 7
    for i in range(n + 1):
        t = i / n
        bump = math.sin(math.pi * t) ** 0.6
        pts.append((x - width / 2 + width * t + rng.uniform(-3, 3), gy - height * bump + rng.uniform(-3, 2)))
    pts.append((x + width / 2, gy + 6))
    m.poly(pts)


def boat(m, x, gy):
    m.poly([(x - 21, gy - 6), (x + 21, gy - 6), (x + 13, gy + 1), (x - 15, gy + 1)])
    m.rect(x - 0.9, gy - 40, x + 0.9, gy - 6)
    m.poly([(x + 1.8, gy - 38), (x + 1.8, gy - 9), (x + 20, gy - 9)])
    m.poly([(x - 1.8, gy - 32), (x - 1.8, gy - 9), (x - 14, gy - 9)])


def mid_coast():
    rng = random.Random(81)
    m = Mask(W, H)
    water = H * 0.86
    lx = W * 0.5
    rock(m, rng, lx - 12, water, 170, 40)
    rock(m, rng, lx + 70, water, 90, 22)
    lamp = lighthouse(m, lx - 8, water - 35)
    for sx, sw, sh in ((lx - 260, 22, 70), (lx - 228, 14, 44), (lx + 330, 44, 30), (lx + 500, 20, 58),
                       (lx - 500, 90, 22)):
        rock(m, rng, sx % W, water, sw, sh)
    boat(m, (lx + 200) % W, water - 1)
    m.save('mid_coast')
    ANCHORS['mid_coast'] = {'focal': 0.5, 'lamp': [lamp[0] / W, lamp[1] / H], 'water': water / H}


def willow(m, rng, x, gy, height):
    m.line([(x, gy + 3), (x + rng.uniform(-3, 3), gy - height * 0.6)], [height * 0.07, height * 0.045])
    cx, cy = x, gy - height * 0.7
    for _ in range(9):
        m.circle(cx + rng.uniform(-0.3, 0.3) * height, cy + rng.uniform(-0.15, 0.08) * height, height * rng.uniform(0.1, 0.16))
    for _ in range(16):
        sx = cx + rng.uniform(-0.38, 0.38) * height
        sy = cy + rng.uniform(0.0, 0.08) * height
        length = height * rng.uniform(0.25, 0.55)
        m.line([(sx, sy), (sx + rng.uniform(-1.5, 1.5), sy + length)], 1.2)


def reeds(m, rng, x, gy):
    for _ in range(rng.randint(4, 7)):
        bx = x + rng.uniform(-6, 6)
        height = rng.uniform(10, 22)
        tip = bx + rng.uniform(-3, 3)
        m.line([(bx, gy + 2), (tip, gy - height)], 0.9)
        if rng.random() < 0.5:
            m.line([(tip, gy - height + 1), (tip + 0.2, gy - height + 6)], 2.2)


def mid_swamp():
    rng = random.Random(93)
    m = Mask(W, H)
    fill_ground(m, base=0.08, amp=0.015)
    gy = lambda xx: ground_line(xx % W, base=0.08, amp=0.015)
    x = 0.0
    while x < W:
        if rng.random() < 0.45:
            willow(m, rng, x, gy(x), rng.uniform(0.62, 0.95) * H)
            x += rng.uniform(60, 95)
        else:
            reeds(m, rng, x, gy(x))
            x += rng.uniform(20, 35)
    m.save('mid_swamp')


def mid_jungle():
    rng = random.Random(105)
    m = Mask(W, H)
    fill_ground(m, base=0.1)
    gy = lambda xx: ground_line(xx % W, base=0.1)
    x = 0.0
    while x < W:
        r = rng.random()
        if r < 0.35:
            h = rng.uniform(0.7, 0.98) * H
            round_tree(m, rng, x, gy(x), h, puff=0.85)
            for _ in range(3):
                vx = x + rng.uniform(-12, 12)
                m.line([(vx, gy(x) - h * 0.75), (vx + rng.uniform(-2, 2), gy(x) - h * rng.uniform(0.3, 0.5))], 0.9)
            x += rng.uniform(30, 50)
        elif r < 0.7:
            palm(m, rng, x, gy(x), rng.uniform(0.45, 0.75) * H)
            x += rng.uniform(25, 40)
        else:
            for _ in range(rng.randint(3, 6)):
                bx = x + rng.uniform(-8, 8)
                top = gy(x) - rng.uniform(0.4, 0.8) * H
                y = gy(x) + 2
                while y > top:
                    seg = rng.uniform(8, 13)
                    m.rect(bx - 1.2, max(top, y - seg), bx + 1.2, y - 0.6)
                    y -= seg
                m.line([(bx, top + 2), (bx + 7, top - 2)], 1.0)
            x += rng.uniform(20, 30)
    m.save('mid_jungle')


def acacia(m, rng, x, gy, height):
    split = gy - height * 0.45
    m.line([(x, gy + 3), (x, split)], [height * 0.06, height * 0.05])
    tops = []
    for side in (-1, 1):
        tx = x + side * height * rng.uniform(0.18, 0.3)
        ty = gy - height * 0.85
        m.line([(x, split), (tx, ty)], [height * 0.04, height * 0.025])
        tops.append((tx, ty))
    cx = (tops[0][0] + tops[1][0]) / 2
    cy = min(t[1] for t in tops) - height * 0.02
    width = height * rng.uniform(0.6, 0.8)
    for _ in range(5):
        m.ellipse(cx + rng.uniform(-0.25, 0.25) * width, cy + rng.uniform(-0.05, 0.04) * height,
                  width * rng.uniform(0.3, 0.45), height * rng.uniform(0.05, 0.08))


def grass_clump(m, rng, x, gy):
    for _ in range(rng.randint(5, 9)):
        bx = x + rng.uniform(-5, 5)
        m.line([(bx, gy + 2), (bx + rng.uniform(-4, 4), gy - rng.uniform(5, 11))], 0.9)


def mid_savanna():
    rng = random.Random(117)
    m = Mask(W, H)
    fill_ground(m, base=0.1, amp=0.015)
    gy = lambda xx: ground_line(xx % W, base=0.1, amp=0.015)
    x = 0.0
    while x < W:
        if rng.random() < 0.4:
            acacia(m, rng, x, gy(x), rng.uniform(0.62, 0.9) * H)
            x += rng.uniform(80, 140)
        else:
            grass_clump(m, rng, x, gy(x))
            x += rng.uniform(16, 30)
    m.save('mid_savanna')


def giant_mushroom(m, rng, x, gy, height):
    cap_rx = height * rng.uniform(0.38, 0.5)
    cap_ry = height * 0.3
    cap_y = gy - height + cap_ry
    m.ellipse(x, cap_y, cap_rx, cap_ry)
    m.rect(x - cap_rx - 2, cap_y + cap_ry * 0.25, x + cap_rx + 2, cap_y + cap_ry + 2, fill=0)
    for _ in range(5):
        m.circle(x + rng.uniform(-0.6, 0.6) * cap_rx, cap_y - rng.uniform(0.1, 0.65) * cap_ry, cap_ry * rng.uniform(0.12, 0.2), fill=0)
    m.line([(x, gy + 3), (x + rng.uniform(-3, 3), cap_y + cap_ry * 0.2)], [height * 0.13, height * 0.1])


def mid_mushroom():
    rng = random.Random(129)
    m = Mask(W, H)
    fill_ground(m, base=0.1)
    gy = lambda xx: ground_line(xx % W, base=0.1)
    x = 0.0
    while x < W:
        if rng.random() < 0.55:
            giant_mushroom(m, rng, x, gy(x), rng.uniform(0.55, 0.95) * H)
            x += rng.uniform(60, 100)
        else:
            for _ in range(3):
                sx = x + rng.uniform(-8, 8)
                giant_mushroom(m, rng, sx, gy(sx), rng.uniform(0.08, 0.14) * H)
            x += rng.uniform(20, 30)
    m.save('mid_mushroom')


def mid_cave():
    rng = random.Random(141)
    m = Mask(W, H)
    u = columns()
    m.fill_above(H * (0.06 + 0.03 * np.sin(2 * np.pi * (3 * u) + 1.1)))
    m.fill_below(H * (1 - (0.08 + 0.03 * np.sin(2 * np.pi * (2 * u) + 0.3))))
    x = 0.0
    while x < W:
        w = rng.uniform(6, 16)
        length = rng.uniform(0.12, 0.42) * H
        m.poly([(x - w / 2, 0), (x + w / 2, 0), (x + rng.uniform(-1, 1), length)])
        if rng.random() < 0.6:
            sx = x + rng.uniform(-10, 10)
            sw = rng.uniform(8, 18)
            sl = rng.uniform(0.1, 0.32) * H
            m.poly([(sx - sw / 2, H), (sx + sw / 2, H), (sx + rng.uniform(-1, 1), H - sl)])
        x += rng.uniform(14, 34)
    m.save('mid_cave')


# ==================== 前景地面 ====================

def ground_top(u):
    """前景地面顶边（占贴图高度的比例，自上而下），游戏里用同一个公式在地面上种草和花。"""
    return 0.42 + 0.1 * np.sin(2 * np.pi * (2 * u) + 0.6) + 0.05 * np.sin(2 * np.pi * (5 * u) + 1.9)


def near_ground(name, tufts):
    rng = random.Random(153)
    m = Mask(W, GROUND_H)
    u = columns()
    m.fill_below(GROUND_H * ground_top(u))
    if tufts:
        x = 0.0
        while x < W:
            gy = GROUND_H * float(ground_top(np.array([x / W]))[0])
            for _ in range(rng.randint(2, 4)):
                bx = x + rng.uniform(-2, 2)
                h = rng.uniform(2.5, 6.5)
                m.poly([(bx - 1.1, gy + 1.5), (bx + 1.1, gy + 1.5), (bx + rng.uniform(-1.5, 1.5), gy - h)])
            x += rng.uniform(3, 7)
    m.save(name)


def main():
    os.makedirs(OUT, exist_ok=True)
    far_mountains()
    far_hills()
    far_dunes()
    far_sea()
    far_cave()
    mid_forest()
    mid_pines()
    mid_cherry()
    mid_village()
    mid_desert()
    mid_coast()
    mid_swamp()
    mid_jungle()
    mid_savanna()
    mid_mushroom()
    mid_cave()
    near_ground('near_grass', True)
    near_ground('near_soft', False)
    with open(os.path.join(ROOT, 'tools', 'region_scene_anchors.json'), 'w', encoding='utf-8') as f:
        json.dump(ANCHORS, f, indent=2)
    print(json.dumps(ANCHORS, indent=2))


if __name__ == '__main__':
    main()
