"""Build a vanilla Java 1.21.1 hospital structure and installable data pack.

Uses only Python's standard library. Coordinates are local; north entrance is -Z.
Run: python tools/generate_hospital.py
"""
import gzip
import json
import struct
import zipfile
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / 'artifacts' / 'hospital'
SIZE = (47, 23, 43)
grid = {}
tiles = {}


def put(x, y, z, block):
    assert all(0 <= p < s for p, s in zip((x, y, z), SIZE))
    grid[x, y, z] = block


def fill(x1, y1, z1, x2, y2, z2, block):
    for y in range(y1, y2 + 1):
        for z in range(z1, z2 + 1):
            for x in range(x1, x2 + 1):
                put(x, y, z, block)


def sign(x, y, z, lines):
    put(x, y, z, 'oak_wall_sign[facing=north,waterlogged=false]')
    tiles[x, y, z] = {'id': (8, 'minecraft:sign'), 'front_text': (10, {
        'messages': (9, (8, [json.dumps({'text': s}, ensure_ascii=False) for s in (lines + [''] * 4)[:4]])),
        'color': (8, 'black'), 'has_glowing_text': (1, 0)})}


def bed(x, y, z):
    put(x, y, z, 'white_bed[facing=south,part=foot,occupied=false]')
    put(x, y, z + 1, 'white_bed[facing=south,part=head,occupied=false]')
    put(x + 1, y, z + 1, 'white_concrete')
    put(x + 1, y + 1, z + 1, 'flower_pot')


def build():
    fill(0, 0, 0, 46, 22, 42, 'air')
    fill(0, 0, 0, 46, 0, 42, 'stone_bricks')
    fill(0, 1, 0, 46, 1, 42, 'smooth_stone')
    # Forecourt: ambulance lane, zebra crossing, planted borders.
    fill(1, 1, 1, 45, 1, 5, 'gray_concrete')
    for x in range(3, 45, 6):
        fill(x, 1, 3, x + 2, 1, 3, 'white_concrete')
    for x in range(21, 27, 2):
        fill(x, 1, 1, x, 1, 5, 'white_concrete')
    for x1, x2 in [(2, 16), (31, 44)]:
        fill(x1, 2, 7, x2, 2, 8, 'stone_bricks')
        fill(x1, 3, 7, x2, 3, 8, 'azalea_leaves[persistent=true]')
    # Main volume: 41 x 31, three floors, clear interiors 4 blocks high.
    fill(3, 2, 10, 43, 17, 40, 'white_concrete')
    for floor in range(3):
        y = 2 + floor * 5
        fill(4, y + 1, 11, 42, y + 4, 39, 'air')
        fill(4, y, 11, 42, y, 39, 'smooth_quartz')
        # Pale blue continuous window bands with structural piers.
        for a, b in [(5, 11), (14, 20), (26, 32), (35, 41)]:
            for z in [10, 40]:
                fill(a, y + 2, z, b, y + 3, z, 'light_blue_stained_glass')
        for x in [3, 43]:
            for a in [12, 20, 28, 36]:
                fill(x, y + 2, a, x, y + 3, min(a + 3, 39), 'light_blue_stained_glass')
        # Central corridor, room partitions, open two-block doorways.
        for x in [19, 27]:
            fill(x, y + 1, 11, x, y + 4, 39, 'white_concrete')
            for z in [14, 23, 33]:
                fill(x, y + 1, z, x, y + 2, z + 1, 'air')
        fill(22, y, 11, 24, y, 39, 'light_blue_concrete')
        for z in [19, 29]:
            fill(4, y + 1, z, 18, y + 4, z, 'white_concrete')
            fill(28, y + 1, z, 42, y + 4, z, 'white_concrete')
        for x in [8, 15, 23, 31, 38]:
            for z in [14, 24, 34]:
                put(x, y + 4, z, 'sea_lantern')
        # Furniture: beds / examination couches, desks, cabinets.
        for x in [6, 12, 30, 36]:
            for z in [22, 32]:
                bed(x, y + 1, z)
        for x in [5, 29]:
            fill(x, y + 1, 17, x + 4, y + 1, 17, 'smooth_quartz')
            put(x + 2, y + 2, 17, 'black_concrete')
            put(x + 6, y + 1, 17, 'barrel[facing=north,open=false]')
            for offset in [0, 3, 6]:
                put(x + offset, y + 1, 13, 'quartz_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
        sign(23, y + 3, 39, [f'{floor + 1}F', ['接待 / 急诊', '门诊 / 检查', '住院 / 护理'][floor], '楼梯在走廊南端'])
    # Lobby connection and reception counter.
    fill(19, 3, 11, 19, 5, 18, 'air')
    fill(20, 3, 17, 26, 3, 18, 'smooth_quartz')
    fill(20, 4, 18, 26, 4, 18, 'cyan_concrete')
    sign(23, 4, 17, ['医院', '接待 / 挂号', 'RECEPTION'])
    # Main entry, emergency side entry, accessible steps from courtyard.
    fill(21, 3, 10, 25, 5, 10, 'air')
    fill(20, 2, 9, 26, 2, 9, 'quartz_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
    fill(18, 6, 7, 28, 6, 12, 'cyan_concrete')
    for x in [18, 28]:
        fill(x, 2, 7, x, 5, 7, 'smooth_quartz')
    fill(43, 3, 22, 43, 5, 24, 'air')
    fill(44, 2, 21, 44, 2, 25, 'quartz_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
    fill(42, 6, 20, 46, 6, 26, 'red_concrete')
    # Straight stairs at rear of central corridor, with 3-block headroom.
    for y in [2, 7]:
        for i in range(5):
            fill(20, y + 1 + i, 32 + i, 22, y + 4 + i, 32 + i, 'air')
            fill(20, y + 1 + i, 32 + i, 22, y + 1 + i, 32 + i,
                 'quartz_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
        fill(20, y + 5, 37, 22, y + 5, 38, 'smooth_quartz')
    # Roof parapets, mechanical room, ventilation units.
    for z in [10, 40]:
        fill(3, 18, z, 43, 18, z, 'smooth_quartz')
    for x in [3, 43]:
        fill(x, 18, 10, x, 18, 40, 'smooth_quartz')
    fill(31, 18, 30, 40, 20, 37, 'light_gray_concrete')
    for x in [8, 13]:
        fill(x, 18, 32, x + 2, 18, 35, 'polished_andesite')
        fill(x, 19, 32, x + 2, 19, 35, 'iron_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
    # Prominent medical cross above entrance.
    fill(20, 18, 10, 26, 22, 10, 'white_concrete')
    fill(23, 18, 9, 23, 22, 9, 'red_concrete')
    fill(21, 20, 9, 25, 20, 9, 'red_concrete')
    for x in [1, 45]:
        for z in [6, 18, 38]:
            fill(x, 2, z, x, 4, z, 'polished_andesite')
            put(x, 5, z, 'sea_lantern')


def string(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def payload(t, value):
    if t == 1:
        return struct.pack('>b', value)
    if t == 3:
        return struct.pack('>i', value)
    if t == 8:
        return string(value)
    if t == 9:
        child, items = value
        return bytes([child]) + struct.pack('>i', len(items)) + b''.join(payload(child, v) for v in items)
    if t == 10:
        return b''.join(bytes([kind]) + string(name) + payload(kind, val) for name, (kind, val) in value.items()) + b'\0'
    raise ValueError(t)


def main():
    build()
    palette = list(dict.fromkeys(grid.values()))
    indexes = {s: i for i, s in enumerate(palette)}
    states = []
    for state in palette:
        name, _, properties = state.partition('[')
        entry = {'Name': (8, 'minecraft:' + name)}
        if properties:
            entry['Properties'] = (10, {k: (8, v) for k, v in (p.split('=') for p in properties[:-1].split(','))})
        states.append(entry)
    blocks = []
    for pos, state in sorted(grid.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
        entry = {'pos': (9, (3, pos)), 'state': (3, indexes[state])}
        if pos in tiles:
            entry['nbt'] = (10, tiles[pos])
        blocks.append(entry)
    root = {'DataVersion': (3, 3955), 'size': (9, (3, SIZE)), 'palette': (9, (10, states)),
            'blocks': (9, (10, blocks)), 'entities': (9, (10, []))}
    encoded = b'\x0a\0\0' + payload(10, root)
    data = gzip.compress(encoded, mtime=0)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'modern_hospital.nbt').write_bytes(data)
    metadata = json.dumps({'pack': {'pack_format': 48, 'description': '三层现代医院 | Java 1.21.1'}}, ensure_ascii=False)
    with zipfile.ZipFile(OUT / 'ModernHospital-1.21.1.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('pack.mcmeta', metadata)
        archive.writestr('data/modern_hospital/structure/hospital.nbt', data)
    # Validate key structure invariants before delivery.
    assert gzip.decompress(data) == encoded
    assert len(grid) == 47 * 23 * 43
    assert all(grid[x, 3, 10] == 'air' for x in range(21, 26))
    for (x, y, z), state in grid.items():
        if 'part=foot' in state:
            assert 'part=head' in grid[x, y, z + 1]
    for y in [2, 7]:
        for i in range(5):
            assert 'stairs' in grid[21, y + 1 + i, 32 + i]
            assert grid[21, y + 2 + i, 32 + i] == 'air'
            assert grid[21, y + 3 + i, 32 + i] == 'air'
    print(f'Built {SIZE}: {len(blocks)} blocks, {len(states)} states, {len(data)} compressed bytes')
    print(OUT)


if __name__ == '__main__':
    main()
