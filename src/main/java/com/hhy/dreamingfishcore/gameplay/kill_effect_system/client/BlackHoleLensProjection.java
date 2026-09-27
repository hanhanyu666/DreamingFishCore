package com.hhy.dreamingfishcore.gameplay.kill_effect_system.client;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Camera-relative projection shared by the refraction pass and its edge/near-plane checks. */
public final class BlackHoleLensProjection {
    public record Lens(float x, float y, float radius, float nearDepth,
                       float coreScale, float progress, float strength) { }

    private BlackHoleLensProjection() { }

    public static Lens project(Matrix4f view, Matrix4f projection, float x, float y, float z,
                               BlackHoleGeometry.Frame frame, float aspect, float effectScale) {
        Vector4f eye = view.transform(new Vector4f(x, y, z, 1));
        if (!eye.isFinite() || -eye.z <= frame.radius() * 1.12F || aspect <= 0) return null;
        Vector4f clip = projection.transform(new Vector4f(eye));
        if (!clip.isFinite() || clip.w <= 0.001F) return null;
        float cx = clip.x / clip.w * 0.5F + 0.5F;
        float cy = clip.y / clip.w * 0.5F + 0.5F;
        Vector4f edge = projection.transform(new Vector4f(eye.x, eye.y + frame.radius(), eye.z, 1));
        float radius = Math.abs(edge.y / edge.w - clip.y / clip.w) * 0.5F;
        float reach = radius * 6.2F;
        if (!Float.isFinite(radius) || radius < 0.001F
                || cx + reach / aspect < 0 || cx - reach / aspect > 1
                || cy + reach < 0 || cy - reach > 1) return null;
        Vector4f near = projection.transform(new Vector4f(eye.x, eye.y, eye.z + frame.radius(), 1));
        return new Lens(cx, cy, radius, Math.max(0, Math.min(1, near.z / near.w * 0.5F + 0.5F)),
                frame.coreRadius() / frame.radius(), frame.progress(),
                frame.presence() * Math.max(0, Math.min(1, effectScale)));
    }
}
