"""生成刷怪箱的方块资源：4 套外观贴图 + blockstate + 方块/物品模型。

与仓库其它 tools 脚本一致：贴图与多变体 JSON 都是程序化生成的 ——
刷怪箱的 blockstate 有 4 外观 × 2 工作 × 2 红石 = 16 个变体，手写极易出错。

用法：
    python tools/generate_spawner_resources.py

输出（全部在 src/main/resources 下）：
    assets/dreamingfishcore/textures/block/spawner_skin0..3.png
    assets/dreamingfishcore/blockstates/spawner.json
    assets/dreamingfishcore/models/block/spawner_skin0..3.json
    assets/dreamingfishcore/models/item/spawner.json
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

SIZE = 16
SKINS = 4

# 每套外观：底座、边框、铆钉、笼栏、能量核心
SKIN_PALETTES = [
    {  # 0 铁灰（默认）
        "plate": (74, 80, 88, 255),
        "edge": (44, 48, 54, 255),
        "rivet": (150, 158, 168, 255),
        "bar": (110, 118, 128, 255),
        "core": (200, 90, 90, 255),
    },
    {  # 1 腐化（暗绿）
        "plate": (58, 72, 54, 255),
        "edge": (34, 44, 32, 255),
        "rivet": (120, 150, 110, 255),
        "bar": (86, 112, 78, 255),
        "core": (140, 210, 90, 255),
    },
    {  # 2 军事（橄榄 + 警示黄）
        "plate": (86, 84, 58, 255),
        "edge": (48, 46, 30, 255),
        "rivet": (150, 146, 100, 255),
        "bar": (118, 112, 70, 255),
        "core": (235, 200, 60, 255),
    },
    {  # 3 发光（深青 + 亮青核心）
        "plate": (48, 64, 74, 255),
        "edge": (26, 38, 46, 255),
        "rivet": (120, 170, 180, 255),
        "bar": (74, 108, 120, 255),
        "core": (90, 240, 235, 255),
    },
]


def build_skin(palette: dict) -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), palette["plate"])
    pixels = image.load()

    # 外边框
    for i in range(SIZE):
        pixels[i, 0] = palette["edge"]
        pixels[i, SIZE - 1] = palette["edge"]
        pixels[0, i] = palette["edge"]
        pixels[SIZE - 1, i] = palette["edge"]

    # 四角铆钉
    for cx, cy in ((2, 2), (SIZE - 4, 2), (2, SIZE - 4), (SIZE - 4, SIZE - 4)):
        for dx in range(2):
            for dy in range(2):
                pixels[cx + dx, cy + dy] = palette["rivet"]

    # 中央笼栏：竖栏 + 上下横梁，中间留一个"能量核心"格
    left, top, right, bottom = 3, 3, 13, 13
    for x in range(left, right):
        pixels[x, top] = palette["edge"]
        pixels[x, bottom - 1] = palette["edge"]
    for y in range(top, bottom):
        for x in range(left, right):
            if (x - left) % 3 == 0:
                pixels[x, y] = palette["bar"]
    # 核心：中央 2×2
    for x in (7, 8):
        for y in (7, 8):
            pixels[x, y] = palette["core"]

    # 顶部高光
    for x in range(1, SIZE - 1):
        pixels[x, 1] = tuple(min(255, c + 16) for c in palette["plate"][:3]) + (255,)

    return image


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    assets = root / "src" / "main" / "resources" / "assets" / "dreamingfishcore"
    texture_dir = assets / "textures" / "block"
    model_dir = assets / "models" / "block"
    item_dir = assets / "models" / "item"
    blockstate_dir = assets / "blockstates"
    for directory in (texture_dir, model_dir, item_dir, blockstate_dir):
        directory.mkdir(parents=True, exist_ok=True)

    for index, palette in enumerate(SKIN_PALETTES):
        texture_path = texture_dir / f"spawner_skin{index}.png"
        build_skin(palette).save(texture_path)
        model = {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": f"dreamingfishcore:block/spawner_skin{index}"},
        }
        (model_dir / f"spawner_skin{index}.json").write_text(
            json.dumps(model, indent=2) + "\n", encoding="utf-8")

    # 16 个变体：外观决定模型，工作/红石只影响是否发光（由方块属性 lightLevel 提供），
    # 因此同一个外观的四个组合共用一份模型。
    variants = {}
    for skin in range(SKINS):
        for active in (False, True):
            for powered in (False, True):
                key = f"active={str(active).lower()},powered={str(powered).lower()},skin={skin}"
                variants[key] = {"model": f"dreamingfishcore:block/spawner_skin{skin}"}
    (blockstate_dir / "spawner.json").write_text(
        json.dumps({"variants": variants}, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    (item_dir / "spawner.json").write_text(
        json.dumps({"parent": "dreamingfishcore:block/spawner_skin0"}, indent=2) + "\n",
        encoding="utf-8")

    print(f"已生成 {SKINS} 套外观贴图、{len(variants)} 个 blockstate 变体与模型")


if __name__ == "__main__":
    main()
