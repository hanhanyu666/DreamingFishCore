"""生成 gametest 用的空结构模板（3x3x3，地上一层石台 + 空气）。

为什么需要它：NeoForge 的 gametest 必须挂在一个结构模板上，而原版与 NeoForge 都没有提供
"空模板"（原版只有具体玩法的结构）。手写 NBT 而不是用结构方块导出，是为了让这个文件
可复现、可审查。

用法：
    python tools/generate_empty_gametest_structure.py

输出：
    src/main/resources/data/dreamingfishcore/structure/empty.nbt
"""

from __future__ import annotations

import gzip
import struct
from pathlib import Path

# 1.21.1 的 DataVersion；缺省为 0 会让 DataFixer 把结构当成远古格式处理。
DATA_VERSION = 3955
SIZE = (3, 3, 3)

TAG_END = 0
TAG_INT = 3
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10

# 调色板：0 = 石台，1 = 空气。
PALETTE = [("minecraft:stone", 0), ("minecraft:air", 1)]


def _name(text: str) -> bytes:
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _int(name: str, value: int) -> bytes:
    return bytes([TAG_INT]) + _name(name) + struct.pack(">i", value)


def _string_payload(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _compound_payload(payload: bytes) -> bytes:
    return payload + bytes([TAG_END])


def _list(name: str, element_type: int, payloads: list[bytes]) -> bytes:
    body = struct.pack(">i", len(payloads)) + b"".join(payloads)
    return bytes([TAG_LIST]) + _name(name) + bytes([element_type]) + body


def build() -> bytes:
    # size 是 3 个 int 的列表（TAG_List/TAG_Int）
    size = _list("size", TAG_INT, [struct.pack(">i", v) for v in SIZE])

    # palette：名称来自 DataFixer 的映射，这里只需要 Name 字段
    palette_entries = [
        _compound_payload(bytes([TAG_STRING]) + _name("Name") + _string_payload(name))
        for name, _ in PALETTE
    ]
    palette = _list("palette", TAG_COMPOUND, palette_entries)

    # blocks：只有地面一层放石台（state=0），其余位置默认就是空气，不需要写出来。
    blocks_payloads = []
    width, _, depth = SIZE
    for x in range(width):
        for z in range(depth):
            pos = _list("pos", TAG_INT, [struct.pack(">i", v) for v in (x, 0, z)])
            blocks_payloads.append(_compound_payload(pos + _int("state", 0)))
    blocks = _list("blocks", TAG_COMPOUND, blocks_payloads)

    entities = _list("entities", TAG_COMPOUND, [])

    root = (
        _int("DataVersion", DATA_VERSION)
        + size
        + palette
        + blocks
        + entities
    )
    # 根标签名称为空字符串
    return bytes([TAG_COMPOUND]) + _name("") + _compound_payload(root)


def main() -> None:
    target = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "data" / \
        "dreamingfishcore" / "structure" / "empty.nbt"
    target.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(target, "wb") as handle:
        handle.write(build())
    print(f"已写入 {target} ({target.stat().st_size} 字节)")


if __name__ == "__main__":
    main()
