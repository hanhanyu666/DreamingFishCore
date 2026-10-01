"""生成聚居地过滤装置的方块贴图（16×16，工作/停机两张）。

与仓库其他 tools 脚本一致：贴图是程序化生成的，改配色只需改常量后重跑，
不必依赖外部画图工具，也避免二进制资源无法审查。

用法：
    python tools/generate_settlement_filter_texture.py

输出：
    src/main/resources/assets/dreamingfishcore/textures/block/settlement_filter.png       （工作）
    src/main/resources/assets/dreamingfishcore/textures/block/settlement_filter_off.png   （停机）
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

SIZE = 16

# 基础金属板
PLATE_ON = (74, 80, 88, 255)
PLATE_OFF = (58, 62, 68, 255)
# 边框与铆钉
EDGE_ON = (44, 48, 54, 255)
EDGE_OFF = (36, 39, 44, 255)
RIVET_ON = (150, 158, 168, 255)
RIVET_OFF = (108, 112, 118, 255)
# 中央格栅：工作时是青绿光，停机时是暗灰
GRILLE_ON = (76, 217, 123, 255)
GRILLE_DIM_ON = (44, 132, 78, 255)
GRILLE_OFF = (86, 90, 96, 255)
GRILLE_DIM_OFF = (62, 66, 72, 255)


def build(active: bool) -> Image.Image:
    plate = PLATE_ON if active else PLATE_OFF
    edge = EDGE_ON if active else EDGE_OFF
    rivet = RIVET_ON if active else RIVET_OFF
    grille = GRILLE_ON if active else GRILLE_OFF
    grille_dim = GRILLE_DIM_ON if active else GRILLE_DIM_OFF

    image = Image.new("RGBA", (SIZE, SIZE), plate)
    pixels = image.load()

    # 外边框
    for i in range(SIZE):
        pixels[i, 0] = edge
        pixels[i, SIZE - 1] = edge
        pixels[0, i] = edge
        pixels[SIZE - 1, i] = edge

    # 四角铆钉（2×2）
    for cx, cy in ((2, 2), (SIZE - 4, 2), (2, SIZE - 4), (SIZE - 4, SIZE - 4)):
        for dx in range(2):
            for dy in range(2):
                pixels[cx + dx, cy + dy] = rivet

    # 中央 8×8 格栅：竖条 + 一条横梁
    left, top, right, bottom = 4, 4, 12, 12
    for x in range(left, right):
        for y in range(top, bottom):
            stripe = (x - left) % 2 == 0
            pixels[x, y] = grille if stripe else grille_dim
    for x in range(left, right):
        pixels[x, top + 3] = edge
        pixels[x, top + 4] = edge

    # 顶部高光，给一点体积感
    for x in range(1, SIZE - 1):
        pixels[x, 1] = tuple(min(255, c + 18) for c in plate[:3]) + (255,)

    return image


def main() -> None:
    target_dir = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / \
        "assets" / "dreamingfishcore" / "textures" / "block"
    target_dir.mkdir(parents=True, exist_ok=True)

    outputs = {
        "settlement_filter.png": build(True),
        "settlement_filter_off.png": build(False),
    }
    for name, image in outputs.items():
        path = target_dir / name
        image.save(path)
        print(f"已写入 {path} ({path.stat().st_size} 字节)")


if __name__ == "__main__":
    main()
