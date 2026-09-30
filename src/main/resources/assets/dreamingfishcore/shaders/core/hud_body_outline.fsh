#version 150

// 体征线稿：从离屏渲染的玩家模型深度中提取轮廓线与棱线，并叠加生命、感染与部位状态。
// 纹理坐标 v 向上递增：Levels.x 是脚底，Levels.y 是头顶。

uniform sampler2D FigureDepth;

uniform vec2 TexelSize;
// x/y：遮挡台阶的深度阈值区间；z/w：棱线（深度二阶差分）阈值区间。均为归一化深度。
uniform vec4 Thresholds;
uniform vec4 LineColor;
uniform vec4 SpentLineColor;
uniform vec4 FillColor;
uniform vec4 LagColor;
uniform vec4 InfectionColor;
// x：脚底 v，y：头顶 v，z：生命液面 v，w：刚失去生命的残影上沿 v
uniform vec4 Levels;
// x：感染覆盖比例，y：感染蔓延的最大深度（纹素），z：线宽加粗（0/1），w：外圈阴影透明度
uniform vec4 InfectionParams;
// 各部位的警示色，a 为强度
uniform vec4 BandHead;
uniform vec4 BandChest;
uniform vec4 BandLegs;
uniform vec4 BandFeet;
// x：受伤白闪，y：扫描线 v（小于 0 不显示），z：内部衬底透明度，w：保留
uniform vec4 Effects;

in vec2 texCoord;
out vec4 fragColor;

const float EMPTY_DEPTH = 0.99999;

float depthAt(vec2 uv) {
    return texture(FigureDepth, uv).r;
}

bool solidAt(vec2 uv) {
    return depthAt(uv) < EMPTY_DEPTH;
}

float edgeAt(vec2 uv) {
    float d = depthAt(uv);
    if (d >= EMPTY_DEPTH) {
        return 0.0;
    }
    vec2 dx = vec2(TexelSize.x, 0.0);
    vec2 dy = vec2(0.0, TexelSize.y);
    float l = depthAt(uv - dx);
    float r = depthAt(uv + dx);
    float b = depthAt(uv - dy);
    float t = depthAt(uv + dy);
    float farthest = max(max(l, r), max(b, t));
    if (farthest >= EMPTY_DEPTH) {
        return 1.0;
    }
    float occlusion = farthest - d;
    float crease = max(abs(l + r - 2.0 * d), abs(b + t - 2.0 * d));
    return max(smoothstep(Thresholds.x, Thresholds.y, occlusion),
               smoothstep(Thresholds.z, Thresholds.w, crease));
}

float lineAt(vec2 uv) {
    float line = edgeAt(uv);
    if (InfectionParams.z > 0.5 && line < 1.0) {
        vec2 dx = vec2(TexelSize.x, 0.0);
        vec2 dy = vec2(0.0, TexelSize.y);
        float around = max(max(edgeAt(uv + dx), edgeAt(uv - dx)), max(edgeAt(uv + dy), edgeAt(uv - dy)));
        line = max(line, around * 0.85);
    }
    return line;
}

// 到剪影外缘的归一化距离；只搜索到 limit（归一化）为止，更远时返回 1。
float edgeDistance(vec2 uv, float reach, float limit) {
    const int STEPS = 12;
    float stepSize = max(1.0, reach / float(STEPS));
    float maxRadius = min(reach, reach * limit);
    for (int i = 1; i <= STEPS; ++i) {
        float radius = float(i) * stepSize;
        if (radius > maxRadius) {
            break;
        }
        vec2 ox = vec2(TexelSize.x * radius, 0.0);
        vec2 oy = vec2(0.0, TexelSize.y * radius);
        vec2 od = vec2(TexelSize.x, TexelSize.y) * radius * 0.7071;
        if (!solidAt(uv + ox) || !solidAt(uv - ox) || !solidAt(uv + oy) || !solidAt(uv - oy)
                || !solidAt(uv + od) || !solidAt(uv - od)
                || !solidAt(uv + vec2(od.x, -od.y)) || !solidAt(uv + vec2(-od.x, od.y))) {
            return radius / reach;
        }
    }
    return 1.0;
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec4 over(vec4 top, vec4 bottom) {
    float alpha = top.a + bottom.a * (1.0 - top.a);
    if (alpha <= 0.0001) {
        return vec4(0.0);
    }
    vec3 rgb = (top.rgb * top.a + bottom.rgb * bottom.a * (1.0 - top.a)) / alpha;
    return vec4(rgb, alpha);
}

void main() {
    vec2 uv = texCoord;
    bool inside = solidAt(uv);
    float span = max(0.0001, Levels.y - Levels.x);
    float relative = clamp((uv.y - Levels.x) / span, 0.0, 1.0);
    bool filled = uv.y <= Levels.z;
    vec4 band = relative >= 0.75 ? BandHead
            : relative >= 0.375 ? BandChest
            : relative >= 0.125 ? BandLegs
            : BandFeet;

    vec4 color = vec4(0.0);
    if (!inside) {
        // 外圈淡阴影：白色线稿在雪地、天空等亮背景上仍然清晰。
        vec2 ox = vec2(TexelSize.x, 0.0);
        vec2 oy = vec2(0.0, TexelSize.y);
        if (solidAt(uv + ox) || solidAt(uv - ox) || solidAt(uv + oy) || solidAt(uv - oy)) {
            color = vec4(0.0, 0.0, 0.0, InfectionParams.w);
        } else if (solidAt(uv + 2.0 * ox) || solidAt(uv - 2.0 * ox)
                || solidAt(uv + 2.0 * oy) || solidAt(uv - 2.0 * oy)) {
            color = vec4(0.0, 0.0, 0.0, InfectionParams.w * 0.45);
        } else {
            // 绘制区域大部分是离人形较远的空白：线条最多向外加粗一像素，这里不可能有内容。
            discard;
        }
    } else {
        color = vec4(0.0, 0.0, 0.0, Effects.z);
        if (filled) {
            // 自下而上略微加浓，填充有体积感但不出现横纹。
            float depthShade = 0.8 + 0.2 * (1.0 - relative);
            color = over(vec4(FillColor.rgb, FillColor.a * depthShade), color);
        } else if (uv.y <= Levels.w) {
            color = over(LagColor, color);
        }
        if (InfectionParams.x > 0.001) {
            float noise = hash(floor(uv / TexelSize));
            // 判定式 distance * 0.85 + noise * 0.15 < ratio：完全感染时恒成立，不必搜索；
            // 部分感染时只需搜索到对应半径。
            float limit = (InfectionParams.x - noise * 0.15) / 0.85;
            bool infected = InfectionParams.x >= 0.999
                    || limit > 0.0 && edgeDistance(uv, InfectionParams.y, limit) < limit;
            if (infected) {
                // 以模型像素为单位的斑驳腐化色块，像皮肤上蔓延的污染。
                float modelPixel = max(1.0, InfectionParams.y / 4.0);
                float mottle = hash(floor(uv / TexelSize / modelPixel) + vec2(17.0, 31.0));
                vec4 infection = vec4(InfectionColor.rgb, InfectionColor.a * (0.35 + 0.65 * mottle));
                if (!filled) {
                    infection.a *= 0.6;
                }
                color = over(infection, color);
            }
        }
        if (band.a > 0.0) {
            color = over(vec4(band.rgb, band.a * 0.28), color);
        }
        if (Levels.z > Levels.x + TexelSize.y && Levels.z < Levels.y
                && abs(uv.y - Levels.z) <= TexelSize.y) {
            color = over(vec4(mix(LineColor.rgb, vec3(1.0), 0.35), 0.55 * LineColor.a), color);
        }
        if (Effects.y >= 0.0) {
            float scan = abs(uv.y - Effects.y) / TexelSize.y;
            if (scan < 1.5) {
                color = over(vec4(1.0, 1.0, 1.0, 0.45), color);
            } else if (uv.y > Effects.y && scan < 6.0) {
                color = over(vec4(1.0, 1.0, 1.0, 0.12 * (1.0 - scan / 6.0)), color);
            }
        }
    }

    float line = lineAt(uv);
    if (line > 0.0) {
        vec4 lineColor = filled ? LineColor : SpentLineColor;
        lineColor.rgb = mix(lineColor.rgb, band.rgb, band.a);
        lineColor.rgb = mix(lineColor.rgb, vec3(1.0), Effects.x * 0.6);
        lineColor.a *= line;
        color = over(lineColor, color);
    }
    fragColor = color;
}
