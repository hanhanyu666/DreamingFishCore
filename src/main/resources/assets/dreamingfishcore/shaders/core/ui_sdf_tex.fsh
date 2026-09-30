#version 150

#moj_import <dreamingfishcore:ui_sdf_common.glsl>

// 梦屿 UI 框架：带圆角裁剪的贴图（头像、图片）。
// Grad = (u0, v0, u1, v1)，Fill0 为着色，Style.x 为描边宽度。

uniform sampler2D Sampler0;
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

void main() {
    float aa = max(max(fwidth(vLocal.x), fwidth(vLocal.y)), 1.0e-4);
    float d = uiRoundBox(vLocal, vHalf, vRadii);
    float outer = clamp(0.5 - d / aa, 0.0, 1.0);
    vec2 uvT = (vLocal + vHalf) / max(2.0 * vHalf, vec2(1.0e-4));
    vec2 uv = mix(vGrad.xy, vGrad.zw, uvT);
    vec4 tex = texture(Sampler0, uv) * vFill0;
    vec4 texP = vec4(tex.rgb * tex.a, tex.a);
    float bw = vStyle.x;
    vec4 color;
    if (bw > 0.0) {
        vec4 borderP = vec4(vBorder.rgb * vBorder.a, vBorder.a);
        vec4 ringP = borderP + texP * (1.0 - vBorder.a);
        float inner = clamp(0.5 - (d + bw) / aa, 0.0, 1.0);
        color = mix(ringP, texP, inner) * outer;
    } else {
        color = texP * outer;
    }
    color *= uiClipCoverage(vGuiPos, vClip, vExtra.x);
    color.rgb *= ColorModulator.rgb;
    color *= ColorModulator.a;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
