#version 150

// 梦屿 UI 框架：SDF 图形顶点着色器。每个图形一个四边形，形状在片元中计算。

in vec3 Position;
in vec2 Local;
in vec2 Half;
in vec4 Radii;
in vec4 Style;
in vec4 Grad;
in vec4 Clip;
in vec4 Extra;
in vec4 Fill0;
in vec4 Fill1;
in vec4 Border;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vLocal;
out vec2 vHalf;
out vec4 vRadii;
out vec4 vStyle;
out vec4 vGrad;
out vec4 vClip;
out vec4 vExtra;
out vec4 vFill0;
out vec4 vFill1;
out vec4 vBorder;
out vec2 vGuiPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vLocal = Local;
    vHalf = Half;
    vRadii = Radii;
    vStyle = Style;
    vGrad = Grad;
    vClip = Clip;
    vExtra = Extra;
    vFill0 = Fill0;
    vFill1 = Fill1;
    vBorder = Border;
    vGuiPos = Position.xy;
}
