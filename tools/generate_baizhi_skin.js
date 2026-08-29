const fs = require("fs");
const path = require("path");
const { PNG } = require("pngjs");

const workspace = path.resolve(__dirname, "..");
const skinPath = path.join(
  workspace,
  "src/main/resources/assets/dreamingfishcore/textures/entity/npc/baizhi.png"
);
const previewPath = path.join(workspace, "design_previews/baizhi_skin_preview.png");

const skin = new PNG({ width: 64, height: 64, colorType: 6 });
skin.data.fill(0);

const C = {
  skin: "#efbea1",
  skinLight: "#f7cdb3",
  skinShadow: "#d99a7e",
  blush: "#e89b99",
  hair: "#49302e",
  hairLight: "#66433d",
  hairShadow: "#2d1d1e",
  eye: "#784822",
  eyeLight: "#d99545",
  eyeWhite: "#f7f3eb",
  coat: "#e8ecea",
  coatLight: "#f7f6ef",
  coatShadow: "#bcc7c7",
  coatDeep: "#97a9aa",
  dust: "#cdb9a6",
  teal: "#447b79",
  tealLight: "#5a9792",
  tealDark: "#315b5b",
  pants: "#303842",
  pantsLight: "#414b58",
  pantsDark: "#232a33",
  shoe: "#d7d9d5",
  shoeShadow: "#868d90",
  sole: "#687176",
  stethoscope: "#303b42",
  metal: "#a9bdc0",
  cyan: "#5ccbd0",
  cyanDark: "#318e94",
  pen: "#b54a48"
};

function color(hex, alpha = 255) {
  const value = Number.parseInt(hex.slice(1), 16);
  return [(value >> 16) & 255, (value >> 8) & 255, value & 255, alpha];
}

function pixel(target, x, y, value) {
  if (x < 0 || y < 0 || x >= target.width || y >= target.height) return;
  const rgba = Array.isArray(value) ? value : color(value);
  const index = (y * target.width + x) * 4;
  target.data[index] = rgba[0];
  target.data[index + 1] = rgba[1];
  target.data[index + 2] = rgba[2];
  target.data[index + 3] = rgba[3];
}

function fill(target, x, y, width, height, value) {
  for (let py = y; py < y + height; py++) {
    for (let px = x; px < x + width; px++) pixel(target, px, py, value);
  }
}

function fabric(x, y, width, height, base, light, dark, seed = 0) {
  fill(skin, x, y, width, height, base);
  for (let px = 0; px < width; px++) {
    pixel(skin, x + px, y, light);
    if ((px + seed) % 3 === 0 && height > 2) pixel(skin, x + px, y + 2, light);
  }
  for (let py = 1; py < height; py++) {
    pixel(skin, x, y + py, dark);
    if ((py + seed) % 4 === 0 && width > 2) pixel(skin, x + width - 2, y + py, light);
  }
  if (width > 2 && height > 3) pixel(skin, x + width - 1, y + height - 2, dark);
}

function paintHead() {
  // Base faces.
  fill(skin, 8, 0, 8, 8, C.hair);
  fill(skin, 16, 0, 8, 8, C.skinShadow);
  fill(skin, 0, 8, 8, 8, C.skin);
  fill(skin, 8, 8, 8, 8, C.skin);
  fill(skin, 16, 8, 8, 8, C.skin);
  fill(skin, 24, 8, 8, 8, C.hair);

  // Hair texture on top and back.
  for (let i = 0; i < 8; i++) {
    pixel(skin, 8 + i, i % 2, C.hairLight);
    pixel(skin, 8 + ((i * 3) % 8), 3 + (i % 4), C.hairShadow);
    pixel(skin, 24 + (i % 8), 9 + (i % 6), i % 3 === 0 ? C.hairLight : C.hairShadow);
  }

  // Side hair and ears.
  fill(skin, 0, 8, 8, 3, C.hair);
  fill(skin, 0, 11, 2, 5, C.hair);
  fill(skin, 6, 11, 2, 5, C.hairShadow);
  pixel(skin, 4, 12, C.skinShadow);
  pixel(skin, 4, 13, C.skinLight);
  fill(skin, 16, 8, 8, 3, C.hair);
  fill(skin, 16, 11, 2, 5, C.hairShadow);
  fill(skin, 22, 11, 2, 5, C.hair);
  pixel(skin, 19, 12, C.skinShadow);
  pixel(skin, 19, 13, C.skinLight);

  // Front bangs and face.
  fill(skin, 8, 8, 8, 2, C.hair);
  pixel(skin, 8, 10, C.hairShadow);
  pixel(skin, 9, 10, C.hair);
  pixel(skin, 11, 10, C.hair);
  pixel(skin, 14, 10, C.hair);
  pixel(skin, 15, 10, C.hairShadow);
  pixel(skin, 8, 11, C.hairShadow);
  pixel(skin, 15, 11, C.hairShadow);
  // Brows and large, warm eyes.
  pixel(skin, 9, 10, C.hairShadow);
  pixel(skin, 10, 10, C.hairShadow);
  pixel(skin, 13, 10, C.hairShadow);
  pixel(skin, 14, 10, C.hairShadow);
  pixel(skin, 9, 11, C.eyeWhite);
  pixel(skin, 10, 11, C.eye);
  pixel(skin, 13, 11, C.eye);
  pixel(skin, 14, 11, C.eyeWhite);
  pixel(skin, 10, 12, C.eyeLight);
  pixel(skin, 13, 12, C.eyeLight);
  // Blush, nose and gentle smile.
  pixel(skin, 9, 13, C.blush);
  pixel(skin, 14, 13, C.blush);
  pixel(skin, 12, 13, C.skinShadow);
  pixel(skin, 11, 14, C.skinShadow);
  pixel(skin, 12, 14, C.skinShadow);
  pixel(skin, 10, 15, C.hairShadow);
  pixel(skin, 13, 15, C.hairShadow);

  // Hair overlay. Transparent gaps intentionally expose the face.
  fill(skin, 40, 0, 8, 8, C.hair);
  for (let i = 0; i < 8; i++) {
    pixel(skin, 40 + i, 1 + ((i * 5) % 6), i % 2 ? C.hairLight : C.hairShadow);
  }
  fill(skin, 32, 8, 8, 2, C.hair);
  fill(skin, 32, 10, 1, 6, C.hairShadow);
  fill(skin, 39, 10, 1, 6, C.hair);
  fill(skin, 48, 8, 8, 2, C.hair);
  fill(skin, 48, 10, 1, 6, C.hair);
  fill(skin, 55, 10, 1, 6, C.hairShadow);
  fill(skin, 56, 8, 8, 8, C.hair);
  fill(skin, 58, 13, 4, 3, C.hairShadow);
  pixel(skin, 59, 12, C.cyan);
  pixel(skin, 60, 12, C.cyanDark);
  // Layered fringe on the front overlay.
  fill(skin, 40, 8, 8, 1, C.hairLight);
  pixel(skin, 40, 9, C.hairShadow);
  pixel(skin, 41, 9, C.hair);
  pixel(skin, 43, 9, C.hairLight);
  pixel(skin, 46, 9, C.hair);
  pixel(skin, 47, 9, C.hairShadow);
  pixel(skin, 40, 10, C.hairShadow);
  pixel(skin, 47, 10, C.hairShadow);
}

function paintTorso() {
  fabric(20, 16, 8, 4, C.teal, C.tealLight, C.tealDark, 1);
  fabric(28, 16, 8, 4, C.tealDark, C.teal, C.tealDark, 2);
  fabric(16, 20, 4, 12, C.tealDark, C.teal, C.tealDark, 3);
  fabric(20, 20, 8, 12, C.teal, C.tealLight, C.tealDark, 4);
  fabric(28, 20, 4, 12, C.tealDark, C.teal, C.tealDark, 5);
  fabric(32, 20, 8, 12, C.teal, C.tealLight, C.tealDark, 6);
  // V-neck.
  pixel(skin, 23, 20, C.skinLight);
  pixel(skin, 24, 20, C.skinLight);
  pixel(skin, 23, 21, C.skin);
  pixel(skin, 24, 21, C.skin);
  pixel(skin, 24, 22, C.skinShadow);
  fill(skin, 20, 30, 8, 2, C.tealDark);

  // White coat outer torso, open in the middle.
  fabric(20, 32, 8, 4, C.coat, C.coatLight, C.coatShadow, 1);
  fabric(28, 32, 8, 4, C.coatShadow, C.coat, C.coatDeep, 2);
  fabric(16, 36, 4, 12, C.coat, C.coatLight, C.coatShadow, 3);
  fabric(28, 36, 4, 12, C.coat, C.coatLight, C.coatShadow, 4);
  fabric(32, 36, 8, 12, C.coat, C.coatLight, C.coatShadow, 5);
  // Clear the open center of the front overlay.
  fill(skin, 20, 36, 8, 12, [0, 0, 0, 0]);
  fill(skin, 20, 36, 3, 12, C.coat);
  fill(skin, 25, 36, 3, 12, C.coat);
  pixel(skin, 22, 36, C.coatLight);
  pixel(skin, 25, 36, C.coatLight);
  pixel(skin, 22, 37, C.coatLight);
  pixel(skin, 25, 37, C.coatLight);
  pixel(skin, 23, 38, C.coatLight);
  pixel(skin, 24, 38, C.coatLight);
  // Lapel shadows and seams.
  pixel(skin, 22, 39, C.coatShadow);
  pixel(skin, 25, 39, C.coatShadow);
  fill(skin, 20, 46, 3, 2, C.coatShadow);
  fill(skin, 25, 46, 3, 2, C.coatShadow);
  // Stethoscope and cyan badge.
  pixel(skin, 22, 38, C.stethoscope);
  pixel(skin, 22, 39, C.stethoscope);
  pixel(skin, 22, 40, C.stethoscope);
  pixel(skin, 23, 41, C.stethoscope);
  pixel(skin, 23, 42, C.metal);
  pixel(skin, 25, 39, C.stethoscope);
  pixel(skin, 26, 40, C.stethoscope);
  fill(skin, 25, 41, 2, 2, C.cyan);
  pixel(skin, 25, 42, C.cyanDark);
  // Buttons and a small dust mark.
  pixel(skin, 26, 44, C.coatDeep);
  pixel(skin, 26, 46, C.coatDeep);
  pixel(skin, 20, 43, C.dust);
  // Back seam, belt, and ponytail hanging over the coat.
  fill(skin, 35, 37, 2, 5, C.hair);
  pixel(skin, 35, 41, C.hairLight);
  pixel(skin, 36, 41, C.cyan);
  fill(skin, 35, 42, 2, 3, C.hairShadow);
  fill(skin, 32, 43, 8, 1, C.coatDeep);
  pixel(skin, 35, 44, C.coatDeep);
  pixel(skin, 36, 44, C.coatDeep);
  pixel(skin, 38, 46, C.dust);
}

const rightArm = {
  top: [44, 16, 3, 4], bottom: [47, 16, 3, 4],
  right: [40, 20, 4, 12], front: [44, 20, 3, 12],
  left: [47, 20, 4, 12], back: [51, 20, 3, 12]
};
const rightArmOuter = {
  top: [44, 32, 3, 4], bottom: [47, 32, 3, 4],
  right: [40, 36, 4, 12], front: [44, 36, 3, 12],
  left: [47, 36, 4, 12], back: [51, 36, 3, 12]
};
const leftArm = {
  top: [36, 48, 3, 4], bottom: [39, 48, 3, 4],
  right: [32, 52, 4, 12], front: [36, 52, 3, 12],
  left: [39, 52, 4, 12], back: [43, 52, 3, 12]
};
const leftArmOuter = {
  top: [52, 48, 3, 4], bottom: [55, 48, 3, 4],
  right: [48, 52, 4, 12], front: [52, 52, 3, 12],
  left: [55, 52, 4, 12], back: [59, 52, 3, 12]
};

function paintArm(base, outer, rightSide) {
  fabric(...base.top, C.coat, C.coatLight, C.coatShadow, rightSide ? 1 : 2);
  fill(skin, ...base.bottom, C.skinShadow);
  for (const key of ["right", "front", "left", "back"]) {
    const [x, y, w] = base[key];
    fabric(x, y, w, 9, C.coat, C.coatLight, C.coatShadow, rightSide ? 3 : 5);
    fill(skin, x, y + 9, w, 1, C.tealDark);
    fill(skin, x, y + 10, w, 2, C.skin);
    pixel(skin, x, y + 10, C.skinShadow);
  }
  // Puffy coat-sleeve outer layer; hands remain visible.
  fabric(...outer.top, C.coatLight, C.coatLight, C.coatShadow, rightSide ? 2 : 4);
  for (const key of ["right", "front", "left", "back"]) {
    const [x, y, w] = outer[key];
    fabric(x, y, w, 9, C.coat, C.coatLight, C.coatShadow, rightSide ? 6 : 7);
    fill(skin, x, y + 8, w, 2, C.teal);
  }
  const [fx, fy, fw] = outer.front;
  pixel(skin, fx + fw - 1, fy + 5, rightSide ? C.pen : C.dust);
  pixel(skin, fx, fy + 7, C.coatDeep);
}

const rightLeg = {
  top: [4, 16, 4, 4], bottom: [8, 16, 4, 4],
  right: [0, 20, 4, 12], front: [4, 20, 4, 12],
  left: [8, 20, 4, 12], back: [12, 20, 4, 12]
};
const rightLegOuter = {
  top: [4, 32, 4, 4], bottom: [8, 32, 4, 4],
  right: [0, 36, 4, 12], front: [4, 36, 4, 12],
  left: [8, 36, 4, 12], back: [12, 36, 4, 12]
};
const leftLeg = {
  top: [20, 48, 4, 4], bottom: [24, 48, 4, 4],
  right: [16, 52, 4, 12], front: [20, 52, 4, 12],
  left: [24, 52, 4, 12], back: [28, 52, 4, 12]
};
const leftLegOuter = {
  top: [4, 48, 4, 4], bottom: [8, 48, 4, 4],
  right: [0, 52, 4, 12], front: [4, 52, 4, 12],
  left: [8, 52, 4, 12], back: [12, 52, 4, 12]
};

function paintLeg(base, outer, leftSide) {
  fabric(...base.top, C.pants, C.pantsLight, C.pantsDark, leftSide ? 2 : 1);
  fill(skin, ...base.bottom, C.sole);
  for (const key of ["right", "front", "left", "back"]) {
    const [x, y, w] = base[key];
    fabric(x, y, w, 9, C.pants, C.pantsLight, C.pantsDark, leftSide ? 5 : 3);
    fill(skin, x, y + 9, w, 2, C.shoe);
    fill(skin, x, y + 11, w, 1, C.sole);
    pixel(skin, x + (leftSide ? w - 1 : 0), y + 9, C.shoeShadow);
    if (key === "front") pixel(skin, x + 1, y + 10, C.shoeShadow);
  }

  // Long white coat tails use the leg overlay and stop above the knee.
  fabric(...outer.top, C.coat, C.coatLight, C.coatShadow, leftSide ? 4 : 3);
  for (const key of ["right", "front", "left", "back"]) {
    const [x, y, w] = outer[key];
    fabric(x, y, w, 6, C.coat, C.coatLight, C.coatShadow, leftSide ? 8 : 7);
    pixel(skin, x + (leftSide ? 0 : w - 1), y + 5, C.coatDeep);
  }
  const [fx, fy, fw] = outer.front;
  pixel(skin, fx + (leftSide ? fw - 1 : 0), fy + 3, C.dust);
}

paintHead();
paintTorso();
paintArm(rightArm, rightArmOuter, true);
paintArm(leftArm, leftArmOuter, false);
paintLeg(rightLeg, rightLegOuter, false);
paintLeg(leftLeg, leftLegOuter, true);

function readPixel(source, x, y) {
  const index = (y * source.width + x) * 4;
  return [
    source.data[index], source.data[index + 1],
    source.data[index + 2], source.data[index + 3]
  ];
}

function composite(base, overlay) {
  return overlay[3] === 0 ? base : overlay;
}

function drawFace(target, sourceRect, overlayRect, dx, dy, scale) {
  const [sx, sy, width, height] = sourceRect;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      let value = readPixel(skin, sx + x, sy + y);
      if (overlayRect) {
        value = composite(value, readPixel(skin, overlayRect[0] + x, overlayRect[1] + y));
      }
      for (let oy = 0; oy < scale; oy++) {
        for (let ox = 0; ox < scale; ox++) {
          pixel(target, dx + x * scale + ox, dy + y * scale + oy, value);
        }
      }
    }
  }
}

function drawCharacter(target, originX, originY, back = false) {
  const scale = 10;
  const headBase = back ? [24, 8, 8, 8] : [8, 8, 8, 8];
  const headOverlay = back ? [56, 8, 8, 8] : [40, 8, 8, 8];
  const torsoBase = back ? [32, 20, 8, 12] : [20, 20, 8, 12];
  const torsoOverlay = back ? [32, 36, 8, 12] : [20, 36, 8, 12];
  const rightArmBase = back ? [51, 20, 3, 12] : [44, 20, 3, 12];
  const rightArmLayer = back ? [51, 36, 3, 12] : [44, 36, 3, 12];
  const leftArmBase = back ? [43, 52, 3, 12] : [36, 52, 3, 12];
  const leftArmLayer = back ? [59, 52, 3, 12] : [52, 52, 3, 12];
  const rightLegBase = back ? [12, 20, 4, 12] : [4, 20, 4, 12];
  const rightLegLayer = back ? [12, 36, 4, 12] : [4, 36, 4, 12];
  const leftLegBase = back ? [28, 52, 4, 12] : [20, 52, 4, 12];
  const leftLegLayer = back ? [12, 52, 4, 12] : [4, 52, 4, 12];

  drawFace(target, headBase, headOverlay, originX + 30, originY, scale);
  drawFace(target, rightArmBase, rightArmLayer, originX, originY + 80, scale);
  drawFace(target, torsoBase, torsoOverlay, originX + 30, originY + 80, scale);
  drawFace(target, leftArmBase, leftArmLayer, originX + 110, originY + 80, scale);
  drawFace(target, rightLegBase, rightLegLayer, originX + 30, originY + 200, scale);
  drawFace(target, leftLegBase, leftLegLayer, originX + 70, originY + 200, scale);
}

const preview = new PNG({ width: 430, height: 390, colorType: 6 });
fill(preview, 0, 0, preview.width, preview.height, "#111922");
fill(preview, 15, 15, 180, 350, "#1b2732");
fill(preview, 235, 15, 180, 350, "#1b2732");
fill(preview, 25, 350, 160, 4, "#344653");
fill(preview, 245, 350, 160, 4, "#344653");
drawCharacter(preview, 35, 25, false);
drawCharacter(preview, 255, 25, true);

fs.mkdirSync(path.dirname(skinPath), { recursive: true });
fs.mkdirSync(path.dirname(previewPath), { recursive: true });
fs.writeFileSync(skinPath, PNG.sync.write(skin));
fs.writeFileSync(previewPath, PNG.sync.write(preview));
console.log(skinPath);
console.log(previewPath);
