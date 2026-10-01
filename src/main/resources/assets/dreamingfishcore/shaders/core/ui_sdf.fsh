#version 150

#moj_import <dreamingfishcore:ui_sdf_common.glsl>

// 梦屿 UI 框架：SDF 图形片元着色器。
// Style = (描边宽度, 阴影 sigma, 阴影扩展, 保留)
// Grad  = 渐变参数；阴影时 xy 为阴影偏移
// Extra = (裁剪圆角, 内阴影标记, 图形类型 0 矩形 1 圆弧, 渐变类型 0 纯色 1 线性 2 径向 3 锥形)

uniform vec4 ColorModulator;

in vec2 vLocal;
in vec2 vHalf;
in vec4 vRadii;
in vec4 vStyle;
in vec4 vGrad;
in vec4 vClip;
in vec4 vExtra;
in vec4 vFill0;
in vec4 vFill1;
in vec4 vBorder;
in vec2 vGuiPos;

out vec4 fragColor;

vec4 fillColor(vec2 p) {
    float mode = vExtra.w;
    if (mode < 0.5) {
        return vFill0;
    }
    float t;
    if (mode < 1.5) {
        vec2 dir = vGrad.zw - vGrad.xy;
        t = dot(p - vGrad.xy, dir) / max(dot(dir, dir), 1.0e-6);
    } else if (mode < 2.5) {
        t = length(p - vGrad.xy) / max(vGrad.z, 1.0e-4);
    } else {
        // 锥形：vGrad = (圆心 x, 圆心 y, 起始角, 跨度)，跨度为 0 时按整圈
        float ang = atan(p.y - vGrad.y, p.x - vGrad.x) - vGrad.z;
        float span = vGrad.w > 0.0 ? vGrad.w : 2.0 * UI_PI;
        t = mod(ang, 2.0 * UI_PI) / span;
    }
    return mix(vFill0, vFill1, clamp(t, 0.0, 1.0));
}

void main() {
    float aa = max(max(fwidth(vLocal.x), fwidth(vLocal.y)), 1.0e-4);
    vec4 color;

    if (vStyle.y > 0.0) {
        // 阴影：外阴影会挖空图形本身，避免压暗半透明表面；内阴影只出现在图形内部。
        vec2 offset = vGrad.xy;
        float spread = vStyle.z;
        float corner = max(max(vRadii.x, vRadii.y), max(vRadii.z, vRadii.w));
        float shape = clamp(0.5 - uiRoundBox(vLocal, vHalf, vRadii) / aa, 0.0, 1.0);
        vec2 shadowHalf = max(vHalf + vec2(vExtra.y > 0.5 ? -spread : spread), vec2(0.5));
        float shadowCorner = max(corner + (vExtra.y > 0.5 ? -spread : spread), 0.0);
        float mask = uiRoundBoxShadow(shadowHalf, vLocal - offset, vStyle.y, min(shadowCorner, min(shadowHalf.x, shadowHalf.y)));
        float alpha = vExtra.y > 0.5 ? (1.0 - mask) * shape : mask * (1.0 - shape);
        color = vec4(vFill0.rgb * vFill0.a, vFill0.a) * alpha;
    } else {
        float d;
        if (vExtra.z > 0.5) {
            d = uiArc(vLocal, vRadii.x, vRadii.y, vRadii.z, vRadii.w);
        } else {
            d = uiRoundBox(vLocal, vHalf, vRadii);
        }
        float outer = clamp(0.5 - d / aa, 0.0, 1.0);
        vec4 fill = fillColor(vLocal);
        vec4 fillP = vec4(fill.rgb * fill.a, fill.a);
        float bw = vStyle.x;
        if (bw > 0.0) {
            vec4 borderP = vec4(vBorder.rgb * vBorder.a, vBorder.a);
            vec4 ringP = borderP + fillP * (1.0 - vBorder.a);
            float inner = clamp(0.5 - (d + bw) / aa, 0.0, 1.0);
            color = mix(ringP, fillP, inner) * outer;
        } else {
            color = fillP * outer;
        }
        if (vExtra.w > 0.5) {
            color.rgb += uiDither(gl_FragCoord.xy) / 255.0 * color.a;
        }
    }

    color *= uiClipCoverage(vGuiPos, vClip, vExtra.x);
    color.rgb *= ColorModulator.rgb;
    color *= ColorModulator.a;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
