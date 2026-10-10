#!/usr/bin/env python3
"""MIDI 分析器：把一首 MIDI 拆成"可复用的编曲数字"，用来给自制 BGM 找参照。

不依赖任何第三方库（SMF 解析是手写的，见 tools/compose_commander_bgm.py 的反向用法）。
关注的是**能被抄的手法**而不是音色本身：
  * 有几个声部、各干什么
  * 旋律的音程是级进还是跳进、跳多大
  * 节奏密度：多少比例是十六分
  * 鼓轨有多密、用了哪几件
  * 速度、时长

用法（本机隔离环境）：
  C:/Users/passk/.workbuddy/binaries/python/envs/default/Scripts/python.exe \
      tools/analyze_midi.py <a.mid> [b.mid ...]
"""

from __future__ import annotations

import struct
import sys
from collections import Counter

NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]

# GM 音色名（只列会用到的，其余显示编号）
GM = {
    0: "Acoustic Grand", 4: "Electric Piano", 19: "Church Organ", 24: "Nylon Guitar",
    25: "Steel Guitar", 27: "E.Guitar clean", 29: "Overdriven Guitar", 30: "Distortion Guitar",
    33: "Acoustic Bass", 34: "Fingered Bass", 35: "Pick Bass", 38: "Synth Bass 1",
    40: "Violin", 41: "Viola", 42: "Cello", 43: "Contrabass", 46: "Harp",
    48: "Strings", 56: "Trumpet", 57: "Trombone", 58: "Tuba", 60: "French Horn",
    61: "Brass Section", 65: "Soprano Sax", 66: "Alto Sax", 68: "Oboe",
    71: "Clarinet", 72: "Piccolo", 73: "Flute", 74: "Recorder",
    80: "Lead (square)", 81: "Lead (sawtooth)", 82: "Lead (calliope)",
    87: "Lead (bass+lead)", 88: "Pad (new age)", 89: "Pad (warm)",
    90: "Pad (polysynth)", 91: "Pad (choir)", 95: "Pad (sweep)",
}

DRUM = {
    35: "Kick2", 36: "Kick", 37: "SideStick", 38: "Snare", 39: "Clap",
    40: "Snare2", 41: "TomFloor", 42: "HiHat", 43: "TomFloor2", 44: "PedalHat",
    45: "TomLow", 46: "OpenHat", 47: "TomLowMid", 48: "TomHiMid", 49: "Crash",
    50: "TomHigh", 51: "Ride", 52: "China", 53: "RideBell", 54: "Tambourine",
    55: "Splash", 57: "Crash2", 59: "Ride2",
}


def note_name(n: int) -> str:
    return NAMES[n % 12] + str(n // 12 - 1)


def read_vlq(data: bytes, i: int) -> tuple[int, int]:
    value = 0
    while True:
        b = data[i]
        i += 1
        value = (value << 7) | (b & 0x7F)
        if not (b & 0x80):
            return value, i


def parse(path: str):
    data = open(path, "rb").read()
    if data[:4] != b"MThd":
        raise ValueError("不是标准 MIDI")
    fmt, ntracks, division = struct.unpack(">HHH", data[8:14])
    i = 14
    tracks = []
    while i + 8 <= len(data) and data[i:i + 4] == b"MTrk":
        length = struct.unpack(">I", data[i + 4:i + 8])[0]
        tracks.append(data[i + 8:i + 8 + length])
        i += 8 + length
    return fmt, division, tracks


def scan_track(body: bytes):
    """扫一条轨：名字 / 程序号 / 通道 / 音符 / 速度变化 / 弯音次数。"""
    i = 0
    tick = 0
    status = 0
    name = None
    channel = None
    program = None
    notes = []            # (start_tick, end_tick, pitch)
    pending: dict[int, list[int]] = {}
    tempos = []
    timesigs = []
    bends = 0
    while i < len(body):
        delta, i = read_vlq(body, i)
        tick += delta
        if i >= len(body):
            break
        b = body[i]
        if b == 0xFF:
            i += 1
            mtype = body[i]
            i += 1
            mlen, i = read_vlq(body, i)
            mdata = body[i:i + mlen]
            i += mlen
            if mtype == 0x03 and name is None:
                name = mdata.decode("utf-8", "replace").rstrip("\x00")
            elif mtype == 0x51:
                tempos.append((tick, int.from_bytes(mdata[:3], "big")))
            elif mtype == 0x58:
                timesigs.append((tick, mdata[0], 2 ** mdata[1]))
        elif b in (0xF0, 0xF7):
            i += 1
            mlen, i = read_vlq(body, i)
            i += mlen
        else:
            if b & 0x80:
                status = b
                i += 1
            hi = status & 0xF0
            if channel is None and hi != 0xF0:
                channel = status & 0x0F
            if hi in (0x80, 0x90, 0xA0, 0xB0, 0xE0):
                d1, d2 = body[i], body[i + 1]
                i += 2
                if hi == 0x90 and d2 > 0:
                    pending.setdefault(d1, []).append(tick)
                elif hi == 0x80 or (hi == 0x90 and d2 == 0):
                    if pending.get(d1):
                        notes.append((pending[d1].pop(0), tick, d1))
                elif hi == 0xE0:
                    bends += 1
            elif hi in (0xC0, 0xD0):
                d1 = body[i]
                i += 1
                if hi == 0xC0 and program is None:
                    program = d1
            else:
                break
    return dict(name=name or "?", channel=channel, program=program,
                notes=notes, tempos=tempos, timesigs=timesigs, bends=bends)


def analyze(path: str) -> None:
    print("=" * 78)
    print(path.rsplit("/", 1)[-1])
    try:
        fmt, division, bodies = parse(path)
    except Exception as exc:                       # noqa: BLE001
        print("  解析失败:", exc)
        return
    heads = [scan_track(b) for b in bodies]

    tempos = sorted({t for h in heads for _, t in h["tempos"]})
    bpm_list = [round(60_000_000 / t, 1) for t in tempos] or [120.0]
    sigs = sorted({(n, d) for h in heads for _, n, d in h["timesigs"]}) or [(4, 4)]

    all_notes = [n for h in heads for n in h["notes"]]
    if not all_notes:
        print("  （没有音符）")
        return
    end_tick = max(n[1] for n in all_notes)
    beats = end_tick / division
    bpm = bpm_list[0]
    seconds = beats / bpm * 60.0

    print("  格式 %d / %d 轨 / division %d | 速度 %s BPM | 拍号 %s"
          % (fmt, len(bodies), division,
             "/".join(str(b) for b in bpm_list[:4]), sigs[:2]))
    print("  时长 %.1f 秒（%.0f 拍）| 总音符 %d | 全曲密度 %.1f 音/秒"
          % (seconds, beats, len(all_notes), len(all_notes) / max(seconds, 1)))

    # ---- 逐轨 ----
    print("  轨道：")
    for idx, h in enumerate(heads):
        notes = h["notes"]
        if not notes:
            continue
        pitches = [n[2] for n in notes]
        lens = [(n[1] - n[0]) / division for n in notes]
        role = "鼓" if h["channel"] == 9 else (
            GM.get(h["program"], "PGM %s" % h["program"]) if h["program"] is not None else "?")
        tag = " %-16s" % role
        if h["channel"] == 9:
            kinds = Counter(DRUM.get(p, str(p)) for p in pitches)
            tag += " 件数%d" % len(kinds)
        print("    #%02d %-18s ch%-2s %s | %4d 音 | %s-%s | 平均 %.2f 拍"
              % (idx, (h["name"] or "?")[:18], h["channel"], tag, len(notes),
                 note_name(min(pitches)), note_name(max(pitches)), sum(lens) / len(lens)))
        if h["channel"] == 9:
            print("        鼓件:", ", ".join("%s×%d" % kv for kv in kinds.most_common(8)))
        else:
            c = Counter(round(l, 2) for l in lens)
            print("        时值:", ", ".join("%.2f拍×%d" % kv for kv in c.most_common(6)))
        if h["bends"]:
            print("        弯音事件 %d 次（说明有滑音/装饰）" % h["bends"])

    # ---- 旋律（音符最多的非鼓轨）分析 ----
    melodic = [h for h in heads if h["channel"] != 9 and len(h["notes"]) >= 8]
    if melodic:
        lead = max(melodic, key=lambda h: len(h["notes"]))
        seq = sorted(lead["notes"], key=lambda n: n[0])
        pitches = [n[2] for n in seq]
        steps = [b - a for a, b in zip(pitches, pitches[1:])]
        leaps = [abs(s) for s in steps if s != 0]
        c = Counter()
        for s in steps:
            if s == 0:
                c["同音"] += 1
            elif abs(s) <= 2:
                c["级进(≤2半音)"] += 1
            elif abs(s) <= 5:
                c["小跳(3-5)"] += 1
            else:
                c["大跳(≥6)"] += 1
        total = max(1, len(steps))
        print("  主旋律（#%d %s，%d 音）：" % (heads.index(lead), lead["name"], len(pitches)))
        print("        音域 %s-%s | 跳进统计: %s"
              % (note_name(min(pitches)), note_name(max(pitches)),
                 "  ".join("%s %.0f%%" % (k, v * 100 / total) for k, v in c.most_common())))
        if leaps:
            print("        最大跳进 %d 半音 | 平均 |跳进| %.1f 半音"
                  % (max(leaps), sum(leaps) / len(leaps)))
        ups = sum(1 for s in steps if s > 0)
        downs = sum(1 for s in steps if s < 0)
        print("        上行 %d / 下行 %d（%.0f%% 上行）" % (ups, downs, ups * 100 / total))


def main() -> None:
    paths = sys.argv[1:]
    if not paths:
        print(__doc__)
        return
    for p in paths:
        analyze(p)


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8")   # Windows 控制台默认 GBK，中文会炸
    except Exception:                              # noqa: BLE001
        pass
    main()
