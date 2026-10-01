"""生成首次启动加载画面用的灯塔插画与光效贴图。

输出到 assets/dreamingfishcore/textures/gui/loading/：
  lighthouse.png        灯塔本体（128x256），礁石底部渐隐进海面
  lighthouse_glow.png   灯室柔光（128x128），叠加混合
  lighthouse_beam.png   旋转光束（512x128），光源在左端中点，叠加混合
  lighthouse_flare.png  光束正对镜头时的横向星芒（256x64），叠加混合
每张都附带 .mcmeta 打开线性过滤，缩放时边缘不会出锯齿。

用法：python tools/generate_startup_lighthouse.py
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "dreamingfishcore", "textures", "gui", "loading")
SS = 4  # 超采样倍数：先画 4 倍大，再按像素块平均缩小


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


class Layer:
    """以最终像素为单位作图，内部按 SS 倍分辨率记录预乘颜色。"""

    def __init__(self, width, height):
        self.w = width
        self.h = height
        self.rgb = np.zeros((height * SS, width * SS, 3), np.float32)
        self.a = np.zeros((height * SS, width * SS), np.float32)
        ys, xs = np.mgrid[0:height * SS, 0:width * SS].astype(np.float32)
        self.x = (xs + 0.5) / SS
        self.y = (ys + 0.5) / SS

    def mask_polygon(self, points):
        img = Image.new("L", (self.w * SS, self.h * SS), 0)
        ImageDraw.Draw(img).polygon([(px * SS, py * SS) for px, py in points], fill=255)
        return np.asarray(img, np.float32) / 255.0

    def mask_ellipse(self, cx, cy, rx, ry):
        return (((self.x - cx) / rx) ** 2 + ((self.y - cy) / ry) ** 2 <= 1.0).astype(np.float32)

    def mask_rect(self, x0, y0, x1, y1):
        return ((self.x >= x0) & (self.x < x1) & (self.y >= y0) & (self.y < y1)).astype(np.float32)

    def paint(self, mask, color, alpha=1.0):
        """color 可以是 (r,g,b) 或与画布同形的 (H,W,3) 数组；alpha 可以是数或数组。"""
        col = np.asarray(color, np.float32) / 255.0
        cover = np.clip(mask * alpha, 0.0, 1.0)
        if col.ndim == 1:
            col = col.reshape(1, 1, 3)
        self.rgb = self.rgb * (1.0 - cover[..., None]) + col * cover[..., None]
        self.a = self.a * (1.0 - cover) + cover

    def save(self, path, fade_bottom=0.0):
        rgb = self.rgb.copy()
        a = self.a.copy()
        if fade_bottom > 0.0:
            ramp = np.clip((self.h - self.y) / fade_bottom, 0.0, 1.0)
            rgb *= ramp[..., None]
            a *= ramp
        # rgb 已按覆盖率混合（相当于预乘），块平均后再除回 alpha
        h, w = self.h, self.w
        rgb = rgb.reshape(h, SS, w, SS, 3).mean(axis=(1, 3))
        a = a.reshape(h, SS, w, SS).mean(axis=(1, 3))
        safe = np.where(a > 1e-4, a, 1.0)
        out = np.dstack([np.clip(rgb / safe[..., None], 0, 1), np.clip(a, 0, 1)])
        Image.fromarray((out * 255.0 + 0.5).astype(np.uint8), "RGBA").save(path)


def shade_cylinder(layer, cx, half_width_at, base, light=0.62, dark=0.55, spec=0.18):
    """圆柱体明暗：左侧受光、右侧背光，左上带一道柔和高光。返回 (H,W,3) 颜色数组。"""
    hw = np.maximum(half_width_at(layer.y), 1e-3)
    xn = np.clip((layer.x - cx) / hw, -1.0, 1.0)
    angle = np.arcsin(xn)
    lit = 0.5 + 0.5 * np.cos(angle + 0.75)
    factor = dark + (1.0 - dark) * lit
    highlight = spec * np.exp(-((xn + 0.42) / 0.16) ** 2)
    col = np.asarray(base, np.float32).reshape(1, 1, 3) * factor[..., None]
    return np.clip(col + highlight[..., None] * 255.0 * light, 0, 255)


def lighthouse():
    W, H = 128, 256
    L = Layer(W, H)
    cx = 64.0

    # ---- 礁石：几块错落的岩体，顶面受灯光照出一点暖色 ----
    rocks = [
        ([(10, 256), (18, 232), (30, 222), (44, 216), (52, 220), (58, 230), (60, 256)], "#1a2229"),
        ([(46, 256), (52, 226), (66, 212), (82, 210), (96, 218), (104, 234), (110, 256)], "#202932"),
        ([(84, 256), (92, 236), (104, 226), (116, 232), (122, 246), (124, 256)], "#182027"),
        ([(26, 256), (34, 240), (48, 236), (60, 244), (64, 256)], "#151c22"),
        ([(60, 256), (68, 238), (80, 232), (92, 240), (98, 256)], "#1c252d"),
    ]
    for points, color in rocks:
        mask = L.mask_polygon(points)
        top = min(p[1] for p in points)
        grad = np.clip((L.y - top) / (256 - top), 0, 1)[..., None]
        col = np.asarray(hex_rgb(color), np.float32) * (1.08 - 0.45 * grad)
        L.paint(mask, col)
    # 岩顶的暖色轮廓光
    rim = [((44, 216), (52, 220)), ((66, 212), (82, 210)), ((82, 210), (96, 218)), ((30, 222), (44, 216)),
           ((92, 236), (104, 226))]
    img = Image.new("L", (W * SS, H * SS), 0)
    draw = ImageDraw.Draw(img)
    for (x0, y0), (x1, y1) in rim:
        draw.line([(x0 * SS, y0 * SS), (x1 * SS, y1 * SS)], fill=255, width=int(1.1 * SS))
    L.paint(np.asarray(img, np.float32) / 255.0, hex_rgb("#8c7652"), 0.32)

    # ---- 塔基石台 ----
    L.paint(L.mask_polygon([(34, 214), (94, 214), (90, 206), (38, 206)]),
            shade_cylinder(L, cx, lambda y: 28 + 0 * y, hex_rgb("#4a535b"), light=0.3, dark=0.6, spec=0.1))
    L.paint(L.mask_rect(38, 206, 90, 207.2), hex_rgb("#77818a"), 0.8)

    # ---- 塔身：上窄下宽的圆柱，奶白底与两道砖红色环带 ----
    top_y, bottom_y = 98.0, 207.0
    top_hw, bottom_hw = 14.5, 22.5

    def tower_hw(y):
        t = np.clip((y - top_y) / (bottom_y - top_y), 0, 1)
        return top_hw + (bottom_hw - top_hw) * t

    tower = L.mask_polygon([(cx - top_hw, top_y), (cx + top_hw, top_y), (cx + bottom_hw, bottom_y),
                            (cx - bottom_hw, bottom_y)])
    cream = shade_cylinder(L, cx, tower_hw, hex_rgb("#f1e8d4"), dark=0.5)
    L.paint(tower, cream)
    for band_top, band_bottom in ((124.0, 141.0), (163.0, 180.0)):
        band = tower * L.mask_rect(0, band_top, W, band_bottom)
        L.paint(band, shade_cylinder(L, cx, tower_hw, hex_rgb("#a8433f"), dark=0.45, spec=0.12))
        # 环带边缘一圈细阴影，显出厚度
        L.paint(tower * L.mask_rect(0, band_bottom, W, band_bottom + 1.0), hex_rgb("#3b2a26"), 0.35)
    # 底部环境光遮蔽
    occlusion = np.clip((L.y - 186.0) / 21.0, 0, 1) * 0.35
    L.paint(tower, (12, 16, 20), occlusion)

    # 窗与门：一扇窗亮着暖灯，像有人在守望
    def arch(cx0, y0, y1, half, color, alpha=1.0):
        mask = np.maximum(L.mask_rect(cx0 - half, y0 + half, cx0 + half, y1), L.mask_ellipse(cx0, y0 + half, half, half))
        L.paint(mask * tower, hex_rgb(color), alpha)

    arch(cx - 3.0, 107.0, 118.0, 2.4, "#1c242b")
    arch(cx + 4.0, 148.0, 159.0, 2.6, "#1c242b")
    arch(cx + 4.0, 149.2, 159.0, 1.6, "#f4b860", 0.95)
    arch(cx, 190.0, 207.0, 5.2, "#2b211b")
    arch(cx, 191.5, 207.0, 4.0, "#3d2d22")

    # ---- 环形阳台与栏杆 ----
    L.paint(L.mask_polygon([(cx - 25, 90), (cx + 25, 90), (cx + 22, 98), (cx - 22, 98)]),
            shade_cylinder(L, cx, lambda y: 25 + 0 * y, hex_rgb("#2d3842"), light=0.5, dark=0.55, spec=0.15))
    L.paint(L.mask_rect(cx - 25, 89.0, cx + 25, 90.4), hex_rgb("#6d7a85"))
    for i in range(-11, 12):
        px = cx + i * 2.05
        L.paint(L.mask_rect(px - 0.45, 79.0, px + 0.45, 89.0), hex_rgb("#2a343d"), 0.95)
    L.paint(L.mask_rect(cx - 23.5, 78.0, cx + 23.5, 79.6), hex_rgb("#3a4651"))
    L.paint(L.mask_rect(cx - 23.0, 84.2, cx + 23.0, 85.0), hex_rgb("#2a343d"), 0.9)

    # ---- 灯室：暖光玻璃、窗棂与底座 ----
    L.paint(L.mask_rect(cx - 15, 72.0, cx + 15, 79.0),
            shade_cylinder(L, cx, lambda y: 15 + 0 * y, hex_rgb("#26303a"), light=0.4, dark=0.6, spec=0.12))
    glass = L.mask_rect(cx - 12, 47.0, cx + 12, 72.0)
    r = np.sqrt(((L.x - cx) / 12.0) ** 2 + ((L.y - 59.0) / 13.0) ** 2)
    warm = np.dstack([
        np.interp(r, [0.0, 0.35, 0.75, 1.3], [255, 255, 247, 222]),
        np.interp(r, [0.0, 0.35, 0.75, 1.3], [252, 236, 196, 150]),
        np.interp(r, [0.0, 0.35, 0.75, 1.3], [236, 186, 112, 70]),
    ])
    L.paint(glass, warm)
    for mx in (-12.0, -4.6, 3.6, 11.0):
        L.paint(L.mask_rect(cx + mx, 47.0, cx + mx + 1.0, 72.0), hex_rgb("#1b232a"), 0.9)
    L.paint(L.mask_rect(cx - 12, 47.0, cx + 12, 48.3), hex_rgb("#1b232a"))
    L.paint(L.mask_rect(cx - 12, 70.6, cx + 12, 72.0), hex_rgb("#1b232a"))

    # ---- 檐口、穹顶与顶饰 ----
    L.paint(L.mask_polygon([(cx - 17.5, 47.0), (cx + 17.5, 47.0), (cx + 15.5, 43.0), (cx - 15.5, 43.0)]),
            hex_rgb("#1d262d"))
    dome = L.mask_ellipse(cx, 43.0, 15.0, 16.0) * L.mask_rect(0, 0, W, 43.0)
    L.paint(dome, shade_cylinder(L, cx, lambda y: 15 + 0 * y, hex_rgb("#34414c"), light=0.55, dark=0.5, spec=0.22))
    L.paint(L.mask_ellipse(cx, 25.5, 2.7, 2.7), hex_rgb("#3e4b56"))
    spike = L.mask_polygon([(cx - 0.9, 24.0), (cx + 0.9, 24.0), (cx + 0.2, 13.0), (cx - 0.2, 13.0)])
    L.paint(spike, hex_rgb("#2e3943"))

    L.save(os.path.join(OUT, "lighthouse.png"), fade_bottom=14.0)


def glow():
    size = 128
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32)
    r = np.hypot(xs + 0.5 - size / 2, ys + 0.5 - size / 2) / (size / 2)
    alpha = 0.85 * np.exp(-(r / 0.32) ** 2) + 0.35 * np.exp(-(r / 0.62) ** 2)
    alpha *= np.clip((1.0 - r) / 0.12, 0, 1)
    rgb = np.dstack([np.full_like(r, 255), 226 - 30 * r, 168 - 70 * r])
    save_rgba(os.path.join(OUT, "lighthouse_glow.png"), rgb, alpha)


def beam():
    width, height = 512, 128
    ys, xs = np.mgrid[0:height, 0:width].astype(np.float32)
    u = (xs + 0.5) / width
    v = (ys + 0.5 - height / 2) / (height / 2)
    spread = 0.06 + 0.94 * u
    across = np.clip(1.0 - np.abs(v) / spread, 0, 1) ** 1.15
    along = (1.0 - u) ** 1.1 * np.clip(u / 0.03, 0, 1)
    core = np.exp(-(v / (0.08 + 0.25 * u)) ** 2) * (1.0 - u) ** 2 * 0.6
    alpha = np.clip(0.75 * across * along + core, 0, 1)
    rgb = np.dstack([np.full_like(u, 255), np.full_like(u, 238), 200 - 40 * u])
    save_rgba(os.path.join(OUT, "lighthouse_beam.png"), rgb, alpha)


def flare():
    width, height = 256, 64
    ys, xs = np.mgrid[0:height, 0:width].astype(np.float32)
    u = (xs + 0.5 - width / 2) / (width / 2)
    v = (ys + 0.5 - height / 2) / (height / 2)
    streak = np.exp(-(v / 0.07) ** 2) * (1.0 - np.abs(u)) ** 2.2
    halo = np.exp(-((u * 4.0) ** 2 + v ** 2) / 0.18)
    alpha = np.clip(streak + 0.8 * halo, 0, 1)
    rgb = np.dstack([np.full_like(u, 255), np.full_like(u, 244), np.full_like(u, 222)])
    save_rgba(os.path.join(OUT, "lighthouse_flare.png"), rgb, alpha)


def save_rgba(path, rgb, alpha):
    out = np.dstack([np.clip(rgb, 0, 255), np.clip(alpha, 0, 1) * 255.0])
    Image.fromarray((out + 0.5).astype(np.uint8), "RGBA").save(path)


def write_meta(name):
    with open(os.path.join(OUT, name + ".mcmeta"), "w", encoding="utf-8") as handle:
        json.dump({"texture": {"blur": True, "clamp": True}}, handle, indent=2)
        handle.write("\n")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    lighthouse()
    glow()
    beam()
    flare()
    for texture in ("lighthouse.png", "lighthouse_glow.png", "lighthouse_beam.png", "lighthouse_flare.png"):
        write_meta(texture)
    print("written to", OUT)
