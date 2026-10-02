"""研究桌的方块资源生成脚本。

生成：
    assets/dreamingfishcore/textures/block/research_table.png
    assets/dreamingfishcore/blockstates/research_table.json
    assets/dreamingfishcore/models/block/research_table.json
    assets/dreamingfishcore/models/item/research_table.json

用法（用装了 Pillow 的 python 跑）：
    python tools/generate_research_table_resources.py

风格参考 generate_settlement_filter_texture.py / generate_spawner_resources.py：
都是"少即是多"的 16x16 手绘贴图，靠明暗与一两个强调色区分功能，不追求写实。
研究桌的主题是"桌面 + 摊开的稿纸 + 一点黄铜"。
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "dreamingfishcore"

TEXTURE_DIR = ASSETS / "textures" / "block"
BLOCKSTATE_DIR = ASSETS / "blockstates"
BLOCK_MODEL_DIR = ASSETS / "models" / "block"
ITEM_MODEL_DIR = ASSETS / "models" / "item"

WOOD_DARK = (66, 46, 30, 255)
WOOD = (104, 74, 46, 255)
WOOD_LIGHT = (140, 102, 62, 255)
WOOD_HIGHLIGHT = (166, 126, 80, 255)
PARCHMENT = (226, 212, 180, 255)
PARCHMENT_DARK = (192, 174, 138, 255)
INK = (46, 42, 50, 255)
BRASS = (198, 158, 76, 255)
BRASS_DARK = (140, 108, 48, 255)


def build_texture() -> Image.Image:
    image = Image.new("RGBA", (16, 16), WOOD)
    pixels = image.load()

    # 桌面：横向木板 + 板缝，四周压深作出边框感。
    for y in range(16):
        for x in range(16):
            if y in (0, 7, 15):
                pixels[x, y] = WOOD_DARK
            elif y == 1 or y == 8:
                pixels[x, y] = WOOD_LIGHT
            elif y in (6, 14):
                pixels[x, y] = WOOD_DARK
            elif (x + y * 3) % 7 == 0:
                pixels[x, y] = WOOD_LIGHT
            else:
                pixels[x, y] = WOOD

    # 左右两条竖缝，让木板看起来是拼起来的。
    for y in range(1, 15):
        if y not in (7,):
            pixels[5, y] = WOOD_DARK
            pixels[11, y] = WOOD_DARK

    # 桌面边框高光（上、左），对应打光方向。
    for x in range(1, 15):
        pixels[x, 1] = WOOD_HIGHLIGHT
    for y in range(1, 15):
        pixels[1, y] = WOOD_HIGHLIGHT

    # 摊开的稿纸。
    for y in range(4, 12):
        for x in range(4, 12):
            pixels[x, y] = PARCHMENT
    for x in range(4, 12):
        pixels[x, 4] = PARCHMENT_DARK
        pixels[x, 11] = PARCHMENT_DARK
    for y in range(4, 12):
        pixels[4, y] = PARCHMENT_DARK
        pixels[11, y] = PARCHMENT_DARK

    # 稿纸上的字迹：几行长短不一、彼此不挨着，否则会糊成一块黑斑。
    for y, (start, end) in {6: (6, 10), 8: (6, 11), 10: (6, 9)}.items():
        for x in range(start, end):
            pixels[x, y] = INK

    # 右上角一小块黄铜压纸角，和刷怪箱/过滤装置的金属件呼应。
    pixels[12, 3] = BRASS
    pixels[13, 3] = BRASS
    pixels[12, 4] = BRASS_DARK
    pixels[13, 4] = BRASS_DARK
    pixels[13, 3] = BRASS

    return image


def main() -> None:
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    BLOCKSTATE_DIR.mkdir(parents=True, exist_ok=True)
    BLOCK_MODEL_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_MODEL_DIR.mkdir(parents=True, exist_ok=True)

    build_texture().save(TEXTURE_DIR / "research_table.png")

    (BLOCKSTATE_DIR / "research_table.json").write_text(
        json.dumps({"variants": {"": {"model": "dreamingfishcore:block/research_table"}}},
                   indent=2, sort_keys=True) + "\n",
        encoding="utf-8")

    (BLOCK_MODEL_DIR / "research_table.json").write_text(
        json.dumps({
            "parent": "minecraft:block/cube_all",
            "textures": {"all": "dreamingfishcore:block/research_table"},
        }, indent=2, sort_keys=True) + "\n",
        encoding="utf-8")

    (ITEM_MODEL_DIR / "research_table.json").write_text(
        json.dumps({"parent": "dreamingfishcore:block/research_table"}, indent=2) + "\n",
        encoding="utf-8")

    print("研究桌资源已生成：纹理 1、blockstate 1、模型 2")


if __name__ == "__main__":
    main()
