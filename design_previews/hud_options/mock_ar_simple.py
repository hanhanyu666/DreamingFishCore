"""右上角「终端眼镜」简洁版：暖色骨白，只保留层级与一条细线。"""
from PIL import Image, ImageDraw
import os

from mock import S, font, tex, HERE
from mock_ar import Holo, PALETTES, SRC_DAY, SRC_NIGHT, label

WARM = dict(PALETTES["warm"])


def text(h, s, gx, gy, rgb, size, spacing=0.0, alpha=255):
    return h.text(s, gx, gy, rgb, size, alpha=alpha, spacing=spacing, glow=False, clean=True)


def simple_hud(img, W, top, with_header):
    h = Holo(img, top, WARM)
    p = WARM
    right = W - 9
    h.lens(W, arc=False, strength=0.8)
    y = 4

    if with_header:
        online_w = text(h, "1 在线", right, y + 5, p["dim"], 16)
        sig_w = h.signal(right - online_w - 4, y + 5, 4, p["dim"])
        text(h, "DREAMINGFISH", right - online_w - 7 - sig_w, y + 5, p["dim"], 15, spacing=0.9)
        y += 8

    # 时钟为视觉重心；日期、星期以小字并排在左侧。
    time_w = text(h, "08:37", right, y + 15, p["main"], 50)
    text(h, "2026.09.29", right - time_w - 3, y + 8.5, p["dim"], 16, spacing=0.2)
    text(h, "星期二", right - time_w - 3, y + 14.5, p["dim"], 16)
    if not with_header:
        text(h, "1 在线", right - time_w - 3 - 42, y + 14.5, p["dim"], 16)
    y += 19

    h.line(right - 96, y, right, y + 0.25, 110, p["main"])
    y += 3

    rank_w = text(h, "OPERATOR", right, y + 7, (226, 110, 96), 15, spacing=0.5)
    title_w = text(h, "萌新鱼友", right - rank_w - 4, y + 7.5, (236, 214, 160), 21)
    name_w = text(h, "Dev", right - rank_w - 4 - title_w - 4, y + 7.5, p["main"], 23)
    skin = tex("steve")
    face = skin.crop((8, 8, 16, 16))
    face.alpha_composite(skin.crop((40, 8, 48, 16)))
    h.c.sprite(face, right - rank_w - 4 - title_w - 4 - name_w - 10, y + 1, 7, 0.95)
    y += 11

    exp_w = text(h, "432 / 880", right, y + 5.5, p["dim"], 15, spacing=0.2)
    bar_right = right - exp_w - 4
    bar_left = bar_right - 60
    h.c.rect(bar_left, y + 2.75, bar_right, y + 3.5, p["dim"] + (70,))
    h.c.rect(bar_left, y + 2.5, bar_left + 60 * 432 / 880, y + 3.75, p["main"] + (220,))
    lv_w = text(h, "9", bar_left - 3, y + 6.5, p["main"], 22)
    text(h, "Lv", bar_left - 3 - lv_w - 1, y + 6, p["dim"], 15)
    y += 10

    ex = right - 9
    for name, remain in (("night_vision", 0.72), ("speed", 0.28)):
        h.c.sprite(tex(name), ex, y, 9, 0.9)
        h.c.rect(ex, y + 10.5, ex + 9, y + 11, p["dim"] + (70,))
        h.c.rect(ex, y + 10.5, ex + 9 * remain, y + 11, p["main"] + (210,))
        ex -= 12


def main():
    day = Image.open(SRC_DAY).convert("RGBA")
    night = Image.open(SRC_NIGHT).convert("RGBA")
    title_bar = 34
    day_clean = day.copy()
    day_clean.alpha_composite(day.crop((600, title_bar, 1600, title_bar + 300)), (1560, title_bar))
    night_clean = night.copy()
    night_clean.alpha_composite(night.crop((700, 0, 1700, 300)), (1560, 0))

    panels = []
    for with_header, name in ((True, "简洁 · 保留顶行（服务器名 + 信号格 + 在线）"),
                              (False, "更简洁 · 去掉顶行，在线人数并入日期旁")):
        for bg, top, bg_name in ((day_clean, title_bar, "白天"), (night_clean, 0, "夜晚")):
            img = bg.copy()
            simple_hud(img, img.width / S, top, with_header)
            panel = img.crop((img.width - 800, top, img.width, top + 260))
            framed = Image.new("RGBA", (panel.width, panel.height + 44), (0, 0, 0, 255))
            framed.alpha_composite(panel, (0, 44))
            label(framed, name, bg_name)
            panels.append(framed)
    pw, ph = panels[0].size
    sheet = Image.new("RGBA", (pw * 2 + 8, ph * 2 + 8), (40, 40, 40, 255))
    for i, p in enumerate(panels):
        sheet.alpha_composite(p, ((i % 2) * (pw + 8), (i // 2) * (ph + 8)))
    sheet.convert("RGB").save(os.path.join(HERE, "top_right_terminal_simple.png"))


if __name__ == "__main__":
    main()
