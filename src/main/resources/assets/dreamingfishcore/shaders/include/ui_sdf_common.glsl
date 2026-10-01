// 梦屿 UI 框架：SDF 公共函数。

const float UI_PI = 3.14159265358979;

// 四角独立圆角矩形的有符号距离。r = (左上, 右上, 右下, 左下)，y 轴向下。
float uiRoundBox(vec2 p, vec2 b, vec4 r) {
    float rad = p.x > 0.0 ? (p.y > 0.0 ? r.z : r.y) : (p.y > 0.0 ? r.w : r.x);
    rad = min(rad, min(b.x, b.y));
    vec2 q = abs(p) - b + rad;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rad;
}

// 圆弧（圆角端点）。r = 半径，th = 半线宽，a0 = 起始角，sweep = 扫过角度（弧度，顺时针）。
float uiArc(vec2 p, float r, float th, float a0, float sweep) {
    if (sweep >= 2.0 * UI_PI - 0.0001) {
        return abs(length(p) - r) - th;
    }
    float ang = atan(p.y, p.x) - a0;
    ang = mod(ang, 2.0 * UI_PI);
    if (ang <= sweep) {
        return abs(length(p) - r) - th;
    }
    vec2 e0 = r * vec2(cos(a0), sin(a0));
    vec2 e1 = r * vec2(cos(a0 + sweep), sin(a0 + sweep));
    return min(length(p - e0), length(p - e1)) - th;
}

float uiGaussian(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma)) / (sqrt(2.0 * UI_PI) * sigma);
}

vec2 uiErf(vec2 x) {
    vec2 s = sign(x);
    vec2 a = abs(x);
    x = 1.0 + (0.278393 + (0.230389 + 0.000972 * a + 0.078108 * a * a) * a) * a;
    x *= x;
    return s - s / (x * x);
}

float uiShadowX(float x, float y, float sigma, float corner, vec2 halfSize) {
    float delta = min(halfSize.y - corner - abs(y), 0.0);
    float curved = halfSize.x - corner + sqrt(max(0.0, corner * corner - delta * delta));
    vec2 integral = 0.5 + 0.5 * uiErf((x + vec2(-curved, curved)) * (sqrt(0.5) / sigma));
    return integral.y - integral.x;
}

// 圆角矩形的高斯模糊阴影遮罩（Evan Wallace 近似）。point 以矩形中心为原点。
float uiRoundBoxShadow(vec2 halfSize, vec2 point, float sigma, float corner) {
    float low = point.y - halfSize.y;
    float high = point.y + halfSize.y;
    float start = clamp(-3.0 * sigma, low, high);
    float end = clamp(3.0 * sigma, low, high);
    float stepSize = (end - start) / 4.0;
    float y = start + stepSize * 0.5;
    float value = 0.0;
    for (int i = 0; i < 4; i++) {
        value += uiShadowX(point.x, point.y - y, sigma, corner, halfSize) * uiGaussian(y, sigma) * stepSize;
        y += stepSize;
    }
    return value;
}

// 裁剪覆盖率：clip = (x0, y0, x1, y1) GUI 坐标，radius 为裁剪区圆角。
float uiClipCoverage(vec2 guiPos, vec4 clip, float radius) {
    vec2 center = (clip.xy + clip.zw) * 0.5;
    vec2 extent = max((clip.zw - clip.xy) * 0.5, vec2(0.0));
    float d = uiRoundBox(guiPos - center, extent, vec4(radius));
    float aa = max(max(fwidth(guiPos.x), fwidth(guiPos.y)), 1.0e-4);
    return clamp(0.5 - d / aa, 0.0, 1.0);
}

// 屏幕空间抖动，消除深色渐变的色带。
float uiDither(vec2 fragCoord) {
    return fract(sin(dot(fragCoord, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
}
