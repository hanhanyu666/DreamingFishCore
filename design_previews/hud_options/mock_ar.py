"""右上角「终端眼镜」设计稿：模拟玩家终端投射在镜片上的界面。"""
from PIL import Image, ImageDraw, ImageFilter
import math
import os

from mock import Canvas, S, font, tex, HERE

SRC_DAY = r"C:/Users/hanhanyu/AppData/Local/Temp/pebrel-paste-YZUL0f.png"
SRC_NIGHT = r"D:/Desktop/DreamingFishCore-1.21.1/run/screenshots/2026-06-08_06.11.51.png"

PALETTES = {
    "cold": {"main": (222, 246, 255), "accent": (122, 214, 255), "dim": (152, 194, 214), "tint": (6, 14, 24)},
    "warm": {"main": (238, 232, 216), "accent": (232, 196, 130), "dim": (196, 186, 164), "tint": (18, 14, 8)},
}


class Holo:
    """投射层：文字带辉光与轻微色散，线条为细发丝线。"""

    def __init__(self, image, top, palette):
        self.c = Canvas(image, top)
        self.image = image
        self.p = palette

    def lens(self, W, arc=True, strength=1.0):
        # 镜片边缘的暗角：从右上角向内渐隐。
        w, h = round(230 * S), round(120 * S)
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        pixels = layer.load()
        tint = self.p["tint"]
        for y in range(0, h, 2):
            for x in range(0, w, 2):
                dx = (w - x) / w
                dy = y / h
                d = math.sqrt((dx * 0.82) ** 2 + (dy * 1.05) ** 2)
                a = max(0.0, 1.0 - d) ** 1.25 * 205 * strength
                for oy in (0, 1):
                    for ox in (0, 1):
                        if x + ox < w and y + oy < h:
                            pixels[x + ox, y + oy] = tint + (int(a),)
        self.image.alpha_composite(layer, self.c.px(W - 230, 0))
        if not arc:
            return
        # 镜框弧线：只在角落露出一小段，提示“透过镜片看”。
        arc = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(arc)
        # 以右上角外侧为圆心的一段弧，贴着暗角的内缘。
        cx, cy, r = w + 20 * S, -40 * S, 200 * S
        d.arc([cx - r, cy - r, cx + r, cy + r], start=112, end=158,
              fill=self.p["accent"] + (95,), width=3)
        arc = arc.filter(ImageFilter.GaussianBlur(1.2))
        self.image.alpha_composite(arc, self.c.px(W - 230, 0))

    def text(self, s, gx, gy, rgb, size, alpha=255, anchor="rs", spacing=0.0, glow=True, clean=False):
        f = font(size)
        x, y = self.c.px(gx, gy)
        layer = Image.new("RGBA", self.image.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        if spacing:
            widths = [d.textlength(ch, font=f) + spacing * S for ch in s]
            total = sum(widths) - spacing * S
            cursor = x - total if anchor.startswith("r") else x
            positions = []
            for ch, w in zip(s, widths):
                positions.append((cursor, ch))
                cursor += w
            draw_anchor = "l" + anchor[1]
        else:
            total = d.textlength(s, font=f)
            positions = [(x, s)]
            draw_anchor = anchor
        if glow:
            g = Image.new("RGBA", self.image.size, (0, 0, 0, 0))
            gd = ImageDraw.Draw(g)
            for px, ch in positions:
                gd.text((px, y), ch, font=f, fill=self.p["accent"] + (int(alpha * 0.55),), anchor=draw_anchor)
            g = g.filter(ImageFilter.GaussianBlur(3))
            self.image.alpha_composite(g)
        for px, ch in positions:
            if clean:
                # 简洁版：只有一层柔和投影，没有色散。
                d.text((px + 2, y + 2), ch, font=f, fill=(0, 0, 0, int(alpha * 0.45)), anchor=draw_anchor)
            else:
                d.text((px - 1, y), ch, font=f, fill=(255, 90, 90, int(alpha * 0.22)), anchor=draw_anchor)
                d.text((px + 1, y), ch, font=f, fill=(90, 210, 255, int(alpha * 0.22)), anchor=draw_anchor)
            d.text((px, y), ch, font=f, fill=rgb + (alpha,), anchor=draw_anchor)
        self.image.alpha_composite(layer)
        return total / S

    def line(self, x0, y0, x1, y1, alpha=150, rgb=None):
        self.c.rect(x0, y0, x1, y1, (rgb or self.p["accent"]) + (alpha,))

    def signal(self, right, base_y, level, rgb):
        # 四格信号：以服务器 TPS 表示连接质量。
        x = right
        for i in range(3, -1, -1):
            h = 1.5 + i * 1.2
            filled = i < level
            x -= 1.5
            self.c.rect(x, base_y - h, x + 1, base_y, rgb + ((230 if filled else 60),))
            x -= 0.75
        return right - x

    def ring(self, cx, cy, radius, progress, rgb):
        a = self.c.px(cx - radius, cy - radius)
        b = self.c.px(cx + radius, cy + radius)
        layer = Image.new("RGBA", self.image.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        d.ellipse([a, b], outline=rgb + (60,), width=2)
        d.arc([a, b], start=-90, end=-90 + 360 * progress, fill=rgb + (230,), width=3)
        self.image.alpha_composite(layer)


def terminal_hud(img, W, top, palette):
    h = Holo(img, top, palette)
    p = palette
    right = W - 9
    h.lens(W)

    # 括号框：右侧竖线与上下短横，像镜片上的取景标记。
    h.line(right + 3, 4, right + 3.5, 60, 120)
    h.line(right - 3, 4, right + 3.5, 4.5, 170)
    h.line(right - 3, 59.5, right + 3.5, 60, 170)
    h.line(right + 2.5, 30, right + 4, 33, 220)

    # 第一行：设备名、信号格、在线人数。
    online_w = h.text("1", right, 9, p["main"], 18, spacing=0.3, glow=False)
    h.c.rect(right - online_w - 3.2, 6.6, right - online_w - 1.7, 8.1, (120, 230, 170, 240))
    sig_w = h.signal(right - online_w - 6, 9, 4, p["accent"])
    h.text("DREAMINGFISH  LINK", right - online_w - 8 - sig_w, 9, p["dim"], 15, spacing=0.9, glow=False)

    # 时钟：大号时间为视觉重心，日期与星期以小字并列。
    time_w = h.text("08:37", right, 24, p["main"], 50)
    h.text("2026.09.29", right - time_w - 3, 17.5, p["dim"], 17, spacing=0.2, glow=False)
    h.text("星期二", right - time_w - 3, 23.5, p["dim"], 17, glow=False)

    # 细分隔线与刻度。
    h.line(right - 108, 27.5, right, 27.75, 90)
    for i in range(0, 13):
        tx = right - i * 9
        h.line(tx - 0.25, 27.5, tx, 28.5 if i % 3 else 29.5, 150)

    # 身份行：头像取景框、名字、称号、Rank 标签。
    rank = "OPERATOR"
    rank_w = h.text(rank, right - 2, 38, (240, 104, 94), 15, spacing=0.6, glow=False)
    h.c.outline(right - rank_w - 4, 32.25, right + 0.5, 39.5, (240, 104, 94, 160), width=0.25)
    title_w = h.text("萌新鱼友", right - rank_w - 7, 38.5, (244, 226, 170), 21)
    dot_w = h.text("·", right - rank_w - 7 - title_w - 1.5, 38.5, p["dim"], 21, glow=False)
    name_w = h.text("Dev", right - rank_w - 7 - title_w - dot_w - 3, 38.5, p["main"], 23)
    ax = right - rank_w - 7 - title_w - dot_w - name_w - 14
    skin = tex("steve")
    face = skin.crop((8, 8, 16, 16))
    face.alpha_composite(skin.crop((40, 8, 48, 16)))
    h.c.sprite(face, ax + 1, 32, 7, 0.95)
    for (x0, y0, dx, dy) in ((ax, 31, 1, 1), (ax + 9, 31, -1, 1), (ax, 40, 1, -1), (ax + 9, 40, -1, -1)):
        h.line(min(x0, x0 + dx * 2), y0, max(x0, x0 + dx * 2) + 0.25, y0 + 0.25, 200)
        h.line(x0, min(y0, y0 + dy * 2), x0 + 0.25, max(y0, y0 + dy * 2) + 0.25, 200)

    # 等级：分段进度条。
    exp_w = h.text("432 / 880", right, 48.5, p["dim"], 15, spacing=0.3, glow=False)
    segments, seg_w, gap = 20, 2.25, 0.75
    bar_right = right - exp_w - 3
    filled = 20 * 432 / 880
    for i in range(segments):
        sx = bar_right - (segments - i) * (seg_w + gap) + gap
        on = i < int(filled)
        partial = i == int(filled)
        h.c.rect(sx, 45.5, sx + seg_w, 47.5, (p["accent"] if on else p["dim"]) + ((230 if on else 55),))
        if partial:
            h.c.rect(sx, 45.5, sx + seg_w * (filled - int(filled)), 47.5, p["accent"] + (230,))
    lv_x = bar_right - segments * (seg_w + gap) - 2
    lw = h.text("9", lv_x, 49, p["main"], 24)
    h.text("LV", lv_x - lw - 1, 48.5, p["dim"], 14, spacing=0.4, glow=False)

    # 状态效果：带倒计时圆环的图标。
    ex = right - 5.5
    for name, remain in (("night_vision", 0.72), ("speed", 0.28)):
        h.ring(ex, 56 + 1, 5.2, remain, p["accent"])
        h.c.sprite(tex(name), ex - 3.5, 56 + 1 - 3.5, 7, 0.95)
        ex -= 13


def label(img, text, sub=""):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, img.width, 44], fill=(20, 22, 24, 230))
    d.text((16, 6), text, font=font(26), fill=(240, 240, 240))
    if sub:
        d.text((16 + d.textlength(text, font=font(26)) + 18, 10), sub, font=font(20), fill=(175, 175, 175))


def main():
    day = Image.open(SRC_DAY).convert("RGBA")
    night = Image.open(SRC_NIGHT).convert("RGBA")
    title_bar = 34
    day_clean = day.copy()
    day_clean.alpha_composite(day.crop((600, title_bar, 1600, title_bar + 300)), (1560, title_bar))
    night_clean = night.copy()
    night_clean.alpha_composite(night.crop((700, 0, 1700, 300)), (1560, 0))

    panels = []
    for key, name in (("cold", "冷色全息"), ("warm", "暖色骨白（与左下角同色系）")):
        for bg, top, bg_name in ((day_clean, title_bar, "白天"), (night_clean, 0, "夜晚")):
            img = bg.copy()
            W = img.width / S
            terminal_hud(img, W, top, PALETTES[key])
            panel = img.crop((img.width - 900, top, img.width, top + 300))
            framed = Image.new("RGBA", (panel.width, panel.height + 44), (0, 0, 0, 255))
            framed.alpha_composite(panel, (0, 44))
            label(framed, f"终端眼镜 · {name}", bg_name)
            panels.append(framed)
    pw, ph = panels[0].size
    sheet = Image.new("RGBA", (pw * 2 + 8, ph * 2 + 8), (40, 40, 40, 255))
    for i, p in enumerate(panels):
        sheet.alpha_composite(p, ((i % 2) * (pw + 8), (i // 2) * (ph + 8)))
    sheet.convert("RGB").save(os.path.join(HERE, "top_right_terminal.png"))


if __name__ == "__main__":
    main()
