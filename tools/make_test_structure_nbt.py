"""生成 gametest 用的「场地模板」结构 NBT（全空气，只提供包围盒尺寸）。

为什么需要它
------------
gametest 框架的 `StructureUtils.forceLoadChunks` 是**按结构模板的包围盒**去
`level.setChunkForced` 的，而 `template = "empty"` 指向的原版空模板只有一格大 ——
于是只有「结构原点所在的那一个区块」被强加载，其余区块只是被 `setBlock` 顺带加载进来、
`chunkVisibility` 里没有登记（该登记由 `ChunkMap` 在每个 tick 回调写入），
**放在那里的实体会被当成"不可见/不可 tick"，下一帧甚至会被 `UNLOADED_TO_CHUNK` 清掉**。

而结构原点落在区块内的偏移是随机的，所以场地事实上横跨两个区块 —— 这会让 gametest 随机红：
症状是「实体明明加进了关卡（`addFreshEntity` 返回 true）却查不到」（`ServerLevel#getEntity` /
`getEntitiesOfClass` 都看不见），以及「弹丸飞过区块边界后凭空消失」。

用自己的大模板（覆盖整个场地）后，框架会在**测试开始前**（更早的那个 tick）就把场地覆盖的
区块全部强制加载并登记可见性，测试体里拿到的世界状态就是完整的。

产物
----
`src/main/resources/data/dreamingfishcore/structure/<name>.nbt`

注意：本工具只给 `size`（包围盒）和一份全空气调色板，**不写任何方块** ——
场地由测试自己 `setBlock` 搭，这里只负责把区块"锚"住。

用法
----
    python tools/make_test_structure_nbt.py                 # 默认 24x1x24 -> commander_arena.nbt
    python tools/make_test_structure_nbt.py --name foo --size 32 1 32
"""

from __future__ import annotations

import argparse
import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "src/main/resources/data/dreamingfishcore/structure"

TAG_INT = 3
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_END = 0

#: 1.21.1 的 DataVersion（见 SharedConstants.WORLD_VERSION）
DATA_VERSION = 3955


def _name(text: str) -> bytes:
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _named(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes([tag_type]) + _name(name) + payload


def _string_value(text: str) -> bytes:
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _int(name: str, value: int) -> bytes:
    return _named(TAG_INT, name, struct.pack(">i", value))


def _list_of(name: str, item_type: int, items: list[bytes]) -> bytes:
    return _named(TAG_LIST, name, bytes([item_type]) + struct.pack(">i", len(items)) + b"".join(items))


def build(size: tuple[int, int, int]) -> bytes:
    """全空气的模板：只有一个空气调色板项，`blocks` / `entities` 都为空。"""
    size_tag = _list_of("size", TAG_INT,
                        [struct.pack(">i", v) for v in size])
    # 列表里的 compound 项不带名字，直接是「若干具名 tag + TAG_End」
    air_entry = _named(TAG_STRING, "Name", _string_value("minecraft:air")) + bytes([TAG_END])
    palette_tag = _list_of("palette", TAG_COMPOUND, [air_entry])
    blocks_tag = _list_of("blocks", TAG_COMPOUND, [])
    entities_tag = _list_of("entities", TAG_COMPOUND, [])

    body = (size_tag + palette_tag + blocks_tag + entities_tag
            + _int("DataVersion", DATA_VERSION) + bytes([TAG_END]))
    return bytes([TAG_COMPOUND]) + _name("") + body


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--name", default="commander_arena")
    ap.add_argument("--size", type=int, nargs=3, default=[24, 1, 24])
    args = ap.parse_args()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out = OUT_DIR / f"{args.name}.nbt"
    raw = build(tuple(args.size))
    out.write_bytes(gzip.compress(raw, 9))
    print(f"写出 {out}（{out.stat().st_size} 字节，解压后 {len(raw)}）")
    print(f"  size = {list(args.size)}，全空气、无方块 —— "
          f"框架会强加载这个包围盒覆盖到的全部区块")


if __name__ == "__main__":
    main()
