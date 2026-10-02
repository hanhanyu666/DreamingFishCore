"""生成两种感染抑制剂的**占位**贴图（16x16）。

为什么是占位：物品模型走 datagen（`ModItemModelProvider.simpleItem`），
引用 `dreamingfishcore:item/<id>`，也就是
`src/main/resources/assets/dreamingfishcore/textures/item/<id>.png`。
正式美术由作者提供；这里先给一张能看出"低剂量/高剂量"区别的瓶子，
免得进游戏是个紫黑方块。

用法：
    python tools/generate_suppressant_textures.py

替换方式：直接用同名 PNG 覆盖即可，不需要动代码。
"""

from __future__ import annotations

from pathlib import Path

try:
    from PIL import Image
except ImportError as exc:  # pragma: no cover - 环境缺 Pillow 时给出明确提示
    raise SystemExit("需要 Pillow：pip install pillow") from exc

SIZE = 16
# (文件名, 瓶身主色, 瓶身高光, 瓶盖色, 液面高光)
DESIGNS = [
    ("infection_suppressant", (58, 122, 148), (140, 205, 226), (176, 176, 184), (214, 240, 250)),
    ("strong_infection_suppressant", (150, 62, 62), (232, 138, 120), (196, 168, 92), (252, 214, 150)),
]


def draw(design: tuple[str, tuple[int, int, int], tuple[int, int, int],
                      tuple[int, int, int], tuple[int, int, int]]) -> Image.Image:
    name, body, highlight, cap, glow = design
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()

    # 瓶盖（y 2..4）
    for y in range(2, 5):
        for x in range(6, 10):
            pixels[x, y] = (*cap, 255)
    # 瓶颈与瓶身（y 5..13），左窄右宽做出圆柱感
    for y in range(5, 14):
        left = 5 if y >= 7 else 6
        right = 10 if y >= 7 else 9
        for x in range(left, right):
            pixels[x, y] = (*body, 255)
    # 高光（左侧一列）
    for y in range(7, 13):
        pixels[6, y] = (*highlight, 255)
    # 液面反光（y 8 的一小段）
    for x in range(7, 9):
        pixels[x, 8] = (*glow, 255)
    # 瓶底阴影
    for x in range(5, 10):
        pixels[x, 13] = (*highlight, 255)

    out = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "assets" / \
        "dreamingfishcore" / "textures" / "item"
    out.mkdir(parents=True, exist_ok=True)
    path = out / f"{name}.png"
    image.save(path)
    return path


def main() -> None:
    for design in DESIGNS:
        print("已写出占位贴图：", draw(design))


if __name__ == "__main__":
    main()
