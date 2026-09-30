"""快捷栏与右上角信息区的设计对比图：画在用户游戏截图（GUI 缩放 4）上。"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "tex")
SRC = r"C:/Users/hanhanyu/AppData/Local/Temp/pebrel-paste-YZUL0f.png"
S = 4  # GUI 缩放
FONT = r"C:/Windows/Fonts/msyh.ttc"
FONT_BOLD = r"C:/Windows/Fonts/msyhbd.ttc"

BONE = (232, 226, 210)
BONE_DIM = (168, 163, 150)


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT, size)


def tex(name):
    return Image.open(os.path.join(TEX, name + ".png")).convert("RGBA")


class Canvas:
    """以 GUI 坐标绘制，自动换算成截图像素并与背景做 alpha 混合。"""

    def __init__(self, image, top=0):
        self.image = image
        self.top = top

    def px(self, gx, gy):
        return round(gx * S), round(gy * S + self.top)

    def glow(self, x0, y0, x1, y1, alpha, blur):
        """无硬边的柔和暗底：保证亮背景上的文字可读，又不出现方框。"""
        margin = round(blur * S * 2)
        a = self.px(x0, y0)
        b = self.px(x1, y1)
        w, h = b[0] - a[0] + margin * 2, b[1] - a[1] + margin * 2
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(layer).rounded_rectangle([margin, margin, w - margin, h - margin],
                                                radius=blur * S, fill=(0, 0, 0, alpha))
        layer = layer.filter(ImageFilter.GaussianBlur(blur * S * 0.8))
        self.image.alpha_composite(layer, (a[0] - margin, a[1] - margin))

    def rect(self, x0, y0, x1, y1, rgba, radius=0):
        a = self.px(x0, y0)
        b = self.px(x1, y1)
        w, h = b[0] - a[0], b[1] - a[1]
        if w <= 0 or h <= 0:
            return
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        if radius:
            d.rounded_rectangle([0, 0, w - 1, h - 1], radius=radius * S, fill=rgba)
        else:
            d.rectangle([0, 0, w - 1, h - 1], fill=rgba)
        self.image.alpha_composite(layer, a)

    def outline(self, x0, y0, x1, y1, rgba, width=1.0):
        self.rect(x0, y0, x1, y0 + width, rgba)
        self.rect(x0, y1 - width, x1, y1, rgba)
        self.rect(x0, y0 + width, x0 + width, y1 - width, rgba)
        self.rect(x1 - width, y0 + width, x1, y1 - width, rgba)

    def sprite(self, img, gx, gy, size, alpha=1.0):
        scaled = img.resize((round(size * S), round(size * S)), Image.NEAREST)
        if alpha < 1.0:
            r, g, b, a = scaled.split()
            a = a.point(lambda v: int(v * alpha))
            scaled = Image.merge("RGBA", (r, g, b, a))
        self.image.alpha_composite(scaled, self.px(gx, gy))

    def text(self, s, gx, gy, rgb, size, alpha=255, bold=False, anchor="la"):
        f = font(size, bold)
        layer = Image.new("RGBA", self.image.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        x, y = self.px(gx, gy)
        d.text((x + 2, y + 2), s, font=f, fill=(0, 0, 0, int(alpha * 0.55)), anchor=anchor)
        d.text((x, y), s, font=f, fill=rgb + (alpha,), anchor=anchor)
        self.image.alpha_composite(layer)
        return d.textlength(s, font=f) / S

    def soft_band(self, x0, y0, x1, y1, alpha):
        a = self.px(x0, y0)
        b = self.px(x1, y1)
        w, h = b[0] - a[0], b[1] - a[1]
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        d.ellipse([w * 0.08, h * 0.15, w * 0.92, h * 0.95], fill=(0, 0, 0, alpha))
        layer = layer.filter(ImageFilter.GaussianBlur(h * 0.3))
        self.image.alpha_composite(layer, a)


ITEMS = [("diamond_sword", 1), ("iron_pickaxe", 1), ("bow", 1), ("arrow", 32), ("bread", 12),
         ("cooked_beef", 7), ("water_bucket", 1), (None, 0), ("golden_apple", 3)]
SELECTED = 0
STAMINA, XP, COURAGE, LEVEL = 0.8, 0.45, 0.6, 32
ORIGINAL_BAR_COLORS = [(185, 154, 87), (110, 154, 112), (129, 112, 167)]
SOFT_BAR_COLORS = [(217, 192, 138), (139, 199, 154), (169, 149, 214)]


def draw_items(c, x0, y, step, offset, scale=1.0):
    size = 16 * scale
    for i, (name, count) in enumerate(ITEMS):
        if not name:
            continue
        ix = x0 + i * step + offset
        c.sprite(tex(name), ix, y + offset, size)
        if count > 1:
            c.text(str(count), ix + size + 0.5, y + offset + size + 0.8, (255, 255, 255),
                   round(7.2 * S * scale), bold=True, anchor="rs")


def bar(c, x, y, w, h, ratio, rgb, style):
    if style == "current":
        c.rect(x + 0.25, y + 0.25, x + w + 0.25, y + h + 0.25, (0, 0, 0, 38), radius=h / 2)
        c.rect(x, y, x + w, y + h, (16, 19, 20, 168), radius=h / 2)
        fw = (w - 2) * ratio
        c.rect(x + 1, y + 1, x + 1 + fw, y + h - 1, rgb + (196,), radius=(h - 2) / 2)
        c.rect(x + 1.5, y + 1, x + fw, y + 1.5, (255, 255, 255, 60))
    elif style == "line":
        c.rect(x, y, x + w, y + h, (0, 0, 0, 70))
        c.outline(x, y, x + w, y + h, BONE + (70,), width=0.5)
        c.rect(x + 0.75, y + 0.75, x + 0.75 + (w - 1.5) * ratio, y + h - 0.75, rgb + (225,))
    elif style == "block":
        c.rect(x, y, x + w, y + h, (0, 0, 0, 140))
        c.rect(x, y, x + w * ratio, y + h, rgb + (230,))
    elif style == "float":
        c.rect(x, y, x + w, y + h, (0, 0, 0, 95), radius=h / 2)
        c.rect(x, y, x + w * ratio, y + h, rgb + (225,), radius=h / 2)


def bars(c, cx, y, widths, h, style, colors, icon_size, icon_gap):
    total = sum(widths) + 8
    x = cx - total / 2
    xs = []
    for w, r, col in zip(widths, (STAMINA, XP, COURAGE), colors):
        bar(c, x, y, w, h, r, col, style)
        xs.append(x)
        x += w + 4
    iy = y + h / 2 - icon_size / 2
    c.sprite(tex("stamina_v2"), xs[0] - icon_gap - icon_size, iy, icon_size, 0.85)
    c.sprite(tex("courage_v2"), xs[2] + widths[2] + icon_gap, iy, icon_size, 0.85)
    c.text(str(LEVEL), xs[1] + widths[1] / 2, y - 1.5, (150, 200, 158), 22, anchor="ms")


def hotbar_current(c, cx, H):
    scale = 0.7
    w, h = 184 * scale, 24 * scale
    x0, y0 = cx - w / 2, H - 4 - h
    c.rect(x0, y0, x0 + w, y0 + h, (33, 38, 41, 144), radius=6 * scale)
    c.outline(x0, y0, x0 + w, y0 + h, (118, 125, 130, 60), width=0.25)
    step = 20 * scale
    sx = x0 + 2 * scale + SELECTED * step
    c.rect(sx, y0 + 2 * scale, sx + step, y0 + h - 2 * scale, (99, 107, 112, 86), radius=4 * scale)
    c.rect(sx + 5 * scale, y0 + h - 3 * scale, sx + step - 5 * scale, y0 + h - 1 * scale, (169, 209, 227, 198))
    draw_items(c, x0 + 2 * scale, y0, step, 4 * scale, scale)
    bars(c, cx, round(y0) - 9, (68, 82, 68), 5, "current", ORIGINAL_BAR_COLORS, 10, 4)


def hotbar_line(c, cx, H):
    step, slot = 20, 18
    w = 9 * slot + 8 * 2
    x0, y0 = cx - w / 2, H - 4 - slot
    for i in range(9):
        sx = x0 + i * step
        if i == SELECTED:
            c.rect(sx, y0, sx + slot, y0 + slot, BONE + (30,))
            c.outline(sx, y0, sx + slot, y0 + slot, BONE + (235,), width=0.5)
            corners = ((sx - 1.5, y0 - 1.5, 1, 1), (sx + slot + 1, y0 - 1.5, -1, 1),
                       (sx - 1.5, y0 + slot + 1, 1, -1), (sx + slot + 1, y0 + slot + 1, -1, -1))
            for px, py, dx, dy in corners:
                c.rect(min(px, px + dx * 3), py, max(px, px + dx * 3) + 0.5, py + 0.5, BONE + (235,))
                c.rect(px, min(py, py + dy * 3), px + 0.5, max(py, py + dy * 3) + 0.5, BONE + (235,))
        else:
            c.rect(sx, y0, sx + slot, y0 + slot, (0, 0, 0, 72))
            c.outline(sx, y0, sx + slot, y0 + slot, BONE + (62,), width=0.25)
    draw_items(c, x0, y0, step, 1)
    bars(c, cx, y0 - 8, (56, 58, 56), 4, "line", SOFT_BAR_COLORS, 8, 4)


def hotbar_block(c, cx, H):
    step, slot = 21, 20
    w = 9 * slot + 8
    x0, y0 = cx - w / 2, H - 3 - slot
    for i in range(9):
        sx = x0 + i * step
        c.rect(sx, y0, sx + slot, y0 + slot, (18, 18, 18, 150))
        if i == SELECTED:
            c.outline(sx, y0, sx + slot, y0 + slot, (255, 255, 255, 240), width=1)
        else:
            c.outline(sx, y0, sx + slot, y0 + slot, (140, 140, 140, 190), width=0.25)
    draw_items(c, x0, y0, step, 2)
    bars(c, cx, y0 - 6, (60, 60, 60), 3, "block", ORIGINAL_BAR_COLORS, 8, 4)


def hotbar_float(c, cx, H):
    step = 20
    w = 9 * step
    x0, y0 = cx - w / 2, H - 5 - 18
    c.soft_band(x0 - 24, y0 - 16, x0 + w + 24, H + 2, 120)
    for i in range(9):
        sx = x0 + i * step
        if i == SELECTED:
            c.rect(sx + 1, y0, sx + 19, y0 + 18, BONE + (26,), radius=3)
            c.rect(sx + 3, y0 + 19.5, sx + 17, y0 + 20.5, BONE + (240,))
        else:
            c.rect(sx + 5, y0 + 19.75, sx + 15, y0 + 20.25, BONE + (80,))
    draw_items(c, x0, y0, step, 2)
    bars(c, cx, y0 - 7, (58, 62, 58), 3, "float", SOFT_BAR_COLORS, 8, 4)


def top_right(c, W, expanded):
    right = W - 6
    y = 5
    if expanded:
        c.glow(right - 122, 3, right + 2, 44, 125, 5)
    else:
        c.glow(right - 40, 3, right + 2, 25, 125, 5)
    tw = c.text("08:37", right, y + 6, BONE, 22, anchor="rs")
    sw = c.text(" · ", right - tw, y + 6, BONE_DIM, 22, anchor="rs")
    c.text("1 在线", right - tw - sw, y + 6, BONE, 22, anchor="rs")
    y += 9
    if expanded:
        rank_w = c.text("OPERATOR", right, y + 7, (230, 90, 80), 18, anchor="rs")
        d1 = c.text(" · ", right - rank_w, y + 7, (120, 118, 110), 22, anchor="rs")
        title_w = c.text("萌新鱼友", right - rank_w - d1, y + 7, (240, 225, 170), 22, anchor="rs")
        d2 = c.text(" · ", right - rank_w - d1 - title_w, y + 7, (120, 118, 110), 22, anchor="rs")
        name_w = c.text("Dev", right - rank_w - d1 - title_w - d2, y + 7, BONE, 24, anchor="rs")
        skin = tex("steve")
        face = skin.crop((8, 8, 16, 16))
        face.alpha_composite(skin.crop((40, 8, 48, 16)))
        c.sprite(face, right - rank_w - d1 - title_w - d2 - name_w - 9, y + 0.5, 7)
        y += 10
        c.text("432/880", right, y + 6, BONE_DIM, 18, anchor="rs")
        bx1 = right - 24
        c.rect(bx1 - 50, y + 3, bx1, y + 4.5, (0, 0, 0, 110))
        c.rect(bx1 - 50, y + 3, bx1 - 50 + 50 * 432 / 880, y + 4.5, (230, 167, 75, 230))
        c.text("Lv.9", bx1 - 53, y + 6, (240, 190, 100), 22, anchor="rs")
        y += 9
    ex = right - 11
    for name, remain in (("night_vision", 0.7), ("speed", 0.3)):
        c.sprite(tex(name), ex, y + 1, 9, 0.9)
        c.rect(ex, y + 11, ex + 9, y + 11.5, (0, 0, 0, 90))
        c.rect(ex, y + 11, ex + 9 * remain, y + 11.5, BONE + (200,))
        ex -= 12


def label(img, text, sub=""):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, img.width, 44], fill=(20, 22, 24, 215))
    d.text((16, 6), text, font=font(26, True), fill=(240, 240, 240))
    if sub:
        d.text((16 + d.textlength(text, font=font(26, True)) + 18, 10), sub, font=font(20), fill=(175, 175, 175))


def stack(panels, columns):
    rows = (len(panels) + columns - 1) // columns
    pw, ph = panels[0].size
    sheet = Image.new("RGBA", (pw * columns + 8 * (columns - 1), ph * rows + 8 * (rows - 1)), (40, 40, 40, 255))
    for i, p in enumerate(panels):
        sheet.alpha_composite(p, ((i % columns) * (pw + 8), (i // columns) * (ph + 8)))
    return sheet


def main():
    src = Image.open(SRC).convert("RGBA")
    W, H = src.width / S, src.height / S
    cx = W / 2

    # 用左侧草地盖掉截图里旧的快捷栏区域。
    clean_bottom = src.copy()
    clean_bottom.alpha_composite(src.crop((440, 1200, 1000, 1368)), (1000, 1200))
    clean_bottom.alpha_composite(src.crop((440, 1200, 560, 1368)), (1560, 1200))
    variants = [
        ("当前", "原样恢复后的版本（空闲时缩到 0.7 倍）", hotbar_current),
        ("方案一 · 线稿槽位", "与左下角线稿同一语言：细描边格子，选中格加角标，物品 1:1 整数像素", hotbar_line),
        ("方案二 · 方块槽位", "接近参考服：深色方格、选中白框，朴素清晰", hotbar_block),
        ("方案三 · 悬浮", "去掉格子，只留底部短刻度和一层柔和暗影，最沉浸", hotbar_float),
    ]
    panels = []
    for title, sub, fn in variants:
        img = clean_bottom.copy()
        fn(Canvas(img), cx, H)
        panel = img.crop((720, 1140, 1840, 1368))
        framed = Image.new("RGBA", (panel.width, panel.height + 44), (0, 0, 0, 255))
        framed.alpha_composite(panel, (0, 44))
        label(framed, title, sub)
        panels.append(framed)
    stack(panels, 1).convert("RGB").save(os.path.join(HERE, "hotbar_options.png"))

    # 截图顶部带 34 像素的窗口标题栏；用中间的天空盖掉右上角原有的信息框。
    title_bar = 34
    clean_top = src.copy()
    clean_top.alpha_composite(src.crop((600, title_bar, 1600, title_bar + 300)), (1560, title_bar))
    top_variants = [
        ("当前", "", None),
        ("方案 A · 无框读数", "常驻；去掉底框和现实日期，TPS 只在偏低时出现", lambda c: top_right(c, W, True)),
        ("方案 B · 平时", "只留时间、在线人数和状态效果", lambda c: top_right(c, W, False)),
        ("方案 B · 按住 Alt / 升级 / 称号变化时", "展开成方案 A，几秒后收回", lambda c: top_right(c, W, True)),
    ]
    panels = []
    for title, sub, fn in top_variants:
        img = src.copy() if fn is None else clean_top.copy()
        if fn:
            fn(Canvas(img, title_bar))
        panel = img.crop((1760, title_bar, 2560, title_bar + 250))
        framed = Image.new("RGBA", (panel.width, panel.height + 44), (0, 0, 0, 255))
        framed.alpha_composite(panel, (0, 44))
        label(framed, title, sub)
        panels.append(framed)
    stack(panels, 2).convert("RGB").save(os.path.join(HERE, "top_right_options.png"))


if __name__ == "__main__":
    main()
