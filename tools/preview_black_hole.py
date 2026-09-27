"""Rasterize BlackHolePreview's production vertices with interpolated color and alpha.

Compile BlackHoleGeometry.java and tools/BlackHolePreview.java into build/black-hole-preview,
run BlackHolePreview build/black-hole-preview [camera pitch], then run this script.
This verifies mesh composition; Minecraft terrain depth and shader-pack behavior need in-game QA.
"""
from pathlib import Path
import struct

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "build/black-hole-preview"
W, H = 640, 460
SCALE = 122


def triangle(canvas, vertices, additive):
    xy = vertices[:, :2]
    x0, y0 = np.floor(xy.min(axis=0)).astype(int)
    x1, y1 = np.ceil(xy.max(axis=0)).astype(int)
    x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, W - 1), min(y1, H - 1)
    if x0 > x1 or y0 > y1:
        return
    a, b, c = xy
    denom = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
    if abs(denom) < 1e-7:
        return
    yy, xx = np.mgrid[y0:y1 + 1, x0:x1 + 1] + 0.5
    u = ((b[1] - c[1]) * (xx - c[0]) + (c[0] - b[0]) * (yy - c[1])) / denom
    v = ((c[1] - a[1]) * (xx - c[0]) + (a[0] - c[0]) * (yy - c[1])) / denom
    w = 1 - u - v
    mask = (u >= 0) & (v >= 0) & (w >= 0)
    rgba = u[..., None] * vertices[0, 3:] + v[..., None] * vertices[1, 3:] + w[..., None] * vertices[2, 3:]
    alpha = np.clip(rgba[..., 3:4], 0, 1)
    region = canvas[y0:y1 + 1, x0:x1 + 1]
    result = rgba[..., :3] * alpha + region * (1 if additive else 1 - alpha)
    region[mask] = np.clip(result[mask], 0, 1)


def render(meshes, sine, cosine):
    ground = Image.new("RGB", (W, H), (28, 34, 36))
    draw = ImageDraw.Draw(ground)
    def ground_point(x, z):
        return W / 2 + x * SCALE, H * 0.59 + z * sine * SCALE
    for x in range(-12, 13):
        draw.line([ground_point(x, -15), ground_point(x, 15)], fill=(38, 46, 46))
    for z in range(-15, 16):
        draw.line([ground_point(-12, z), ground_point(12, z)], fill=(38, 46, 46))
    canvas = np.asarray(ground).astype(np.float32) / 255
    for index, mesh in enumerate(meshes):
        projected = mesh.copy()
        projected[:, 0] = W / 2 + mesh[:, 0] * SCALE
        projected[:, 1] = H * 0.59 - (mesh[:, 1] * cosine - mesh[:, 2] * sine) * SCALE
        for quad in projected.reshape(-1, 4, 7):
            triangle(canvas, quad[[0, 1, 2]], index in (1, 3))
            triangle(canvas, quad[[0, 2, 3]], index in (1, 3))
    return Image.fromarray((canvas * 255).astype(np.uint8))


def main():
    data = (OUTPUT / "mesh.bin").read_bytes()
    count, sine, cosine = struct.unpack_from(">iff", data)
    offset = 12
    phases = ["01  /  GRAVITY WELL", "02  /  ACCRETION", "03  /  COMPRESSION",
              "04  /  RELEASE", "05  /  SHOCK FRONT", "06  /  AFTERWAVE"]
    sheet = Image.new("RGB", (W * 3, (H + 50) * 2 + 96), (13, 17, 23))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 21)
    small = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 16)
    draw.text((28, 18), "SINGULARITY  /  RANK II+", font=font, fill=(241, 216, 183))
    draw.text((28, 54), "Production mesh preview - 1.8 s sequence - software alpha composition - not an in-game capture",
              font=small, fill=(138, 149, 168))
    for i in range(count):
        progress, = struct.unpack_from(">f", data, offset)
        offset += 4
        meshes = []
        for _ in range(4):
            length, = struct.unpack_from(">i", data, offset)
            offset += 4
            meshes.append(np.frombuffer(data, dtype=">f4", count=length, offset=offset).astype(np.float32).reshape(-1, 7))
            offset += length * 4
        frame = render(meshes, sine, cosine)
        frame.save(OUTPUT / f"phase-{i}.png")
        x, y = (i % 3) * W, 96 + (i // 3) * (H + 50)
        sheet.paste(frame, (x, y))
        draw.text((x + 22, y + H + 9), f"{phases[i]}      {progress * 1.8:.2f}s", font=small, fill=(204, 193, 179))
    sheet.save(OUTPUT / "black-hole-sequence.png")
    print(OUTPUT / "black-hole-sequence.png")


if __name__ == "__main__":
    main()
