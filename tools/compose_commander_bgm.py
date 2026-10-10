#!/usr/bin/env python3
"""尸潮指挥官（Zombie Commander）Boss BGM 的作曲 + 渲染脚本。

设计
----
调性 A 小调，104 BPM，4/4 拍，32 小节（约 74 秒），可无缝循环。
和声进行每两小节一个和弦，十六小节一轮：Am - F - Dm - E（i - VI - iv - V）。
E 大三和弦里的 G# 是 A 和声小调的导音，也是整首紧张感的来源。

段落
----
  1- 4  引子：次低频暗流 + 定音鼓心跳 + 远处铜钹，鼓组刻意不进场
  5-12  A 段：底鼓 / 踩镲 / 军鼓齐入，低音线八分音符行进，弦乐和声垫
 13-20  B 段：主题第一次陈述（威胁型动机：同音反复 + 半音下行 + 硬顶根音的小二度）
 21-28  C 段：主题碎片化成小二度摇摆 + 弦乐十六分脉冲，张力长音加重
 29-32  D 段：全奏收束，定音鼓心跳翻倍成每拍一下，收在不解决的音上接回循环

配器取向（第三版：换成电子编制）
--------------------------------
参照 Pigstep 的编制，把"管弦正剧"整条换掉：

  * 旋律  钢琴（高速钢琴）。同一份旋律表 + 一层十六分钢琴琶音，音区摊成 D3-F5。
          节奏用附点（每拍拆成长 0.70 + 短 0.20），琶音层从只盖 C 段扩到全曲的均匀十六分。
  * 低音  失真锯齿（synthbass）+ 次低频暗流（sub）+ 管风琴（organ），三层叠成"墙"。
          Pigstep 的低音是 Trombone(D2) + Tuba(G#1) + 失真吉他三层同时铺的。
  * 踏板  一条全曲不动的 A3（clarinet 音色）。原曲那条 Muted Trumpet 轨 128 个音
          全是同一个 A3，旋律怎么走它都不动——这是它压迫感的一大来源。
  * 中音区刻意留空：和声垫从 0.105 退到 0.055，把中频让给旋律和踏板。
  * 打击  踩镲改成全程十六分（原曲的连续"沙沙"是主要推进力），铜钹只留 3 处。
  * 空间  混响从 0.30 收到 0.12。Pigstep 是干声，大混响会立刻把电子编制拉回管弦大厅。

旋律取向（第四版：改成快速 riff）
--------------------------------
第三版的动机以 1~2 拍长音为主，配长笛的柔和音色听感偏"沉思"，既不酷也不刺激。
现在整条改成**快速断奏的 riff**：绝大多数音符只有 0.45 拍（断奏留缝），
每 8 小节一轮里有 2~3 处十六分短跑（C 段整段是十六分），音程以三度/六度跳进为主。
配合锐化的长笛音色与极短的包络（起音 10ms、释音 50ms），听感是一串打下来的音型，
而不是一条旋律线。

主题的取向
----------
刻意不写成"歌唱性"旋律：那需要一个稳定音高中心 + 上行的、能唱出来的乐句，
结果就是激昂甚至欢快。这里反过来——大量同音反复、半音下行、并在 E 和弦上硬顶
根音上方小二度的 F。旋律不再是"唱"，而是"敲"。

压迫感来自四层叠加
------------------
  1. 鼓组  每段逐层加密，段落交界用通鼓过门推进（KICK / HIHAT / SNARE / TOM_*）
  2. 低频  三层低音叠加 + 一条 55Hz 次低频暗流（SUB），归一化时排除在外以免压扁其余部分
  3. 和声  每块和弦挂一个不解决的张力音（CHORDS 的 tension）+ 全曲不动的 A3 踏板音
  4. 动态  低音线的半音逼近、段落交界的不谐和音簇（STAB）、心跳加速的定音鼓

产物
----
  tools/commander_bgm.mid         MIDI —— 「作曲」的实际交付物，可在 DAW 里换音源重渲染
  tools/commander_bgm_preview.wav 试听用（16bit PCM）
  src/main/resources/assets/dreamingfishcore/sounds/zombie_commander_bgm.ogg
                                  mod 实际播放的文件

全部音色由下面的加法合成器现场合成，不依赖 SoundFont 或任何外部音源。
改曲子只需改 SECTIONS / 各 *_TRACK 表，然后重跑本脚本。
嫌鼓点太满 / 太吵：把 main() 里 render_kick / render_hihat / render_snare 的 gain 调小即可。

用法（隔离环境）：
  C:/Users/passk/.workbuddy/binaries/python/envs/default/Scripts/python.exe \
      tools/compose_commander_bgm.py
"""

from __future__ import annotations

import math
import os
import subprocess
import wave

import numpy as np

# ---------------------------------------------------------------- 基本参数

SR = 44100
BPM = 104.0
BEAT = 60.0 / BPM
BAR = 4.0 * BEAT
BARS = 32
DUR = BARS * BAR
TOTAL = int(DUR * SR) + SR  # 末尾多留一秒给混响尾巴
TPB = 480                   # MIDI 每拍 tick 数

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MID_PATH = os.path.join(ROOT, "tools", "commander_bgm.mid")
WAV_PATH = os.path.join(ROOT, "tools", "commander_bgm_preview.wav")
OGG_PATH = os.path.join(
    ROOT, "src", "main", "resources", "assets", "dreamingfishcore", "sounds",
    "zombie_commander_bgm.ogg",
)

STEP = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6,
        "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}
NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]


def midi_number(name: str) -> int:
    """音名转 MIDI 编号；A4 = 69、C4 = 60。"""
    return 12 * (int(name[-1]) + 1) + STEP[name[:-1]]


def note_name(number: int) -> str:
    """MIDI 编号转音名，用来算半音逼近音。"""
    return NAMES[number % 12] + str(number // 12 - 1)


def hz(number: int) -> float:
    return 440.0 * 2.0 ** ((number - 69) / 12.0)


# ---------------------------------------------------------------- 和声骨架

# "tension" 是挂在高音区的张力音：单看都是正常的和弦外音（九音 / 上方半音），
# 拉长之后持续存在，就成了「这段和声一直没解决」的压迫感来源。E 上的 F 是最狠的一个
# ——它和根音只差小二度，听感就是「有什么东西一直压着不放」。
CHORDS = {
    "Am": {"bass": "A2", "pad": ("A3", "C4", "E4"), "drone": "A2", "tension": "B4"},
    "F":  {"bass": "F2", "pad": ("F3", "A3", "C4"), "drone": "F2", "tension": "G#4"},
    "Dm": {"bass": "D2", "pad": ("D3", "F3", "A3"), "drone": "D2", "tension": "E4"},
    "E":  {"bass": "E2", "pad": ("E3", "G#3", "B3"), "drone": "E2", "tension": "F4"},
}
PROGRESSION = ["Am", "F", "Dm", "E"] * 8  # 32 小节，每和弦占 2 小节


def chord_at(bar: int) -> str:
    """bar 从 1 开始。"""
    return PROGRESSION[(bar - 1) // 2]


# ---------------------------------------------------------------- 音色

# 加法合成的谐波表：{音色: [(谐波序号, 幅度), ...]}
TIMBRES = {
    # 管风琴：奇偶都齐，低频厚重（全曲不动的底压层）
    "organ":    [(1, 1.0), (2, 0.50), (3, 0.26), (4, 0.15), (6, 0.07)],
    # 弦乐垫：高次谐波少、起音慢（第三版退到背景，中音区要留给旋律）
    "strings":  [(1, 1.0), (2, 0.40), (3, 0.16), (4, 0.08), (5, 0.04)],
    # 钢琴：谐波铺到 8 次，靠"快起音 + 长衰减 + 极低保持值"做出击弦的颗粒感。
    # 钢琴没有真正的 sustain，所以保持值给到 0.22，听感是"敲一下就走"而不是拉长。
    "piano":    [(1, 1.0), (2, 0.52), (3, 0.30), (4, 0.20), (5, 0.13), (6, 0.09),
                 (7, 0.06), (8, 0.04)],
    # 单簧管：闭管乐器，**只有奇次谐波**，听感空、木、发闷。
    # Pigstep 里那层"不动"的紧张就是这类音色（原曲那轨是 Muted Trumpet）。
    "clarinet": [(1, 1.0), (3, 0.34), (5, 0.17), (7, 0.09)],
    # 失真锯齿低音：谐波按 1/n 铺满，又厚又脏——把低音做成"墙"而不是"线"。
    "synthbass": [(1, 1.0), (2, 0.66), (3, 0.48), (4, 0.36), (5, 0.28), (6, 0.22), (7, 0.17)],
    # 次低频暗流：几乎只剩基频，避免低音区堆出浑浊的拍频
    "sub":      [(1, 1.0), (2, 0.10)],
}

# 各音色的包络 (起音, 衰减, 保持比例, 释音) 单位秒
ENVELOPES = {
    "organ":     (0.25, 0.30, 0.85, 0.60),
    "strings":   (0.28, 0.35, 0.80, 0.55),
    "piano":     (0.004, 0.40, 0.22, 0.15),
    "clarinet":  (0.05, 0.14, 0.84, 0.22),
    "synthbass": (0.004, 0.09, 0.78, 0.09),
    "pulse":     (0.005, 0.06, 0.30, 0.08),
    "sub":       (1.20, 0.60, 0.95, 1.20),
}


def envelope(n: int, shape: tuple[float, float, float, float]) -> np.ndarray:
    """线性 ADSR 包络。"""
    a, d, s, r = shape
    a_n, d_n, r_n = max(1, int(a * SR)), max(1, int(d * SR)), max(1, int(r * SR))
    if a_n + d_n + r_n >= n:  # 太短，等比压缩
        scale = n / float(a_n + d_n + r_n)
        a_n, d_n, r_n = max(1, int(a_n * scale)), max(1, int(d_n * scale)), max(1, int(r_n * scale))
    sustain_n = n - a_n - d_n - r_n
    parts = [
        np.linspace(0.0, 1.0, a_n, endpoint=False),
        np.linspace(1.0, s, d_n, endpoint=False),
        np.full(sustain_n, s),
        np.linspace(s, 0.0, r_n),
    ]
    env = np.concatenate(parts)
    if env.size < n:
        env = np.concatenate([env, np.zeros(n - env.size)])
    return env[:n]


def additive(freq: float, n: int, timbre: str, detune_cents: float = 0.0,
             glide_cents: float = 0.0) -> np.ndarray:
    """按谐波表做加法合成；超过奈奎斯特的谐波直接丢弃，避免混叠。

    glide_cents != 0 时，基频在约 35ms 内从 freq*2^(glide/1200) 滑到 freq ——
    这是东方曲里那种"吹上去"的装饰音（原曲 MIDI 里表现为成百次的 pitch bend）。
    实现必须用**相位累积**而不是 f*t：频率一变，f*t 的相位就断了，会听到咔哒。
    """
    t = np.arange(n) / SR
    f = freq * 2.0 ** (detune_cents / 1200.0)
    if glide_cents:
        sweep = 2.0 ** (glide_cents * np.exp(-t / 0.035) / 1200.0)
        phase = 2.0 * math.pi * np.cumsum(f * sweep) / SR
    else:
        phase = 2.0 * math.pi * f * t
    out = np.zeros(n)
    for harmonic, amp in TIMBRES[timbre]:
        if f * harmonic > SR * 0.45:
            continue
        out += amp * np.sin(phase * harmonic)   # 谐波相位 = 基频相位 × 谐波序号
    return out


# ---------------------------------------------------------------- 轨道数据
# 音符写成 (起始拍, 时值拍, 音名, 力度)。起始拍从 0 开始，全曲共 128 拍。

def bar_beat(bar: int, beat: float = 0.0) -> float:
    return (bar - 1) * 4.0 + beat


# 主题动机（B 段）：第 13 小节进入。
# 音程（第五版）：按 U.N.オーエンは彼女なのか？的实测分布 —— 小跳(3-5 半音) 56%、大跳(≥6) 19%、
#   同音 13%、级进 12%，平均 |跳进| 4.8 半音。我们原来的级进占 42% / 平均 2.8，太平滑、不够"硌"，
#   所以把相邻音程基本拉到三度以上、每两小节插一次六度以上的大跳。
# 节奏（第七版）：按神さびた古戦場的附点 —— 每拍拆成「长 0.70 + 短 0.20」，不用均匀八分。
#   那种"长—短"的摇摆是它区别于"哒哒哒"均匀行进的关键，配小号/小提琴的起音尤其明显。
THEME_B = [
    (13, 0.0, 0.70, "E4", 0.95), (13, 0.75, 0.20, "G4", 0.80), (13, 1.0, 0.70, "A4", 0.92),
    (13, 1.75, 0.20, "E4", 0.78), (13, 2.0, 0.70, "C5", 0.96), (13, 2.75, 0.20, "A4", 0.84),
    (13, 3.0, 0.70, "E4", 0.86), (13, 3.75, 0.20, "G4", 0.82),
    (14, 0.0, 0.70, "A4", 0.94), (14, 0.75, 0.20, "C5", 0.84), (14, 1.0, 0.70, "E5", 0.96),
    (14, 1.75, 0.20, "C5", 0.82), (14, 2.0, 0.70, "A4", 0.90), (14, 2.75, 0.20, "E4", 0.80),
    (14, 3.0, 0.95, "A4", 0.95),
    (15, 0.0, 0.70, "F4", 0.92), (15, 0.75, 0.20, "A4", 0.82), (15, 1.0, 0.70, "C5", 0.94),
    (15, 1.75, 0.20, "A4", 0.80), (15, 2.0, 0.70, "F5", 0.98), (15, 2.75, 0.20, "C5", 0.84),
    (15, 3.0, 0.70, "A4", 0.88), (15, 3.75, 0.20, "C5", 0.84),
    (16, 0.0, 0.70, "A4", 0.92), (16, 0.75, 0.20, "F4", 0.80), (16, 1.0, 0.70, "A4", 0.86),
    (16, 1.75, 0.20, "C5", 0.90), (16, 2.0, 0.95, "A4", 0.94),
    (17, 0.0, 0.70, "D5", 0.96), (17, 0.75, 0.20, "A4", 0.82), (17, 1.0, 0.70, "F4", 0.88),
    (17, 1.75, 0.20, "A4", 0.80), (17, 2.0, 0.70, "D5", 0.94), (17, 2.75, 0.20, "F5", 0.96),
    (17, 3.0, 0.70, "D5", 0.84), (17, 3.75, 0.20, "A4", 0.82),
    (18, 0.0, 0.70, "F4", 0.90), (18, 0.75, 0.20, "A4", 0.82), (18, 1.0, 0.70, "D5", 0.92),
    (18, 1.75, 0.20, "F5", 0.96), (18, 2.0, 0.95, "D5", 0.92),
    (19, 0.0, 0.70, "E5", 0.98), (19, 0.75, 0.20, "B4", 0.84), (19, 1.0, 0.70, "G#4", 0.90),
    (19, 1.75, 0.20, "B4", 0.82), (19, 2.0, 0.70, "E5", 0.96), (19, 2.75, 0.20, "F5", 0.94),
    (19, 3.0, 0.70, "E5", 0.88), (19, 3.75, 0.20, "B4", 0.82),
    (20, 0.0, 0.70, "G#4", 0.92), (20, 0.75, 0.20, "B4", 0.84), (20, 1.0, 0.70, "E5", 0.96),
    (20, 1.75, 0.20, "G#4", 0.82), (20, 2.0, 0.95, "B4", 0.92),
]

# C 段：**高潮段**，重写过。上一版的致命问题是音域跟 B 段一模一样（都是 E4-F5）——
# 前后半段走同一个八度，听感自然"没变化"。这一版整段抬到 A4-B5（平均高一个五度）：
# 每小节前半是一串上行十六分冲刺（A4-C5-E5-A5）冲到高音站住，后半再落回来，
# 每两小节一轮，第八小节顶到 B5。
THEME_C = [
    (21, 0.0, 0.22, "A4", 0.92), (21, 0.25, 0.22, "C5", 0.88), (21, 0.5, 0.22, "E5", 0.92),
    (21, 0.75, 0.22, "A5", 0.98), (21, 1.0, 0.45, "A5", 0.94), (21, 1.5, 0.45, "G5", 0.88),
    (21, 2.0, 0.22, "E5", 0.90), (21, 2.25, 0.22, "C5", 0.86), (21, 2.5, 0.22, "A4", 0.88),
    (21, 2.75, 0.22, "C5", 0.86), (21, 3.0, 0.95, "E5", 0.94),
    (22, 0.0, 0.22, "A4", 0.92), (22, 0.25, 0.22, "C5", 0.88), (22, 0.5, 0.22, "E5", 0.92),
    (22, 0.75, 0.22, "A5", 0.98), (22, 1.0, 0.45, "A5", 0.94), (22, 1.5, 0.45, "F5", 0.90),
    (22, 2.0, 0.45, "E5", 0.90), (22, 2.5, 0.45, "C5", 0.86), (22, 3.0, 0.95, "A4", 0.94),
    (23, 0.0, 0.22, "A4", 0.92), (23, 0.25, 0.22, "C5", 0.88), (23, 0.5, 0.22, "F5", 0.94),
    (23, 0.75, 0.22, "A5", 0.98), (23, 1.0, 0.45, "A5", 0.94), (23, 1.5, 0.45, "G5", 0.88),
    (23, 2.0, 0.22, "F5", 0.92), (23, 2.25, 0.22, "C5", 0.86), (23, 2.5, 0.22, "A4", 0.88),
    (23, 2.75, 0.22, "C5", 0.88), (23, 3.0, 0.95, "F5", 0.94),
    (24, 0.0, 0.22, "A4", 0.92), (24, 0.25, 0.22, "C5", 0.88), (24, 0.5, 0.22, "F5", 0.94),
    (24, 0.75, 0.22, "A5", 0.98), (24, 1.0, 0.45, "G5", 0.90), (24, 1.5, 0.45, "F5", 0.92),
    (24, 2.0, 0.45, "C5", 0.88), (24, 2.5, 0.45, "A4", 0.86), (24, 3.0, 0.95, "F5", 0.94),
    (25, 0.0, 0.22, "D5", 0.94), (25, 0.25, 0.22, "F5", 0.90), (25, 0.5, 0.22, "A5", 0.98),
    (25, 0.75, 0.22, "A5", 0.92), (25, 1.0, 0.45, "A5", 0.96), (25, 1.5, 0.45, "G5", 0.88),
    (25, 2.0, 0.22, "F5", 0.92), (25, 2.25, 0.22, "D5", 0.86), (25, 2.5, 0.22, "A4", 0.88),
    (25, 2.75, 0.22, "D5", 0.88), (25, 3.0, 0.95, "F5", 0.94),
    (26, 0.0, 0.22, "D5", 0.92), (26, 0.25, 0.22, "F5", 0.90), (26, 0.5, 0.22, "A5", 0.98),
    (26, 0.75, 0.22, "F5", 0.90), (26, 1.0, 0.45, "D5", 0.92), (26, 1.5, 0.45, "A4", 0.86),
    (26, 2.0, 0.45, "D5", 0.90), (26, 2.5, 0.45, "F5", 0.94), (26, 3.0, 0.95, "D5", 0.92),
    (27, 0.0, 0.22, "E5", 0.96), (27, 0.25, 0.22, "G#5", 0.92), (27, 0.5, 0.22, "B5", 1.00),
    (27, 0.75, 0.22, "B5", 0.94), (27, 1.0, 0.45, "B5", 0.98), (27, 1.5, 0.45, "A5", 0.90),
    (27, 2.0, 0.22, "G#5", 0.94), (27, 2.25, 0.22, "E5", 0.88), (27, 2.5, 0.22, "B4", 0.88),
    (27, 2.75, 0.22, "E5", 0.90), (27, 3.0, 0.95, "G#5", 0.96),
    (28, 0.0, 0.22, "E5", 0.94), (28, 0.25, 0.22, "G#5", 0.92), (28, 0.5, 0.22, "B5", 1.00),
    (28, 0.75, 0.22, "G#5", 0.92), (28, 1.0, 0.45, "E5", 0.94), (28, 1.5, 0.45, "B4", 0.88),
    (28, 2.0, 0.45, "E5", 0.92), (28, 2.5, 0.45, "G#5", 0.96), (28, 3.0, 0.95, "B5", 1.00),
]

# D 段：高潮收束。整段停在 A5 附近不下来（上一版是 E4-F5，等于又掉回去了），
# 每小节用「A5 → 五度下落 → 回冲」的型推着走，最后一小节连续上行顶到 B5 接回循环。
THEME_D = [
    (29, 0.0, 0.22, "A5", 0.98), (29, 0.25, 0.22, "E5", 0.90), (29, 0.5, 0.22, "C5", 0.90),
    (29, 0.75, 0.22, "E5", 0.92), (29, 1.0, 0.45, "A5", 0.98), (29, 1.5, 0.45, "G5", 0.90),
    (29, 2.0, 0.22, "E5", 0.92), (29, 2.25, 0.22, "C5", 0.88), (29, 2.5, 0.22, "A4", 0.88),
    (29, 2.75, 0.22, "C5", 0.88), (29, 3.0, 0.95, "E5", 0.94),
    (30, 0.0, 0.22, "A5", 0.98), (30, 0.25, 0.22, "F5", 0.92), (30, 0.5, 0.22, "C5", 0.90),
    (30, 0.75, 0.22, "F5", 0.94), (30, 1.0, 0.45, "A5", 0.98), (30, 1.5, 0.45, "G5", 0.90),
    (30, 2.0, 0.45, "F5", 0.94), (30, 2.5, 0.45, "C5", 0.88), (30, 3.0, 0.95, "A4", 0.92),
    (31, 0.0, 0.22, "A5", 0.98), (31, 0.25, 0.22, "F5", 0.92), (31, 0.5, 0.22, "D5", 0.90),
    (31, 0.75, 0.22, "F5", 0.94), (31, 1.0, 0.45, "A5", 0.98), (31, 1.5, 0.45, "G5", 0.90),
    (31, 2.0, 0.45, "F5", 0.94), (31, 2.5, 0.45, "D5", 0.90), (31, 3.0, 0.95, "A5", 0.98),
    (32, 0.0, 0.22, "E5", 0.96), (32, 0.25, 0.22, "G#5", 0.94), (32, 0.5, 0.22, "B5", 1.00),
    (32, 0.75, 0.22, "B5", 0.96), (32, 1.0, 0.45, "E5", 0.96), (32, 1.5, 0.45, "G#5", 0.94),
    (32, 2.0, 0.45, "B5", 1.00), (32, 2.5, 0.45, "E5", 0.94), (32, 3.0, 0.95, "B5", 1.00),
]
# 主题旋律写谱时用 (小节, 拍, 时值, 音名, 力度)，这里统一换算成 (起始拍, 时值, 音名, 力度)。
# 音高直接写在目标音区（E4-F5），不再做整体移调——改谱时不用再心算半音，少一层出错机会。
MELODY = [(bar_beat(bar, beat), length, note, velocity)
          for bar, beat, length, note, velocity in (THEME_B + THEME_C + THEME_D)]

# 低音线：每小节八个八分音符的根音，行军感。
# 每个和弦块（两小节）的末尾放一个「下一和弦根音的下方半音」——这是最老派的紧张感手法，
# 半音贴上去的那一下会让听众下意识等一个解决，而曲子一直不真正落地。
#
# 【试过又退回】曾按「神さびた古戦場」改成在根音/低八度/上五度之间大跳（平均 |跳进| 9.6 半音，
# 那首原曲是 8.5）。数据上是"更接近原著"，但听感变浮——低音失去落点，整首曲子像踩在棉花上。
# 结论：低音的跳跃程度是风格选择，不是越高越对；这首曲子需要一根稳的锚。
BASS = []
for bar in range(5, BARS + 1):
    root = CHORDS[chord_at(bar)]["bass"]
    approach = note_name(midi_number(CHORDS[chord_at(bar + 2)]["bass"]) - 1) if bar % 2 == 0 else None
    for eighth in range(8):
        accent = 1.0 if eighth % 4 == 0 else (0.78 if eighth % 2 == 0 else 0.6)
        pitch = root
        if approach is not None and eighth == 7:   # 第 2 小节的最后一拍改半音逼近
            pitch, accent = approach, 0.74
        BASS.append((bar_beat(bar, eighth * 0.5), 0.42, pitch, accent))

# 低八度加厚层：整轨降八度做"低音墙"（东方快曲的低音普遍压在 F#1-A#2）。
BASS_LOW = [(start, length, note_name(midi_number(name) - 12), velocity * 0.9)
            for start, length, name, velocity in BASS]

# 和声垫：每两小节一个和弦，慢起音长音
PAD = []
for bar in range(5, BARS + 1, 2):
    for note in CHORDS[chord_at(bar)]["pad"]:
        PAD.append((bar_beat(bar, 0.0), 7.6, note, 0.85))

# 低频 drone：全曲铺底，每两小节换根音
DRONE = []
for bar in range(1, BARS + 1, 2):
    DRONE.append((bar_beat(bar, 0.0), 7.8, CHORDS[chord_at(bar)]["drone"], 1.0))

# 高速钢琴层：原曲那轨 Steel Guitar 是 659 个均匀十六分。我们照 0.25 拍铺满全曲。
# 音高做"两上两下"的八度摆动（每拍前两个音高一档、后两个落回来），而不是全都吊在高音区——
# 主奏本来就在 E4-F5，琶音跟它挤在同一个八度只会互相掩蔽，摊开成 D3-E5 才有层次。
PULSE = []
for bar in range(5, BARS + 1):
    for sixteenth in range(16):
        accent = 0.95 if sixteenth % 4 == 0 else (0.60 if sixteenth % 2 == 0 else 0.42)
        offset = 12 if sixteenth % 4 in (0, 1) else 0
        note = note_name(midi_number(CHORDS[chord_at(bar)]["pad"][sixteenth % 3]) + offset)
        PULSE.append((bar_beat(bar, sixteenth * 0.25), 0.2, note, accent))

# --- 打击乐（单独用噪声/正弦合成，不走 TIMBRES） ---


def drum_section(bar: int) -> str:
    """鼓组按段落分层：引子让定音鼓单独铺心跳，A 段起逐层加密，D 段全奏收束。"""
    if bar <= 4:
        return "intro"
    if bar <= 12:
        return "A"
    if bar <= 20:
        return "B"
    if bar <= 28:
        return "C"
    return "D"


# 段落交界前的一小节：第 4 拍整拍换成通鼓过门，把下一段"推"进来。
FILL_BARS = (12, 20, 28)

# 底鼓：A 段起进场。正拍与定音鼓齐奏成更重的"心跳"，后半段补切分把律动往前推。
KICK_PATTERN = {
    "A": [(0.0, 0.95), (2.0, 0.74)],
    "B": [(0.0, 0.95), (1.5, 0.52), (2.0, 0.78)],
    "C": [(0.0, 0.98), (1.5, 0.56), (2.0, 0.80), (3.5, 0.55)],
    "D": [(0.0, 1.00), (1.0, 0.60), (2.0, 0.85), (3.0, 0.62)],
}
KICK = []
for bar in range(5, BARS + 1):
    for beat, accent in KICK_PATTERN[drum_section(bar)]:
        if bar in FILL_BARS and beat >= 3.0:  # 过门小节把第 4 拍让给通鼓
            continue
        KICK.append((bar_beat(bar, beat), accent))

# 定音鼓：全曲"心跳"，0/2 拍各一下；A 段起与底鼓齐奏，渲染时降到一半力度。
# D 段（29 起）心跳直接翻倍成每拍一下——同样的鼓、同样的音高，只是越来越快，
# 这比加任何新乐器都更能把"该结束了"的压力堆上去。
TIMBANI = []
for bar in range(1, BARS + 1):
    beats = (0.0, 1.0, 2.0, 3.0) if bar >= 29 else (0.0, 2.0)
    for beat in beats:
        accent = 1.0 if beat == 0.0 else 0.72
        TIMBANI.append((bar_beat(bar, beat), accent))

# 踩镲：A 段起就是十六分（Pigstep 那种连续的"沙沙"声是主要的推进力之一），
# 每 8 小节一轮在第 4 拍后半开一次镲。
OPEN_HAT_BARS = tuple(b for b in range(6, BARS + 1, 2) if b not in FILL_BARS)
HH_CLOSED = []
HH_OPEN = []
for bar in range(5, BARS + 1):
    base = {"A": 0.42, "B": 0.48, "C": 0.55, "D": 0.58}[drum_section(bar)]
    for i in range(16):
        if bar in OPEN_HAT_BARS and i == 14:   # 该拍由开镲顶上，不开两遍
            continue
        accent = base if i % 4 == 0 else (base * 0.62 if i % 2 == 0 else base * 0.38)
        HH_CLOSED.append((bar_beat(bar, i * 0.25), accent))
    if bar in OPEN_HAT_BARS:
        HH_OPEN.append((bar_beat(bar, 3.5), 0.50))

# 军鼓：A 段打后拍，之后逐段加 ghost note；过门小节只留前半，后半让给通鼓。
SNARE_ACCENT = {"A": (0.72, 0.84), "B": (0.74, 0.86), "C": (0.78, 0.88), "D": (0.82, 0.92)}
SNARE = []
for bar in range(5, BARS + 1):
    section = drum_section(bar)
    back, front = SNARE_ACCENT[section]
    SNARE.append((bar_beat(bar, 1.0), back))
    if bar in FILL_BARS:
        continue
    SNARE.append((bar_beat(bar, 3.0), front))
    if section in ("B", "D"):
        SNARE.append((bar_beat(bar, 3.5), 0.46))
    elif section == "C":
        SNARE.append((bar_beat(bar, 2.75), 0.33))
        SNARE.append((bar_beat(bar, 3.75), 0.35))

# 通鼓过门：中—低—高—中，四个十六分音走完第 4 拍。
TOM_LOW, TOM_MID, TOM_HIGH = [], [], []
FILL_HITS = [(3.0, TOM_MID, 0.72), (3.25, TOM_LOW, 0.62),
             (3.5, TOM_HIGH, 0.80), (3.75, TOM_MID, 0.66)]
for bar in FILL_BARS:
    for beat, target, accent in FILL_HITS:
        target.append((bar_beat(bar, beat), accent))

# 铜钹：只在真正的段落交界响，Pigstep 几乎不用 crash——炸多了就变成"热闹"而不是"压迫"。
# 【试过又退回】曾按「神さびた古戦場」加了一轨 Splash 水镲（原曲用了 433 个），
# 但那 433 个是配 3/4 拍和 168BPM 的；在我们的 4/4 / 104BPM 里只显得吵。
CYMBAL = [(bar_beat(b, 0.0), 1.0) for b in (1, 17, 29)]


# ---------------------------------------------------------------- 压力层

# 极低频暗流：整曲一个持续的 A1（55Hz），给曲子垫一层"空气变重"的底压。
# 刻意不跟和声走——A 在 Am / F / Dm 里都是协和音，在 E 上形成的四度浑浊正是要的效果。
SUB = [(bar_beat(bar, 0.0), 7.8, "A1", 1.0) for bar in range(1, BARS + 1, 2)]

# 张力长音：每两小节一个，与 pad 同步拉满整块。第 9 小节起进场，C 段加重。
TENSION = []
for bar in range(9, BARS + 1, 2):
    weight = 0.42 if bar < 21 else 0.72
    TENSION.append((bar_beat(bar, 0.0), 7.6, CHORDS[chord_at(bar)]["tension"], weight))

# 踏板音：全曲只有一个不动的 A3（220Hz），音色用单簧管那种"空木"质感。
# 这是照着 Pigstep 学的——原曲那条 Muted Trumpet 轨 128 个音**全是同一个 A3**，
# 旋律怎么走它都不动。听感就是"有什么东西一直杵在那儿"，比任何律动都更压人。
PEDAL = [(bar_beat(bar, 0.0), 7.8, "A3", 1.0) for bar in range(1, BARS + 1, 2)]

# 强击和弦：段落交界处一记短促强奏 —— 根音 + 纯五度 + 八度（power chord 堆叠）。
# 【改过】原来是「根音 + 五度 + 上方小二度」，那个小二度给的是"诡异/不适"而不是"刺激"；
# 换成纯五度堆叠后，同一个位置变成"威武"的语汇。诡异和热血的差别就卡在这一个半音上。
STAB_BARS = (9, 13, 17, 21, 25, 29)
STAB = []
for bar in STAB_BARS:
    root = midi_number(CHORDS[chord_at(bar)]["bass"]) + 12   # 提到中音区
    for offset, weight in ((0, 1.00), (7, 0.92), (12, 0.80)):
        STAB.append((bar_beat(bar, 0.0), 1.2, note_name(root + offset), weight))


# ---------------------------------------------------------------- 渲染

def render_tonal(track, timbre: str, gain: float, pan: float,
                 detune_spread: float = 0.0, glide_cents: float = 0.0) -> np.ndarray:
    """把一条音符轨渲染成立体声 float32。

    detune_spread>0 时叠两路失谐副本做厚度；glide_cents!=0 时每个音都带一次起音滑音
    （见 additive 的说明）。
    """
    buf = np.zeros((TOTAL, 2), dtype=np.float32)
    left = math.sqrt((1.0 - pan) * 0.5 + 0.5 * 0.5)
    right = math.sqrt((1.0 + pan) * 0.5 + 0.5 * 0.5)
    for start, length, name, velocity in track:
        begin = int(start * BEAT * SR)
        n = int(length * BEAT * SR)
        if n <= 0 or begin >= TOTAL:
            continue
        n = min(n, TOTAL - begin)
        f = hz(midi_number(name))
        if detune_spread > 0.0:
            wave_data = (
                additive(f, n, timbre, -detune_spread, glide_cents) * 0.5
                + additive(f, n, timbre, detune_spread, glide_cents) * 0.5
            )
        else:
            wave_data = additive(f, n, timbre, 0.0, glide_cents)
        env = envelope(n, ENVELOPES[timbre])
        sig = wave_data * env * velocity * gain
        buf[begin:begin + n, 0] += sig * left
        buf[begin:begin + n, 1] += sig * right
    return buf


def render_kick(hits, gain: float = 1.0) -> np.ndarray:
    """底鼓：比定音鼓收得更快的音高下滑 + 一记鼓槌点击，负责把每拍"踩实"。"""
    rng = np.random.default_rng(20261009)
    buf = np.zeros((TOTAL, 2), dtype=np.float32)
    for start, velocity in hits:
        begin = int(start * BEAT * SR)
        n = int(0.42 * SR)
        if begin >= TOTAL:
            continue
        n = min(n, TOTAL - begin)
        t = np.arange(n) / SR
        pitch = 124.0 * np.exp(-t * 24.0) + 46.0     # 170Hz -> 46Hz，下滑比定音鼓陡得多
        body = np.sin(2.0 * math.pi * np.cumsum(pitch) / SR) * np.exp(-t * 6.5)
        click = rng.standard_normal(n) * np.exp(-t * 340.0) * 0.22
        sig = (body + click) * velocity * gain
        buf[begin:begin + n, 0] += sig
        buf[begin:begin + n, 1] += sig
    return buf


def render_hihat(hits, gain: float = 1.0, open_hat: bool = False) -> np.ndarray:
    """踩镲：高通噪声 + 极短衰减。开镲只是把衰减拉长，音色同一套。"""
    rng = np.random.default_rng(20261010)
    buf = np.zeros((TOTAL, 2), dtype=np.float32)
    length = 0.34 if open_hat else 0.075
    decay = 8.5 if open_hat else 58.0
    for start, velocity in hits:
        begin = int(start * BEAT * SR)
        n = int(length * SR)
        if begin >= TOTAL:
            continue
        n = min(n, TOTAL - begin)
        t = np.arange(n) / SR
        noise = rng.standard_normal(n + 2)
        # 二阶差分就是高通：把噪声里的低频轰鸣削掉，只留金属的"沙"声。
        hp = np.diff(noise, n=2)[:n]
        sig = hp * np.exp(-t * decay) * velocity * gain
        buf[begin:begin + n, 0] += sig
        buf[begin:begin + n, 1] += sig * 0.95
    return buf


def render_tom(hits, pitch_hz: float, gain: float = 1.0) -> np.ndarray:
    """通鼓：音高比底鼓高、下滑比底鼓浅，用来做过门时才有存在感。"""
    buf = np.zeros((TOTAL, 2), dtype=np.float32)
    for start, velocity in hits:
        begin = int(start * BEAT * SR)
        n = int(0.5 * SR)
        if begin >= TOTAL:
            continue
        n = min(n, TOTAL - begin)
        t = np.arange(n) / SR
        pitch = pitch_hz * (1.0 + 0.30 * np.exp(-t * 20.0))
        sig = np.sin(2.0 * math.pi * np.cumsum(pitch) / SR) * np.exp(-t * 9.0)
        sig += np.sin(2.0 * math.pi * np.cumsum(pitch * 1.58) / SR) * np.exp(-t * 24.0) * 0.28
        sig *= velocity * gain
        buf[begin:begin + n, 0] += sig
        buf[begin:begin + n, 1] += sig
    return buf


def render_timpani(hits, gain: float = 1.0) -> np.ndarray:
    """定音鼓：低频正弦快速下滑 + 短促衰减，带一点身体感。"""
    buf = np.zeros((TOTAL, 2), dtype=np.float32)
    for start, velocity in hits:
        begin = int(start * BEAT * SR)
        n = int(0.75 * SR)
        if begin >= TOTAL:
            continue
        n = min(n, TOTAL - begin)
        t = np.arange(n) / SR
        pitch = 62.0 * np.exp(-t * 5.0) + 44.0  # 从 106Hz 滑到 44Hz
        sig = np.sin(2.0 * math.pi * np.cumsum(pitch) / SR)
        sig *= np.exp(-t * 3.4) * velocity * gain
        buf[begin:begin + n, 0] += sig
        buf[begin:begin + n, 1] += sig
    return buf


def render_snare(hits, gain: float = 1.0) -> np.ndarray:
    """军鼓：噪声 + 短促包络，加一个 180Hz 的鼓皮音。"""
    rng = np.random.default_rng(20261008)
    buf = np.zeros((TOTAL, 2), dtype=np.float32)
    for start, velocity in hits:
        begin = int(start * BEAT * SR)
        n = int(0.28 * SR)
        if begin >= TOTAL:
            continue
        n = min(n, TOTAL - begin)
        t = np.arange(n) / SR
        noise = rng.standard_normal(n) * np.exp(-t * 26.0) * 0.55
        skin = np.sin(2.0 * math.pi * 182.0 * t) * np.exp(-t * 34.0) * 0.45
        sig = (noise + skin) * velocity * gain
        buf[begin:begin + n, 0] += sig
        buf[begin:begin + n, 1] += sig * 0.92
    return buf


def render_cymbal(hits, gain: float = 1.0, length: float = 2.6, decay: float = 1.5) -> np.ndarray:
    """铜钹：高通噪声 + 长衰减。把 length/decay 调小就变成"水镲"（Splash）：更短、更炸。"""
    rng = np.random.default_rng(5201314)
    buf = np.zeros((TOTAL, 2), dtype=np.float32)
    for start, velocity in hits:
        begin = int(start * BEAT * SR)
        n = int(length * SR)
        if begin >= TOTAL:
            continue
        n = min(n, TOTAL - begin)
        t = np.arange(n) / SR
        noise = rng.standard_normal(n)
        # 一阶高通，去掉低频轰鸣
        hp = np.empty(n)
        prev_in = prev_out = 0.0
        alpha = 0.92
        for i in range(n):
            cur = noise[i]
            prev_out = alpha * (prev_out + cur - prev_in)
            prev_in = cur
            hp[i] = prev_out
        sig = hp * np.exp(-t * decay) * velocity * gain * 0.5
        buf[begin:begin + n, 0] += sig
        buf[begin:begin + n, 1] += sig
    return buf


def reverb(x: np.ndarray, mix: float = 0.26, decay: float = 0.62) -> np.ndarray:
    """多抽头延迟混响：比卷积便宜，对这类氛围足够。"""
    taps = [(0.029, 1.00), (0.043, 0.82), (0.061, 0.66), (0.079, 0.52),
            (0.101, 0.40), (0.127, 0.30), (0.157, 0.22)]
    wet = np.zeros_like(x)
    for delay_s, amp in taps:
        d = int(delay_s * SR)
        if d >= x.shape[0]:
            continue
        gain = amp * (decay ** 3)
        wet[d:, 0] += x[:-d, 0] * gain
        wet[d:, 1] += x[:-d, 1] * gain * 0.94
    return x * (1.0 - mix) + wet * mix


def soft_clip(x: np.ndarray, ceiling: float = 0.94) -> np.ndarray:
    """总线软限幅：tanh 形状，避免硬削顶。"""
    return np.tanh(x / ceiling) * ceiling


# ---------------------------------------------------------------- 音频落盘

def write_wav(path: str, audio: np.ndarray) -> None:
    """标准库写 16bit PCM WAV（不依赖 soundfile）。"""
    clipped = np.clip(audio, -1.0, 1.0)
    ints = (clipped * 32767.0).astype("<i2")
    with wave.open(path, "wb") as handle:
        handle.setnchannels(2)
        handle.setsampwidth(2)
        handle.setframerate(SR)
        handle.writeframes(ints.tobytes())


def _ffmpeg_exe() -> str:
    """定位 ffmpeg：优先 imageio-ffmpeg 自带的二进制，其次 tools/ffmpeg.exe，最后 PATH。"""
    try:
        import imageio_ffmpeg  # 可选依赖
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        pass
    local = os.path.join(ROOT, "tools", "ffmpeg.exe")
    return local if os.path.isfile(local) else "ffmpeg"


def write_ogg(src_wav: str, dst_ogg: str) -> None:
    """用 ffmpeg 把 WAV 编成 Vorbis（Minecraft 只认 ogg）。

    优先 libvorbis；某些构建没编进这个外部库时退回 ffmpeg 自带的实验性 vorbis 编码器。
    """
    ffmpeg = _ffmpeg_exe()
    for codec in ("libvorbis", "vorbis"):
        args = [ffmpeg, "-y", "-i", src_wav, "-c:a", codec, "-q:a", "5"]
        if codec == "vorbis":
            args += ["-strict", "experimental"]
        args.append(dst_ogg)
        try:
            result = subprocess.run(args, capture_output=True)
        except FileNotFoundError as exc:
            raise RuntimeError(
                "找不到 ffmpeg。把 ffmpeg.exe 放到 tools/ 下，或装好后加入 PATH；"
                f"WAV 已生成，也可以用别的工具手动转 ogg。（{exc}）"
            ) from exc
        if result.returncode == 0 and os.path.getsize(dst_ogg) > 0:
            return
    raise RuntimeError(
        f"ffmpeg 无法编码 OGG：{result.stderr.decode('utf-8', 'replace')[-400:]}")


# ---------------------------------------------------------------- MIDI 导出
# 直接手写标准 MIDI 文件（SMF Type 1），不依赖任何第三方库。

def _vlq(value: int) -> bytes:
    """MIDI 的可变长度量。"""
    out = bytearray([value & 0x7F])
    value >>= 7
    while value:
        out.append((value & 0x7F) | 0x80)
        value >>= 7
    return bytes(reversed(out))


def _track(events: list[tuple[int, bytes]], name: str) -> bytes:
    """把 (绝对 tick, 事件字节) 列表打包成一个 MTrk 块。"""
    body = bytearray()
    # 开头那个 \x00 是**必需的 delta-time**：SMF 里 meta 事件也要以 delta 开头，
    # 漏掉它会直接破坏整条轨的事件流解析（轨名读不出来、后续事件全部错位）。
    body += b"\x00\xFF\x03" + _vlq(len(name)) + name.encode("ascii")
    prev = 0
    for tick, payload in sorted(events, key=lambda e: e[0]):
        body += _vlq(tick - prev) + payload
        prev = tick
    body += _vlq(0) + b"\xFF\x2F\x00"  # end of track
    return b"MTrk" + len(body).to_bytes(4, "big") + bytes(body)


def _note_track(notes, program: int, channel: int, name: str) -> bytes:
    events: list[tuple[int, bytes]] = [(0, bytes([0xC0 | channel, program]))]
    for start, length, pitch, velocity in notes:
        note = midi_number(pitch)
        vel = max(1, min(127, int(velocity * 110)))
        begin = int(start * TPB)
        end = int((start + length) * TPB)
        events.append((begin, bytes([0x90 | channel, note, vel])))
        events.append((end, bytes([0x80 | channel, note, 0])))
    return _track(events, name)


def _percussion_track(hits, note: int, name: str) -> bytes:
    events: list[tuple[int, bytes]] = []
    for start, velocity in hits:
        tick = int(start * TPB)
        vel = max(1, min(127, int(velocity * 110)))
        events.append((tick, bytes([0x99, note, vel])))
        events.append((tick + 8, bytes([0x89, note, 0])))
    return _track(events, name)


def write_midi() -> None:
    """把同一份乐谱写成标准 MIDI：可以在 DAW 里换音源重新渲染。"""
    tracks = [
        _note_track(DRONE, 19, 0, "Drone"),          # Church Organ（不动的底压）
        _note_track(SUB, 38, 5, "Sub"),              # Synth Bass 1（次低频暗流）
        _note_track(BASS, 38, 1, "Bass"),            # Synth Bass 1（失真锯齿低音）
        _note_track(BASS_LOW, 38, 10, "BassLow"),    # Synth Bass 1（低八度墙，渲染层真的叠了它）
        _note_track(PAD, 89, 2, "Pad"),              # Pad 2 (warm)
        _note_track(MELODY, 0, 3, "Theme-Piano"),    # Acoustic Grand（主题）
        _note_track(PULSE, 0, 4, "Pulse-Piano"),     # Acoustic Grand（高八度十六分琶音层）
        _note_track(STAB, 0, 7, "Power"),            # Acoustic Grand（纯五度强击）
        _percussion_track(KICK, 36, "Kick"),         # Bass Drum 1
        _percussion_track(TIMBANI, 41, "Timpani"),   # Low Floor Tom
        _percussion_track(SNARE, 38, "Snare"),       # Acoustic Snare
        _percussion_track(HH_CLOSED, 42, "HiHatClose"),   # Closed Hi-Hat
        _percussion_track(HH_OPEN, 46, "HiHatOpen"),      # Open Hi-Hat
        _percussion_track(TOM_LOW, 47, "TomLow"),    # Low-Mid Tom
        _percussion_track(TOM_MID, 45, "TomMid"),    # Low Tom
        _percussion_track(TOM_HIGH, 50, "TomHigh"),  # High Tom
        _percussion_track(CYMBAL, 49, "Cymbal"),     # Crash Cymbal 1
    ]
    header = b"MThd" + (6).to_bytes(4, "big") + (1).to_bytes(2, "big") \
        + len(tracks).to_bytes(2, "big") + TPB.to_bytes(2, "big")
    with open(MID_PATH, "wb") as handle:
        handle.write(header)
        for chunk in tracks:
            handle.write(chunk)
    print(f"MIDI   -> {MID_PATH}")


# ---------------------------------------------------------------- 主流程

def main() -> None:
    print(f"作曲参数：A 小调 / {BPM:g} BPM / {BARS} 小节 / {DUR:.1f} 秒")

    print("渲染 Drone / Sub ...")
    drone = render_tonal(DRONE, "organ", 0.130, 0.0, detune_spread=4.0)
    sub = render_tonal(SUB, "sub", 0.115, 0.0)
    print("渲染 Bass（含低八度层）/ Pad ...")
    bass = render_tonal(BASS, "synthbass", 0.115, -0.12, glide_cents=-40)
    bass_low = render_tonal(BASS_LOW, "synthbass", 0.085, -0.12)
    pad = render_tonal(PAD, "strings", 0.055, 0.18, detune_spread=7.0)
    print("渲染 Theme（钢琴）/ Pulse（高八度钢琴琶音）/ 强击和弦 ...")
    # 钢琴不做滑音也不做失谐：它靠"快起音 + 快衰减"的颗粒感立住，加了滑音反而糊。
    theme = render_tonal(MELODY, "piano", 0.300, 0.0)
    pulse = render_tonal(PULSE, "piano", 0.090, -0.22)
    stab = render_tonal(STAB, "piano", 0.110, 0.0)
    print("渲染 鼓组 ...")
    kick = render_kick(KICK, gain=0.30)
    timpani = render_timpani(TIMBANI, gain=0.21)   # 从 0.34 降下来给底鼓让位，两件在正拍齐奏
    snare = render_snare(SNARE, gain=0.125)
    hh_closed = render_hihat(HH_CLOSED, gain=0.050)
    hh_open = render_hihat(HH_OPEN, gain=0.058, open_hat=True)
    tom_low = render_tom(TOM_LOW, 138.0, gain=0.155)
    tom_mid = render_tom(TOM_MID, 116.0, gain=0.160)
    tom_high = render_tom(TOM_HIGH, 195.0, gain=0.135)
    cymbal = render_cymbal(CYMBAL, gain=0.20)

    print("混音 + 混响 ...")
    # TENSION（张力长音）与 PEDAL（固定踏板音）已停用：它们是"不解决"带来的诡异感的来源，
    # 定义还留在上面，想恢复把人声部加回来即可。
    body = (drone + bass + bass_low + pad + theme + pulse + stab
            + kick + timpani + snare + hh_closed + hh_open
            + tom_low + tom_mid + tom_high + cymbal)
    # 归一化时把 sub 排除在外：55Hz 正弦的振幅天然最大，让它参与的话会把整首曲子的
    # 其它部分一起压扁——结果是"更压抑但更听不清"。sub 等归一化之后再叠上去。
    peak = float(np.max(np.abs(body))) or 1.0
    mix = body / peak * 0.80 + sub
    # 混响大幅收紧（0.30 → 0.12）：Pigstep 是干声，大混响会立刻把电子编制拉回"管弦大厅"。
    mix = reverb(mix, mix=0.12, decay=0.45)
    mix = soft_clip(mix)

    # 最后把总线顶到 -1.5dBFS：游戏里 BGM 走「音乐」滑块能单独调，不需要预留余量，
    # 而"压迫感"有一半就是响度——留 6dB 余量的交响乐听起来只会像背景音。
    # 留 1.5dB 而不是 1dB：Vorbis 是有损编码，解码时会有轻微过冲，贴太近会削顶。
    peak = float(np.max(np.abs(mix))) or 1.0
    mix = mix / peak * 0.84

    # 冻结结尾，循环时不会有咔哒声
    fade = int(0.05 * SR)
    mix[:fade] *= np.linspace(0.0, 1.0, fade)[:, None]
    mix[-fade:] *= np.linspace(1.0, 0.0, fade)[:, None]

    print("写 WAV ...")
    write_wav(WAV_PATH, mix)
    print(f"WAV    -> {WAV_PATH}")

    print("写 OGG ...")
    os.makedirs(os.path.dirname(OGG_PATH), exist_ok=True)
    write_ogg(WAV_PATH, OGG_PATH)
    print(f"OGG    -> {OGG_PATH}  ({os.path.getsize(OGG_PATH) / 1024:.0f} KB)")

    write_midi()


if __name__ == "__main__":
    main()
