"""把指挥官的动作渲染成 GIF（逐帧固定取景，避免"忽大忽小"）。

为什么单独一个工具
------------------
`preview_bbmodel.py` 只能出静态分镜（一列一个时刻），看不出"动"。这里按 20 fps
（=  Minecraft 的一个游戏 tick）逐帧采样，用同一套取景参数渲染，再用 ffmpeg 的
`palettegen` / `paletteuse` 两遍法合成 GIF —— 单遍 GIF 会因为全局调色板过小而出现色带。

取景参数是对**所有帧的包围盒取并集**之后算一次的：只按静止姿势量会被抬旗/挥刀的那几帧
顶出去（画面被切），每帧各自量又会抖。

用法
----
    python tools/render_anim_gif.py                 # 出全部四支 GIF
    python tools/render_anim_gif.py walk melee      # 只出指定的
"""

from __future__ import annotations

import argparse
import base64
import json
import math
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import preview_bbmodel as pv  # noqa: E402

import imageio_ffmpeg  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
BBMODEL = ROOT / "tools/zombie_commander.bbmodel"
ANIM_DIR = (ROOT / "src/main/resources/assets/dreamingfishcore/neoforge/animations"
            / "entity/zombie_commander")
OUT_DIR = ROOT / "tools"

FPS = 20
SIZE = 300

#: 每支 GIF = 若干部动画顺序连播（名, 播放长度占该动画总长的比例）
SPECS = {
    "walk": dict(parts=[("walk", 2.0)], view=210.0, hold=0.0,
                 note="行走循环（放两圈，方便看循环点）"),
    "melee": dict(parts=[("melee_1", 1.0), ("melee_2", 1.0), ("melee_3", 1.0)],
                  view=215.0, hold=0.25, note="近战三段连招：横斩 → 反手斩 → 举刀过顶劈"),
    "shoot": dict(parts=[("shoot", 1.0)], view=205.0, hold=0.30,
                  note="射击：蓄力 → 三连发 → 收枪"),
    "summon": dict(parts=[("summon", 1.0)], view=210.0, hold=0.30,
                   note="召唤：拔旗 → 高举 → 用力插进身前地里 → 拔回背上"),
}


def anim_length(name: str) -> float:
    doc = json.loads((ANIM_DIR / f"{name}.json").read_text(encoding="utf-8"))
    return float(doc["length"])


def frame_times(parts) -> list[tuple[str, float]]:
    """展开成 [(动画名, 该帧在动画内的时间), ...]，末尾按需补若干静止帧。"""
    out: list[tuple[str, float]] = []
    for name, fraction in parts:
        total = anim_length(name) * fraction
        steps = max(1, round(total * FPS))
        for i in range(steps):
            out.append((name, i / FPS))
    return out


def union_fit(bb, order, views, frames):
    """把所有帧的立方体角点并成一个包围盒，再量一次取景 —— 既不会切到动作，也不会抖。"""
    lo = [math.inf] * 3
    hi = [-math.inf] * 3
    for name, t in frames:
        model = pv.build(bb, order, pv.load_anim(str(ANIM_DIR / f"{name}.json"), t))
        for cube in model["cubes"]:
            for face in pv.FACES:
                if cube["faces"].get(face) is None:
                    continue
                for p in pv.FACES[face](cube["min"], cube["max"]):
                    w = pv.mat_apply(cube["matrix"], p)
                    for i in range(3):
                        lo[i] = min(lo[i], w[i])
                        hi[i] = max(hi[i], w[i])
    uv = [0, 0, 1, 1]
    synthetic = {"cubes": [{
        "min": tuple(lo), "max": tuple(hi),
        "faces": {f: uv for f in pv.FACES},
        "matrix": pv.IDENT, "name": "_bbox", "uuid": "-", "parent": None,
    }]}
    return pv.measure(synthetic, order, views, SIZE)


def render_gif(bb, tex, order, key: str) -> Path:
    spec = SPECS[key]
    views = [spec["view"]]
    frames = frame_times(spec["parts"])
    if spec["hold"] > 0:
        frames += [frames[-1]] * round(spec["hold"] * FPS)

    fit = union_fit(bb, order, views, frames)
    tmp = Path(tempfile.mkdtemp(prefix=f"animgif_{key}_"))
    try:
        for i, (name, t) in enumerate(frames):
            model = pv.build(bb, order, pv.load_anim(str(ANIM_DIR / f"{name}.json"), t))
            w, h, buf = pv.render(model, tex, order, views, size=SIZE, fit=fit)
            pv.write_png(tmp / f"f{i:04d}.png", w, h, buf)

        ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
        pattern = str(tmp / "f%04d.png")
        palette = tmp / "palette.png"
        out = OUT_DIR / f"anim_{key}.gif"
        subprocess.run([ffmpeg, "-y", "-framerate", str(FPS), "-i", pattern,
                        "-vf", "palettegen=stats_mode=diff", str(palette)],
                       capture_output=True, check=True)
        subprocess.run([ffmpeg, "-y", "-framerate", str(FPS), "-i", pattern, "-i", str(palette),
                        "-lavfi", "paletteuse=dither=bayer:bayer_scale=3",
                        "-loop", "0", str(out)],
                       capture_output=True, check=True)
        print(f"  {out.name:16s} {len(frames):3d} 帧 / {len(frames) / FPS:.2f}s / "
              f"{out.stat().st_size / 1024:.0f} KB   {spec['note']}")
        return out
    finally:
        for f in tmp.glob("*.png"):
            f.unlink()
        tmp.rmdir()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("keys", nargs="*", default=None,
                    help="要出的动画（walk / melee / shoot / summon），不给就出全部")
    ap.add_argument("--order", default="ZYX")
    args = ap.parse_args()

    keys = args.keys or list(SPECS)
    for k in keys:
        if k not in SPECS:
            raise SystemExit(f"未知动画 {k}，可选：{', '.join(SPECS)}")

    bb = json.loads(BBMODEL.read_text(encoding="utf-8"))
    tex = pv.read_png_bytes(base64.b64decode(bb["textures"][0]["source"].split(",", 1)[1]))
    order = args.order.upper()
    print(f"渲染 {len(keys)} 支 GIF（{FPS} fps / {SIZE}px）：")
    for k in keys:
        render_gif(bb, tex, order, k)


if __name__ == "__main__":
    main()
