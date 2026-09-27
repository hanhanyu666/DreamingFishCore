package com.hhy.dreamingfishcore.gameplay.kill_effect_system.client;

/**
 * Deterministic, texture-free singularity geometry in world axes, relative to the kill's feet.
 * Rear light, the curved black horizon, and foreground light are separate painter passes so
 * the core occludes its own disk even with shader loaders that own the world's depth target.
 */
public final class BlackHoleGeometry {
    public static final float RELEASE_PROGRESS = 0.70F;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final Point ORIGIN = new Point(0, 0, 0);
    private static final Point X = new Point(1, 0, 0);
    private static final Point Z = new Point(0, 0, 1);
    // A fixed world-space tilt keeps the disk three-dimensional as the observer moves.
    private static final Point DISK_X = new Point(0.994F, -0.110F, 0);
    private static final Point DISK_Z = new Point(0.032F, 0.289F, 0.957F);

    public enum Pass { SHADOW, REAR_LIGHT, HORIZON, FRONT_LIGHT }

    @FunctionalInterface
    public interface VertexSink {
        void vertex(float x, float y, float z, float red, float green, float blue, float alpha);
    }

    public record Point(float x, float y, float z) {
        Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        Point scale(float scale) { return new Point(x * scale, y * scale, z * scale); }
        float dot(Point p) { return x * p.x + y * p.y + z * p.z; }
    }

    /** Orthonormal camera axes; towardEye points from the singularity toward the viewer. */
    public record View(Point right, Point up, Point towardEye) { }

    public record Frame(float progress, float time, float radius, float coreRadius,
                        float presence, float coreAlpha, float collapse, float height,
                        float bodyTop, long seed, boolean reducedDetail) {
        public Point center() { return new Point(0, radius * 1.08F, 0); }
    }

    private BlackHoleGeometry() { }

    public static float radiusFor(float width, float height) {
        return clamp(Math.max(finite(width) * 1.25F, finite(height) * 0.42F), 0.40F, 3.0F);
    }

    /** Conservative horizontal extent shared by frustum culling and geometry checks. */
    public static float reachFor(float width, float height) {
        return radiusFor(width, height) * 6.2F;
    }

    public static Frame sample(float width, float height, float elapsed, int duration,
                               float bodyTop, long seed, boolean reducedDetail) {
        float progress = clamp(finite(elapsed) / Math.max(1, duration), 0, 1);
        float radius = radiusFor(width, height);
        float grow = smooth(0, 0.13F, progress);
        float collapse = smooth(0.55F, 0.735F, progress);
        float core = radius * (0.18F + 0.82F * grow) * (1 - 0.985F * collapse);
        float presence = smooth(0, 0.035F, progress) * (1 - smooth(0.90F, 1, progress));
        float coreAlpha = presence * (1 - smooth(0.725F, 0.79F, progress));
        // Normalize choreography to lifetime; packet duration must not change its phase alignment.
        return new Frame(progress, progress * 36, radius, core, presence, coreAlpha, collapse,
                clamp(finite(height), 0.05F, 32), clamp(finite(bodyTop), -40, 64), seed, reducedDetail);
    }

    public static void render(Pass pass, Frame frame, View view, VertexSink sink) {
        if (frame.presence <= 0.001F) return;
        Mesh mesh = new Mesh(pass, frame, view, sink);
        if (pass == Pass.SHADOW) {
            shadow(mesh);
        } else if (pass == Pass.HORIZON) {
            horizon(mesh);
        } else {
            accretion(mesh);
            inflow(mesh);
            lens(mesh);
            shockwaves(mesh);
        }
    }

    private static void shadow(Mesh m) {
        Frame f = m.frame;
        float extent = f.radius * (1.1F + smooth(0, 0.18F, f.progress) * 1.9F);
        float alpha = f.coreAlpha * (0.48F + f.collapse * 0.12F);
        // A continuous radial penumbra replaces the old stack of hard-edged black disks.
        m.band(ORIGIN, X, Z, 0, extent * 0.38F, 0, TAU, m.segments,
                0.005F, 0.004F, 0.016F, alpha, alpha * 0.78F);
        m.band(ORIGIN, X, Z, extent * 0.38F, extent, 0, TAU, m.segments,
                0.008F, 0.006F, 0.025F, alpha * 0.78F, 0);
    }

    private static void horizon(Mesh m) {
        Frame f = m.frame;
        if (f.coreAlpha <= 0.001F) return;
        Point center = f.center();
        // The visible hemisphere has real depth. Disjoint latitude bands avoid alpha stacking.
        int latitudes = f.reducedDetail ? 5 : 8;
        for (int latitude = 0; latitude < latitudes; latitude++) {
            float theta0 = (float) Math.PI * 0.5F * latitude / latitudes;
            float theta1 = (float) Math.PI * 0.5F * (latitude + 1) / latitudes;
            float r0 = f.coreRadius * sin(theta0);
            float r1 = f.coreRadius * sin(theta1);
            Point center0 = center.add(m.view.towardEye.scale(f.coreRadius * cos(theta0)));
            Point center1 = center.add(m.view.towardEye.scale(f.coreRadius * cos(theta1)));
            for (int i = 0; i < m.segments; i++) {
                float a = TAU * i / m.segments;
                float b = TAU * (i + 1) / m.segments;
                m.quad(point(center0, m.view.right, m.view.up, r0, a),
                        point(center0, m.view.right, m.view.up, r0, b),
                        point(center1, m.view.right, m.view.up, r1, b),
                        point(center1, m.view.right, m.view.up, r1, a),
                        0, 0, 0, f.coreAlpha, f.coreAlpha);
            }
        }
    }

    private static void accretion(Mesh m) {
        Frame f = m.frame;
        if (f.coreAlpha <= 0.001F) return;
        Point center = f.center();
        float rotation = f.time * (0.075F + 0.15F * f.collapse) + noise(f.seed, 0) * TAU;
        float r = f.coreRadius;
        // White inner disk and wide copper plasma, like the reference's luminous equatorial belt.
        for (int i = 0; i < m.segments; i++) {
            float a = TAU * i / m.segments;
            float b = TAU * (i + 1) / m.segments;
            float hot = (float) Math.pow(0.5F + 0.5F * cos(a - rotation), 3);
            float ripple = 1 + 0.025F * sin(a * 5 - f.time * 0.4F);
            float alpha = f.coreAlpha * (0.64F + hot * 0.36F);
            m.band(center, DISK_X, DISK_Z, r * 1.035F, r * 1.43F, a, b, 1,
                    1, 0.94F, 0.77F, alpha, alpha * 0.90F);
            m.band(center, DISK_X, DISK_Z, r * 1.43F, r * 2.25F, a, b, 1,
                    1, 0.65F + hot * 0.16F, 0.30F + hot * 0.18F, alpha * 0.90F, alpha * 0.48F);
            m.band(center, DISK_X, DISK_Z, r * 2.25F, r * 3.65F * ripple, a, b, 1,
                    1, 0.36F + hot * 0.13F, 0.12F, alpha * 0.48F, 0);
        }

        int strands = f.reducedDetail ? 12 : 24;
        for (int strand = 0; strand < strands; strand++) {
            float n = noise(f.seed, strand + 10);
            float base = r * (1.09F + n * 2.15F);
            float start = noise(f.seed, strand + 45) * TAU + rotation * (1.2F - n * 0.6F);
            float sweep = 0.85F + n * 2.35F;
            for (int segment = 0; segment < 12; segment++) {
                float t0 = segment / 12.0F;
                float t1 = (segment + 1) / 12.0F;
                float radius = base * (1 - t0 * 0.07F);
                float alpha = sin(t0 * (float) Math.PI) * f.coreAlpha * (0.25F + (1 - n) * 0.42F);
                m.band(center, DISK_X, DISK_Z, radius, radius + r * (0.004F + n * 0.008F),
                        start + sweep * t0, start + sweep * t1, 1,
                        1, 0.94F - n * 0.35F, 0.74F - n * 0.40F, alpha, alpha * 0.12F);
            }
        }
    }

    private static void inflow(Mesh m) {
        Frame f = m.frame;
        float alpha = f.presence * smooth(0.02F, 0.15F, f.progress)
                * (1 - smooth(0.50F, 0.70F, f.progress));
        if (alpha <= 0.001F) return;
        int streams = f.reducedDetail ? 10 : 18;
        for (int stream = 0; stream < streams; stream++) {
            float n = noise(f.seed, stream + 90);
            float phase = fract(f.time * (0.035F + n * 0.026F) + noise(f.seed, stream + 120));
            float angle = noise(f.seed, stream + 150) * TAU;
            // Space curves descend from beside the victim and tighten into the tilted disk.
            for (int segment = 0; segment < 9; segment++) {
                float t0 = phase * 0.64F + segment * 0.04F;
                float t1 = t0 + 0.04F;
                Point a = infallPoint(f, n, angle, t0);
                Point b = infallPoint(f, n, angle, t1);
                float tail = sin(segment / 9.0F * (float) Math.PI);
                float strength = alpha * tail * (0.10F + n * 0.30F);
                m.ribbon(a, b, f.radius * (0.009F + n * 0.012F),
                        1, 0.57F + n * 0.37F, 0.27F + n * 0.46F,
                        strength * 0.48F, strength);
            }
        }
    }

    private static Point infallPoint(Frame f, float n, float angle, float t) {
        float radius = f.coreRadius * 1.14F + f.radius * (1 - t) * (1 - t) * (1.1F + n * 1.2F);
        float turn = angle + t * (2.8F + n * 1.8F) + f.time * 0.045F;
        Point rim = point(f.center(), DISK_X, DISK_Z, radius, turn);
        float top = Math.max(f.radius * (1.3F + n), f.bodyTop - f.center().y);
        return rim.add(new Point(0, top * (1 - t) * (1 - t), 0));
    }

    private static void lens(Mesh m) {
        Frame f = m.frame;
        if (f.coreAlpha <= 0.001F) return;
        Point center = f.center().add(m.view.towardEye.scale(f.coreRadius * 0.025F));
        float r = f.coreRadius;
        float intensity = f.coreAlpha * (0.86F + 0.14F * f.collapse);
        // Project the fixed disk axis, so the bent upper image joins the physical equatorial disk.
        float dx = DISK_X.dot(m.view.right);
        float dy = DISK_X.dot(m.view.up);
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        Point horizontal = length < 0.05F ? m.view.right
                : m.view.right.scale(dx / length).add(m.view.up.scale(dy / length));
        Point vertical = length < 0.05F ? m.view.up
                : m.view.right.scale(-dy / length).add(m.view.up.scale(dx / length));
        if (vertical.dot(m.view.up) < 0) {
            horizontal = horizontal.scale(-1);
            vertical = vertical.scale(-1);
        }
        Point diskNormal = new Point(-0.105F, -0.951F, 0.291F);
        float edgeOn = 1 - Math.abs(diskNormal.dot(m.view.towardEye));
        for (int i = 0; i < m.segments; i++) {
            float a = TAU * i / m.segments;
            float b = TAU * (i + 1) / m.segments;
            float top = Math.max(0, sin((a + b) * 0.5F));
            float bottom = Math.max(0, -sin((a + b) * 0.5F));
            float bulge = (float) Math.pow(top, 0.60) * edgeOn;
            float brightness = intensity * (0.72F + top * 0.28F);
            // A fine photon edge surrounds the uninterrupted black center.
            m.band(center, horizontal, vertical, r * 1.002F, r * 1.027F, a, b, 1,
                    1, 0.97F, 0.84F, brightness, brightness * 0.74F);
            float inner = r * 1.027F;
            float white = r * (1.065F + bulge * 0.16F);
            float orange = r * (1.10F + bulge * 0.36F + bottom * edgeOn * 0.08F);
            float halo = r * (1.18F + bulge * 0.62F + bottom * edgeOn * 0.12F);
            // Upper arc is the magnified rear disk: white-hot inside, orange outside, a soft corona.
            m.band(center, horizontal, vertical, inner, white, a, b, 1,
                    1, 0.96F, 0.79F, brightness, brightness * 0.85F);
            m.band(center, horizontal, vertical, white, orange, a, b, 1,
                    1, 0.66F, 0.32F, brightness * 0.85F, brightness * 0.48F);
            m.band(center, horizontal, vertical, orange, halo, a, b, 1,
                    1, 0.32F, 0.10F, brightness * 0.48F, 0);
        }
    }

    private static void shockwaves(Mesh m) {
        Frame f = m.frame;
        wave(m, 0.025F, 0.30F, 1.25F, 3.45F, 0.24F, false);
        wave(m, RELEASE_PROGRESS, 1, 0.48F, 5.65F, 0.82F, true);
        wave(m, 0.775F, 1, 0.40F, 4.65F, 0.40F, true);
        float flash = smooth(0.675F, RELEASE_PROGRESS, f.progress)
                * (1 - smooth(RELEASE_PROGRESS, 0.78F, f.progress));
        if (flash <= 0.001F) return;
        Point center = f.center();
        // A brief, local equatorial flare marks the release; its light has a soft radial tail.
        float size = f.radius * (0.20F + flash * 0.44F);
        Point wide = m.view.right.scale(2.8F);
        Point narrow = m.view.up.scale(0.09F);
        m.band(center, wide, narrow, 0, size, 0, TAU, m.segments,
                1, 0.80F, 0.56F, flash * 0.85F, 0);
    }

    /** Outward travel is independent of fading, so a dying wave never contracts back inward. */
    public static float waveTravel(float progress, float start, float end) {
        float t = clamp((progress - start) / (end - start), 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private static void wave(Mesh m, float start, float end, float from, float to,
                             float strength, boolean spatialEcho) {
        Frame f = m.frame;
        if (f.progress <= start || f.progress >= end) return;
        float t = (f.progress - start) / (end - start);
        float travel = waveTravel(f.progress, start, end);
        float radius = f.radius * (from + (to - from) * travel);
        float alpha = strength * smooth(0, 0.045F, t) * (1 - t) * (1 - t);
        float width = f.radius * (0.035F + travel * 0.065F);
        Point center = new Point(0, 0.045F, 0);
        // The crest is narrow and bright; a much wider, translucent wake supplies displaced volume.
        m.band(center, X, Z, radius - width * 5, radius - width, 0, TAU, m.segments,
                0.98F, 0.48F, 0.23F, 0, alpha * 0.24F);
        m.band(center, X, Z, radius - width, radius, 0, TAU, m.segments,
                1, 0.86F, 0.62F, alpha * 0.24F, alpha);
        m.band(center, X, Z, radius, radius + width, 0, TAU, m.segments,
                1, 0.94F, 0.80F, alpha, 0);
        if (!spatialEcho) return;
        // An expanding oblate shell breaks the ground plane, with no persistent full-screen overlay.
        Point shellZ = new Point(0, 0.50F, 0.866F);
        m.band(f.center(), X, shellZ, radius * 0.86F, radius * 0.86F + width * 0.6F,
                0, TAU, m.segments, 1, 0.77F, 0.49F, alpha * 0.30F, 0);
    }

    private static final class Mesh {
        private final Pass pass;
        private final Frame frame;
        private final View view;
        private final VertexSink sink;
        private final int segments;

        private Mesh(Pass pass, Frame frame, View view, VertexSink sink) {
            this.pass = pass;
            this.frame = frame;
            this.view = view;
            this.sink = sink;
            this.segments = frame.reducedDetail ? 48 : 80;
        }

        private void band(Point center, Point xAxis, Point zAxis, float inner, float outer,
                          float start, float end, int count, float r, float g, float b,
                          float innerAlpha, float outerAlpha) {
            if (Math.max(innerAlpha, outerAlpha) <= 0.001F || outer <= inner) return;
            inner = Math.max(0, inner);
            for (int i = 0; i < count; i++) {
                float a = start + (end - start) * i / count;
                float c = start + (end - start) * (i + 1) / count;
                quad(point(center, xAxis, zAxis, inner, a), point(center, xAxis, zAxis, inner, c),
                        point(center, xAxis, zAxis, outer, c), point(center, xAxis, zAxis, outer, a),
                        r, g, b, innerAlpha, outerAlpha);
            }
        }

        private void ribbon(Point a, Point b, float width, float r, float g, float blue,
                            float startAlpha, float endAlpha) {
            Point delta = b.add(a.scale(-1));
            float dx = delta.dot(view.right);
            float dy = delta.dot(view.up);
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            Point side = length < 0.00001F ? view.right.scale(width)
                    : view.right.scale(-dy * width / length).add(view.up.scale(dx * width / length));
            quad(a.add(side), a.add(side.scale(-1)), b.add(side.scale(-0.45F)), b.add(side.scale(0.45F)),
                    r, g, blue, startAlpha, endAlpha);
        }

        private void quad(Point a, Point b, Point c, Point d, float r, float g, float blue,
                          float firstAlpha, float secondAlpha) {
            if (Math.max(firstAlpha, secondAlpha) <= 0.001F) return;
            if (pass == Pass.REAR_LIGHT || pass == Pass.FRONT_LIGHT) {
                Point middle = a.add(b).add(c).add(d).scale(0.25F).add(frame.center().scale(-1));
                boolean front = middle.dot(view.towardEye) >= 0;
                if (front != (pass == Pass.FRONT_LIGHT)) return;
            }
            vertex(a, r, g, blue, firstAlpha);
            vertex(b, r, g, blue, firstAlpha);
            vertex(c, r, g, blue, secondAlpha);
            vertex(d, r, g, blue, secondAlpha);
        }

        private void vertex(Point p, float r, float g, float b, float alpha) {
            sink.vertex(p.x, p.y, p.z, clamp(r, 0, 1), clamp(g, 0, 1), clamp(b, 0, 1), clamp(alpha, 0, 1));
        }
    }

    private static Point point(Point center, Point x, Point z, float radius, float angle) {
        return center.add(x.scale(cos(angle) * radius)).add(z.scale(sin(angle) * radius));
    }

    private static float noise(long seed, int index) {
        long value = seed + 0x9E3779B97F4A7C15L * (index + 1L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (float) ((value >>> 40) / 16_777_216.0);
    }

    private static float finite(float value) { return Float.isFinite(value) ? value : 0; }
    private static float sin(float value) { return (float) Math.sin(value); }
    private static float cos(float value) { return (float) Math.cos(value); }
    private static float fract(float value) { return value - (float) Math.floor(value); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static float smooth(float start, float end, float value) {
        float t = clamp((value - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
