"""读取结构 NBT 的 size / palette / blocks 数量，用于核对模板是否符合预期。"""
import gzip
import struct
import sys
from pathlib import Path


def read_tag(data, i, tag_type):
    if tag_type == 1:
        return struct.unpack('>b', data[i:i + 1])[0], i + 1
    if tag_type == 2:
        return struct.unpack('>h', data[i:i + 2])[0], i + 2
    if tag_type == 3:
        return struct.unpack('>i', data[i:i + 4])[0], i + 4
    if tag_type == 4:
        return struct.unpack('>q', data[i:i + 8])[0], i + 8
    if tag_type == 5:
        return struct.unpack('>f', data[i:i + 4])[0], i + 4
    if tag_type == 6:
        return struct.unpack('>d', data[i:i + 8])[0], i + 8
    if tag_type == 8:
        n = struct.unpack('>H', data[i:i + 2])[0]
        return data[i + 2:i + 2 + n].decode('utf-8', 'replace'), i + 2 + n
    if tag_type == 9:
        item_type = data[i]
        n = struct.unpack('>i', data[i + 1:i + 5])[0]
        i += 5
        items = []
        for _ in range(n):
            v, i = read_tag(data, i, item_type)
            items.append(v)
        return items, i
    if tag_type == 10:
        out = {}
        while True:
            t = data[i]
            i += 1
            if t == 0:
                break
            n = struct.unpack('>H', data[i:i + 2])[0]
            name = data[i + 2:i + 2 + n].decode('utf-8', 'replace')
            i += 2 + n
            v, i = read_tag(data, i, t)
            out[name] = v
        return out, i
    raise ValueError(f'不支持的 tag 类型 {tag_type}')


def main(path):
    raw = Path(path).read_bytes()
    data = gzip.decompress(raw) if raw[:2] == b'\x1f\x8b' else raw
    t = data[0]
    assert t == 10, f'根不是 Compound（{t}）'
    n = struct.unpack('>H', data[1:3])[0]
    i = 3 + n
    root, _ = read_tag(data, i, 10)
    print(f'文件 {Path(path).name}，{len(raw)} 字节（gzip）')
    print('  顶层键:', sorted(root.keys()))
    print('  size:', root.get('size'))
    print('  DataVersion:', root.get('DataVersion'))
    palette = root.get('palette') or []
    print('  palette:', palette)
    blocks = root.get('blocks') or []
    print('  blocks 条数:', len(blocks), '前 3 个:', blocks[:3])
    print('  entities 条数:', len(root.get('entities') or []))


if __name__ == '__main__':
    main(sys.argv[1])
