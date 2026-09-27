#version 150

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
uniform vec2 Viewport;
uniform int LensCount;
// center UV, base radius in screen-height units, strength
uniform vec4 Hole0;
uniform vec4 Hole1;
uniform vec4 Hole2;
uniform vec4 Hole3;
// core/base radius, lifetime progress, nearest view-space sphere depth (0..1), reserved
uniform vec4 Shape0;
uniform vec4 Shape1;
uniform vec4 Shape2;
uniform vec4 Shape3;

in vec2 texCoord;
out vec4 fragColor;

float wave(float r, float progress, float start, float end, float from, float to) {
    float t = clamp((progress - start) / (end - start), 0.0, 1.0);
    float travel = 1.0 - pow(1.0 - t, 3.0);
    float radius = mix(from, to, travel);
    float width = 0.09 + travel * 0.15;
    float crest = (r - radius) / width;
    return crest * exp(-crest * crest) * smoothstep(0.0, 0.045, t) * pow(1.0 - t, 2.0);
}

void main() {
    vec4 holes[4] = vec4[4](Hole0, Hole1, Hole2, Hole3);
    vec4 shapes[4] = vec4[4](Shape0, Shape1, Shape2, Shape3);
    vec2 aspect = vec2(Viewport.x / Viewport.y, 1.0);
    vec2 displacement = vec2(0.0);
    float strongest = 0.0;
    float selectedDepth = 1.0;
    float fringe = 0.0;
    float depth = texture(DepthSampler, texCoord).r;
    for (int i = 0; i < 4; ++i) {
        if (i >= LensCount) break;
        vec4 hole = holes[i];
        vec4 shape = shapes[i];
        if (hole.z <= 0.0) continue;
        vec2 delta = (texCoord - hole.xy) * aspect / hole.z;
        float r = length(delta);
        if (r > 6.2 || r < 0.001) continue;
        // Nearby walls, foreground entities and terrain remain in front of the gravity field.
        float visible = smoothstep(shape.z - 0.000015, shape.z + 0.000015, depth);
        float horizon = max(shape.x, 0.015);
        float rr = r / horizon;
        float envelope = (1.0 - smoothstep(1.3, 3.3, rr)) * smoothstep(0.80, 1.04, rr);
        float alive = 1.0 - smoothstep(0.70, 0.79, shape.y);
        // Radial magnification folds the background into an Einstein-ring-like annulus.
        float bend = 0.86 * horizon * envelope / max(rr, 0.92) * alive;
        float twist = 0.16 * horizon * envelope * alive;
        float pulse = 0.14 * wave(r, shape.y, 0.025, 0.30, 1.25, 3.45)
                    + 0.36 * wave(r, shape.y, 0.70, 1.0, 0.48, 5.65)
                    + 0.18 * wave(r, shape.y, 0.775, 1.0, 0.40, 4.65);
        vec2 radial = delta / r;
        vec2 shift = (-radial * (bend + pulse) + vec2(-radial.y, radial.x) * twist)
                   * hole.z / aspect * hole.w * visible;
        float weight = length(shift * aspect);
        // Select a dominant lens where fields overlap, keeping distortion bounded during kill streaks.
        if (weight > strongest) {
            strongest = weight;
            displacement = shift;
            fringe = envelope * alive * 0.009 * hole.w;
            selectedDepth = shape.z;
        }
    }
    vec2 margin = 0.5 / Viewport;
    vec2 uv = clamp(texCoord + displacement, margin, 1.0 - margin);
    float edgeFade = smoothstep(0.0, 0.025, min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y)));
    float sourceVisible = smoothstep(selectedDepth - 0.000015, selectedDepth + 0.000015,
                                     texture(DepthSampler, uv).r);
    displacement *= edgeFade * sourceVisible;
    uv = clamp(texCoord + displacement, margin, 1.0 - margin);
    vec3 color = texture(SceneSampler, uv).rgb;
    color.r = texture(SceneSampler, clamp(uv + displacement * fringe, margin, 1.0 - margin)).r;
    color.b = texture(SceneSampler, clamp(uv - displacement * fringe, margin, 1.0 - margin)).b;
    fragColor = vec4(color, texture(SceneSampler, texCoord).a);
}
