# -*- coding: utf-8 -*-
"""
生成「永恒」风格 Minecraft 建筑世界（Java 1.21.1 纯原版可打开）。
输出：一个标准 Anvil 世界存档目录（level.dat + region/*.mca）。

实现要点：
- 超平坦世界（flat preset，一层 bedrock + 数层 dirt + 一层 grass_block），y=0 附近铺场景。
- 自实现 Anvil region 写入：4KB offset 表 + zlib 压缩 chunk。
- palette 用全局引用计数编码（compact long array）。
- DataVersion = 3955（1.21.1）。
"""
import os
import io
import gzip
import zlib
import struct
import shutil
import nbtlib
from nbtlib import tag as T

DATA_VERSION = 3955
WORLD_HEIGHT = 384
MIN_Y = -64

# ---------------------------------------------------------------------------
# 方块 palette 与索引
# ---------------------------------------------------------------------------
# (名称, properties) -> palette 全局索引；空 properties 用空 dict。
BLOCKS = {}

def block_index(name, props=None):
    props = props or {}
    key = (name, tuple(sorted(props.items())))
    if key not in BLOCKS:
        BLOCKS[key] = len(BLOCKS)
    return BLOCKS[key]

# 预注册常用方块（确保 air 索引 0，stone 索引靠前）
AIR = block_index("minecraft:air")
BEDROCK = block_index("minecraft:bedrock")
DIRT = block_index("minecraft:dirt")
GRASS = block_index("minecraft:grass_block")
STONE = block_index("minecraft:stone")
STONE_BRICKS = block_index("minecraft:stone_bricks")
QUARTZ_BLOCK = block_index("minecraft:quartz_block")
QUARTZ_PILLAR = block_index("minecraft:quartz_pillar")
QUARTZ_STAIRS = block_index("minecraft:quartz_stairs")
GOLD_BLOCK = block_index("minecraft:gold_block")
COPPER_BLOCK = block_index("minecraft:copper_block")
CUT_COPPER = block_index("minecraft:cut_copper")
EXPOSED_COPPER = block_index("minecraft:exposed_copper")
WAXED_COPPER = block_index("minecraft:waxed_copper_block")
STRIPPED_OAK = block_index("minecraft:stripped_oak_log")
STRIPPED_SPRUCE = block_index("minecraft:stripped_spruce_log")
OAK_LOG = block_index("minecraft:oak_log")
GLASS = block_index("minecraft:glass")
TINTED_GLASS = block_index("minecraft:tinted_glass")
GLASS_PANE = block_index("minecraft:glass_pane")
WHITE_STAINED_GLASS = block_index("minecraft:white_stained_glass")
LIGHT_GRAY_STAINED_GLASS = block_index("minecraft:light_gray_stained_glass")
YELLOW_STAINED_GLASS = block_index("minecraft:yellow_stained_glass")
SEA_LANTERN = block_index("minecraft:sea_lantern")
GLOWSTONE = block_index("minecraft:glowstone")
OAK_PLANKS = block_index("minecraft:oak_planks")
OAK_STAIRS = block_index("minecraft:oak_stairs")
IRON_BLOCK = block_index("minecraft:iron_block")
SNOW_BLOCK = block_index("minecraft:snow_block")
WHITE_WOOL = block_index("minecraft:white_wool")
WHITE_CONCRETE = block_index("minecraft:white_concrete")
WHITE_CONCRETE_POWDER = block_index("minecraft:white_concrete_powder")
SOUL_SAND = block_index("minecraft:soul_sand")
WATER = block_index("minecraft:water", {"level": "0"})

# ---------------------------------------------------------------------------
# compact long array 编码（palette 引用计数）
# ---------------------------------------------------------------------------
def bits_needed(n):
    """Minecraft PalettedContainer 的 bits 规则：最小 4 bit（单元素=0 除外）。

    bits = max(4, ceil(log2(n)))。palette 2~16 都是 4 bit，17~256 是 8 bit，以此类推。
    """
    if n <= 1:
        return 0
    b = 1
    while (1 << b) < n:
        b += 1
    return max(4, b)

def pack_indices(indices, palette_len):
    """把 4096 个 palette 索引编码成 LongArray（Minecraft 连续 bit 流语义）。

    与 Minecraft PackedLongArray 一致：第 i 个值从 bit (i*b) 开始连续排布，
    跨 long 边界打包，每个值占 b bit，低位在前。
    """
    b = bits_needed(palette_len)
    n = len(indices)
    if b == 0:
        return []  # 单元素 palette，data 可空
    total_bits = n * b
    num_longs = (total_bits + 63) // 64
    longs = [0] * num_longs
    MASK = (1 << 64) - 1
    for i, val in enumerate(indices):
        bitpos = i * b
        li = bitpos // 64
        off = bitpos % 64
        v = val & ((1 << b) - 1)
        longs[li] = (longs[li] | (v << off)) & MASK
        # 跨 long 边界
        if off + b > 64:
            longs[li + 1] = (longs[li + 1] | (v >> (64 - off))) & MASK
    return longs

# ---------------------------------------------------------------------------
# 世界模型：三维体素数组（内存中，仅存非空气方块）
# ---------------------------------------------------------------------------
class VoxelWorld:
    def __init__(self):
        # dict: (x, y, z) -> palette_index
        self.blocks = {}

    def set(self, x, y, z, idx):
        if idx == AIR:
            self.blocks.pop((x, y, z), None)
        else:
            self.blocks[(x, y, z)] = idx

    def get(self, x, y, z):
        return self.blocks.get((x, y, z), AIR)


# ---------------------------------------------------------------------------
# 场景构建
# ---------------------------------------------------------------------------
def build_scene(w):
    """构建「永恒」风格场景，中心在 (0,0)，地面 y=0（草皮顶在 y=4）。"""
    # --- 地面 ---
    for x in range(-80, 81):
        for z in range(-80, 81):
            w.set(x, -4, z, BEDROCK)
            for y in range(-3, 3):
                w.set(x, y, z, DIRT)
            w.set(x, 3, z, GRASS)
            # 奶白色水面区：前景（z 负方向）铺雪白水面
            if z < -40 and z >= -70:
                if -30 <= x <= 30:
                    w.set(x, 4, z, SNOW_BLOCK)      # 白色水面（雪块模拟奶沫）
                    w.set(x, 4, z, SNOW_BLOCK)

    # 前景奶白浪花（雪块 + 白色混凝土 + 白色羊毛形成泡沫感）
    for x in range(-70, 71):
        for z in range(-70, -35):
            d = abs(x)
            if d < 30:
                w.set(x, 4, z, SNOW_BLOCK)
            elif d < 42 and (x + z) % 3 == 0:
                w.set(x, 4, z, WHITE_CONCRETE)
            if d in (38, 39, 40) and (x * 7 + z) % 5 == 0:
                w.set(x, 5, z, WHITE_WOOL)   # 泡沫浪尖

    # --- 中央玻璃大球（半径 14，球心 (0, 18, 0)）---
    import math
    R = 14
    cy = 18
    for x in range(-R - 1, R + 2):
        for y in range(-R - 1, R + 2):
            for z in range(-R - 1, R + 2):
                d = math.sqrt(x * x + y * y + z * z)
                # 连续球壳：厚度约 1.5 格
                if R - 1.0 <= d <= R + 0.5:
                    w.set(x, cy + y, z, GLASS)
                # 内部装饰齿轮：内核外的铜块环带（赤道附近）
                elif 6 <= d <= 10 and abs(y) <= 3:
                    ang = math.degrees(math.atan2(z, x))
                    # 齿轮齿状：每 30 度一个齿
                    if (int(ang) % 30) < 15:
                        w.set(x, cy + y, z, COPPER_BLOCK)
    # 球心发光核心
    for dx in range(-3, 4):
        for dy in range(-3, 4):
            for dz in range(-3, 4):
                if dx * dx + dy * dy + dz * dz <= 9:
                    w.set(dx, cy + dy, dz, GLOWSTONE)

    # --- 双层齿轮环（环绕球体，像土星环：斜置的厚环 + 齿）---
    for ring_r, ring_y, tilt_deg, mat in [(22, 0, 12, COPPER_BLOCK), (27, 8, 0, GOLD_BLOCK)]:
        tilt = math.radians(tilt_deg)
        for x in range(-ring_r - 1, ring_r + 2):
            for z in range(-ring_r - 1, ring_r + 2):
                r_horiz = math.sqrt(x * x + z * z)
                if ring_r - 1.5 <= r_horiz <= ring_r + 1.5:
                    # 环体：沿倾斜平面绕一圈
                    for t in (-2, -1, 0, 1, 2):
                        yy = cy + ring_y + t + int(r_horiz * math.tan(tilt) * 0.1)
                        w.set(x, yy, z, mat)
        # 环上的齿（每 20 度）
        for ang in range(0, 360, 20):
            rad = math.radians(ang)
            gx = int(round(ring_r * math.cos(rad)))
            gz = int(round(ring_r * math.sin(rad)))
            for t in (0, 1):
                w.set(gx, cy + ring_y + t, gz, STRIPPED_OAK if t == 0 else mat)

    # --- 漂浮金齿轮（球体周围若干小齿轮，竖直放置更像参考图）---
    for (gx, gy, gz, gr) in [
        (-34, 34, 6, 5), (34, 30, -4, 4), (-30, 26, -20, 4),
        (30, 40, 18, 5), (0, 46, -26, 4), (-18, 42, 22, 3),
        (20, 38, -24, 3), (0, 24, 30, 3),
    ]:
        # 竖直齿轮：在 x-y 平面上的圆环（厚度 2）
        for x in range(-gr - 1, gr + 2):
            for y in range(-gr - 1, gr + 2):
                r2d = math.sqrt(x * x + y * y)
                if gr - 1.2 <= r2d <= gr + 0.8:
                    w.set(gx + x, gy + y, gz, GOLD_BLOCK)
                    w.set(gx + x, gy + y, gz + 1, GOLD_BLOCK)
        # 齿（沿圆周每 45 度）
        for ang in range(0, 360, 45):
            rad = math.radians(ang)
            tx = int(round((gr + 1.5) * math.cos(rad)))
            ty = int(round((gr + 1.5) * math.sin(rad)))
            w.set(gx + tx, gy + ty, gz, COPPER_BLOCK)
            w.set(gx + tx, gy + ty, gz + 1, COPPER_BLOCK)
        # 轴心
        w.set(gx, gy, gz, SEA_LANTERN)
        w.set(gx, gy, gz + 1, SEA_LANTERN)

    # --- 石英柱大殿（球体后方，z 正方向）---
    for px in (-38, -24, -10, 10, 24, 38):
        for py in range(0, 34):
            if py in (0, 1, 2, 33):          # 底座 + 柱头
                w.set(px, py, 34, GOLD_BLOCK)
            else:
                w.set(px, py, 34, QUARTZ_PILLAR)
    # 大殿拱窗墙（浅色玻璃）
    for px in range(-38, 39):
        for py in range(4, 30):
            if py % 6 < 3:
                w.set(px, py, 30, WHITE_STAINED_GLASS)
            else:
                w.set(px, py, 30, QUARTZ_BLOCK)
    # 大殿顶梁
    for px in range(-40, 41):
        w.set(px, 30, 30, QUARTZ_BLOCK)
        w.set(px, 31, 30, QUARTZ_BLOCK)

    # --- 中轴线大道（从大殿延伸过来）---
    for z in range(-30, 31):
        for x in range(-3, 4):
            w.set(x, 4, z, STONE_BRICKS)
        # 两侧金柱灯
        if z % 8 == 0:
            w.set(-6, 5, z, GOLD_BLOCK)
            w.set(-6, 6, z, SEA_LANTERN)
            w.set(6, 5, z, GOLD_BLOCK)
            w.set(6, 6, z, SEA_LANTERN)


# ---------------------------------------------------------------------------
# Anvil region 写入
# ---------------------------------------------------------------------------
def write_chunk_nbt(w, cx, cz):
    """构造并返回一个 chunk 的 NBT 根 Compound（未压缩）。"""
    # 收集该 chunk 内所有非空气方块，按 section 分组
    # chunk 世界坐标范围
    min_x = cx * 16
    min_z = cz * 16
    sections = {}   # sectionY(全局) -> dict[(lx,ly,lz)->idx]
    for (x, y, z), idx in w.blocks.items():
        if not (min_x <= x < min_x + 16 and min_z <= z < min_z + 16):
            continue
        if y < MIN_Y or y >= MIN_Y + WORLD_HEIGHT:
            continue
        sy = (y - MIN_Y) // 16
        if sy not in sections:
            sections[sy] = {}
        sections[sy][(x - min_x, y - MIN_Y - sy * 16, z - min_z)] = idx

    # 确定要写的 section Y 范围（-4 .. 19），只写非空 section
    sec_list = []
    for sy in sorted(sections.keys()):
        if sections[sy]:
            sec_list.append(sy)

    section_tags = T.List[nbtlib.Compound]()
    for sy in sec_list:
        sec = T.Compound()
        # section 的 Y 标签是「绝对层索引」：世界 y // 16，范围 -4..19
        # 我们内部 sy 是逻辑索引 0..23（对应 y=-64..320），换算：绝对层 = sy - 4
        sec['Y'] = T.Byte(sy - 4)
        # palette
        cells = sections[sy]
        # 收集该 section 出现的 palette 索引
        used = set(cells.values())
        # 重新映射到 section 局部 palette
        local_map = {}
        local_pal = []
        for idx in sorted(used):
            name, props = list(BLOCKS.keys())[idx]
            entry = T.Compound()
            entry['Name'] = T.String(name)
            if props:
                props_c = T.Compound()
                for k, v in props:
                    props_c[k] = T.String(v)
                entry['properties'] = props_c
            local_map[idx] = len(local_pal)
            local_pal.append(entry)

        # 填充 4096 索引数组
        indices = [0] * 4096
        for (lx, ly, lz), idx in cells.items():
            flat = (ly * 16 + lz) * 16 + lx
            indices[flat] = local_map[idx]

        bs = T.Compound()
        bs['palette'] = T.List[nbtlib.Compound](local_pal)
        data_longs = pack_indices(indices, len(local_pal))
        if data_longs:  # 单元素 palette（bits=0）时省略 data 字段
            bs['data'] = T.LongArray([to_signed_long(v) for v in data_longs])
        sec['block_states'] = bs
        # 生物群系：palette 是「字符串」列表（非 Compound），单元素时省略 data
        sec['biomes'] = T.Compound({'palette': T.List[nbtlib.String]([
            T.String('minecraft:plains')
        ])})
        # 天空光照：2048 字节（4096 方块 × 4bit/方块），全亮 = 0xFF = int8 的 -1
        sec['SkyLight'] = T.ByteArray([-1] * 2048)
        section_tags.append(sec)

    root = T.Compound()
    root['DataVersion'] = T.Int(DATA_VERSION)
    root['xPos'] = T.Int(cx)
    root['zPos'] = T.Int(cz)
    root['yPos'] = T.Int(-4)
    root['Status'] = T.String('minecraft:full')
    root['sections'] = section_tags
    root['block_entities'] = T.List[nbtlib.Compound]()
    root['block_ticks'] = T.List[nbtlib.Compound]()
    root['fluid_ticks'] = T.List[nbtlib.Compound]()
    root['LastUpdate'] = T.Long(0)
    root['InhabitedTime'] = T.Long(0)
    root['isLightOn'] = T.Byte(1)
    root['PostProcessing'] = T.List[nbtlib.List]()
    root['structures'] = T.Compound({'References': T.Compound(), 'starts': T.Compound()})
    # Heightmaps（粗填）
    root['Heightmaps'] = T.Compound()
    return root


def to_signed_long(v):
    """把 0..2^64-1 的无符号 long 转成 numpy int64 能表示的补码值。"""
    if v >= (1 << 63):
        v -= (1 << 64)
    return v


def nbt_to_bytes(root):
    """序列化 NBT 为字节（无压缩，无 gzip 头）。"""
    buf = io.BytesIO()
    nbtlib.File(root).write(buf, byteorder='big')
    return buf.getvalue()


def write_region(w, rx, rz, out_dir):
    """把一个 region (32x32 chunk) 写成 .mca 文件。"""
    chunks = {}   # (cx,cz) -> bytes(压缩后)
    for lcx in range(32):
        for lcz in range(32):
            cx = rx * 32 + lcx
            cz = rz * 32 + lcz
            # 判断这个 chunk 是否有内容
            min_x, min_z = cx * 16, cz * 16
            has = any(min_x <= x < min_x + 16 and min_z <= z < min_z + 16
                      for (x, y, z) in w.blocks.keys())
            if not has:
                continue
            root = write_chunk_nbt(w, cx, cz)
            raw = nbt_to_bytes(root)
            comp = zlib.compress(raw)
            chunks[(lcx, lcz)] = comp

    if not chunks:
        return

    # 构建 region 文件
    header = bytearray(4096 * 2)  # offset 表 + timestamp 表
    body = bytearray()
    # 先放两个空 sector（header 占 8KB = 2 sector）
    offset = 2

    for (lcx, lcz), comp in sorted(chunks.items()):
        idx = lcx + lcz * 32
        length = len(comp)
        sectors = (length + 4 + 1 + 4095) // 4096
        # 写 header entry：offset(3字节) + sector数(1字节)
        header[idx * 4] = (offset >> 16) & 0xFF
        header[idx * 4 + 1] = (offset >> 8) & 0xFF
        header[idx * 4 + 2] = offset & 0xFF
        header[idx * 4 + 3] = sectors
        # body：4字节长度 + 1字节压缩类型 + 数据 + 填充
        body += struct.pack('>I', length) + bytes([2]) + comp
        body += b'\x00' * (sectors * 4096 - (length + 5))
        offset += sectors

    # 补齐 body 到 sector 边界
    if len(body) % 4096:
        body += b'\x00' * (4096 - len(body) % 4096)

    data = bytes(header) + bytes(body)
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, f'r.{rx}.{rz}.mca'), 'wb') as f:
        f.write(data)


# ---------------------------------------------------------------------------
# level.dat 生成
# ---------------------------------------------------------------------------
def write_level_dat(out_dir, name="永恒 Eternal"):
    root = T.Compound()
    data = T.Compound()
    data['version'] = T.Int(19133)   # 原版 NBT 版本号（网络协议无关，粗填）
    data['DataVersion'] = T.Int(DATA_VERSION)
    data['LevelName'] = T.String(name)
    data['allowCommands'] = T.Byte(1)
    data['initialized'] = T.Byte(1)
    data['hardcore'] = T.Byte(0)
    data['Difficulty'] = T.Byte(1)
    data['DifficultyLocked'] = T.Byte(0)
    data['GameType'] = T.Int(1)      # 创造模式
    data['Time'] = T.Long(0)
    data['DayTime'] = T.Long(0)
    data['raining'] = T.Byte(0)
    data['rainTime'] = T.Int(0)
    data['thundering'] = T.Byte(0)
    data['thunderTime'] = T.Int(0)
    data['clearWeatherTime'] = T.Int(0)
    data['SpawnX'] = T.Int(0)
    data['SpawnY'] = T.Int(5)
    data['SpawnZ'] = T.Int(-40)
    data['SpawnAngle'] = T.Float(0.0)
    data['BorderCenterX'] = T.Double(0.0)
    data['BorderCenterZ'] = T.Double(0.0)
    data['BorderSize'] = T.Double(60000000.0)
    data['BorderSizeLerpTime'] = T.Long(0)
    data['BorderSizeLerpTarget'] = T.Double(60000000.0)
    data['BorderSafeZone'] = T.Double(5.0)
    data['BorderWarningBlocks'] = T.Double(5.0)
    data['BorderWarningTime'] = T.Double(15.0)
    data['BorderDamagePerBlock'] = T.Double(0.2)
    data['LastPlayed'] = T.Long(0)
    data['WanderingTraderSpawnChance'] = T.Int(25)
    data['WanderingTraderSpawnDelay'] = T.Int(24000)
    data['CustomBossEvents'] = T.Compound()
    data['ScheduledEvents'] = T.List[nbtlib.Compound]()
    data['ServerBrands'] = T.List[nbtlib.String]()
    data['confirmedExperimentalSettings'] = T.Byte(0)
    data['WorldGenSettings'] = T.Compound({
        'seed': T.Long(0),
        'generate_features': T.Byte(0),
        'bonus_chest': T.Byte(0),
        'dimensions': T.Compound({
            'minecraft:overworld': T.Compound({
                'type': T.String('minecraft:overworld'),
                'generator': T.Compound({
                    'type': T.String('minecraft:flat'),
                    'settings': T.Compound({
                        'features': T.Byte(0),
                        'biome': T.String('minecraft:plains'),
                        'layers': T.List[nbtlib.Compound]([
                            T.Compound({'block': T.String('minecraft:bedrock'), 'height': T.Int(1)}),
                            T.Compound({'block': T.String('minecraft:dirt'), 'height': T.Int(2)}),
                            T.Compound({'block': T.String('minecraft:grass_block'), 'height': T.Int(1)}),
                        ]),
                        'structure_overrides': T.List[nbtlib.String](),
                    }),
                }),
            }),
            'minecraft:the_nether': T.Compound({
                'type': T.String('minecraft:the_nether'),
                'generator': T.Compound({
                    'type': T.String('minecraft:noise'),
                    'settings': T.String('minecraft:nether'),
                    'biome_source': T.Compound({
                        'type': T.String('minecraft:multi_noise'),
                        'preset': T.String('minecraft:nether'),
                    }),
                }),
            }),
            'minecraft:the_end': T.Compound({
                'type': T.String('minecraft:the_end'),
                'generator': T.Compound({
                    'type': T.String('minecraft:noise'),
                    'settings': T.String('minecraft:end'),
                    'biome_source': T.Compound({
                        'type': T.String('minecraft:the_end'),
                    }),
                }),
            }),
        }),
    })
    data['GameRules'] = T.Compound()
    data['DataPacks'] = T.Compound({
        'Enabled': T.List[nbtlib.String]([T.String('vanilla')]),
        'Disabled': T.List[nbtlib.String](),
    })
    data['DragonFight'] = T.Compound({
        'NeedsStateScanning': T.Byte(1),
        'PreviouslyKilled': T.Byte(0),
        'DragonKilled': T.Byte(0),
        'Gateways': T.IntArray([]),
    })
    data['Version'] = T.Compound({
        'Id': T.Int(DATA_VERSION),
        'Name': T.String('1.21.1'),
        'Series': T.String('main'),
        'Snapshot': T.Byte(0),
    })
    root['Data'] = data

    buf = io.BytesIO()
    nbtlib.File(root).write(buf, byteorder='big')
    raw = buf.getvalue()
    with gzip.open(os.path.join(out_dir, 'level.dat'), 'wb') as f:
        f.write(raw)


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
def main():
    out_dir = os.path.join(os.path.dirname(__file__), '..', 'generated-world', '永恒 Eternal')
    out_dir = os.path.abspath(out_dir)
    if os.path.exists(out_dir):
        shutil.rmtree(out_dir)
    os.makedirs(out_dir, exist_ok=True)

    print('构建场景...')
    w = VoxelWorld()
    build_scene(w)
    print('方块总数:', len(w.blocks))

    # 确定需要写的 region 范围
    xs = [p[0] for p in w.blocks]
    zs = [p[2] for p in w.blocks]
    min_rx, max_rx = min(xs) // 512, max(xs) // 512
    min_rz, max_rz = min(zs) // 512, max(zs) // 512
    region_dir = os.path.join(out_dir, 'region')
    print(f'region 范围: rx[{min_rx},{max_rx}] rz[{min_rz},{max_rz}]')
    for rx in range(min_rx, max_rx + 1):
        for rz in range(min_rz, max_rz + 1):
            write_region(w, rx, rz, region_dir)

    write_level_dat(out_dir)
    print('完成！输出目录:', out_dir)
    print('palette 方块数:', len(BLOCKS))
    for key, idx in BLOCKS.items():
        print(f'  [{idx}] {key[0]} {dict(key[1])}')


if __name__ == '__main__':
    main()
