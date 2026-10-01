package com.hhy.dreamingfishcore.client.ui.framework.render;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * 一层 SDF 图形的 CPU 端顶点缓冲。按"着色器 + 贴图"切分为连续段，一次上传、按段提交。
 *
 * <p>写入前先用 {@code set*} 填好本图形的公共参数，再用 {@link #vertex} 写 4 个顶点；
 * 全程不分配对象。</p>
 */
final class ShapeBuffer implements AutoCloseable {
    private static final int INITIAL_QUADS = 256;

    private ByteBuffer buffer = MemoryUtil.memAlloc(INITIAL_QUADS * 4 * SdfRenderer.STRIDE);
    private int quadCount;
    private Segment[] segments = new Segment[8];
    private int segmentCount;

    private float halfX;
    private float halfY;
    private float r0;
    private float r1;
    private float r2;
    private float r3;
    private float s0;
    private float s1;
    private float s2;
    private float s3;
    private float g0;
    private float g1;
    private float g2;
    private float g3;
    private float c0;
    private float c1;
    private float c2;
    private float c3;
    private float e0;
    private float e1;
    private float e2;
    private float e3;
    private int fill0;
    private int fill1;
    private int border;

    ShapeBuffer() {
        for (int i = 0; i < segments.length; i++) {
            segments[i] = new Segment();
        }
    }

    int quadCount() {
        return quadCount;
    }

    boolean isEmpty() {
        return quadCount == 0;
    }

    void clear() {
        quadCount = 0;
        segmentCount = 0;
    }

    /** 开始一个四边形；{@code textureId} 为 -1 表示纯色图形。 */
    void beginQuad(int textureId) {
        ensureCapacity(quadCount + 1);
        if (segmentCount == 0 || segments[segmentCount - 1].textureId != textureId) {
            if (segmentCount == segments.length) {
                Segment[] grown = new Segment[segments.length * 2];
                System.arraycopy(segments, 0, grown, 0, segments.length);
                for (int i = segments.length; i < grown.length; i++) {
                    grown[i] = new Segment();
                }
                segments = grown;
            }
            Segment segment = segments[segmentCount++];
            segment.textureId = textureId;
            segment.firstQuad = quadCount;
            segment.quadCount = 0;
        }
        segments[segmentCount - 1].quadCount++;
        quadCount++;
    }

    void setHalf(float x, float y) {
        halfX = x;
        halfY = y;
    }

    void setRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        r0 = topLeft;
        r1 = topRight;
        r2 = bottomRight;
        r3 = bottomLeft;
    }

    void setStyle(float borderWidth, float shadowSigma, float shadowSpread, float reserved) {
        s0 = borderWidth;
        s1 = shadowSigma;
        s2 = shadowSpread;
        s3 = reserved;
    }

    void setGrad(float a, float b, float c, float d) {
        g0 = a;
        g1 = b;
        g2 = c;
        g3 = d;
    }

    void setClip(float x0, float y0, float x1, float y1, float radius) {
        c0 = x0;
        c1 = y0;
        c2 = x1;
        c3 = y1;
        e0 = radius;
    }

    void setExtra(float innerShadow, float shapeType, float gradientType) {
        e1 = innerShadow;
        e2 = shapeType;
        e3 = gradientType;
    }

    void setColors(int fillStart, int fillEnd, int borderColor) {
        fill0 = fillStart;
        fill1 = fillEnd;
        border = borderColor;
    }

    /** 写一个顶点：{@code (x, y)} 为绘制坐标（经 pose 变换），{@code (localX, localY)} 为相对图形中心的坐标。 */
    void vertex(Matrix4f pose, float x, float y, float localX, float localY) {
        // memAddress 返回的是当前 position 处的地址
        long p = MemoryUtil.memAddress(buffer);
        float px = pose.m00() * x + pose.m10() * y + pose.m30();
        float py = pose.m01() * x + pose.m11() * y + pose.m31();
        float pz = pose.m02() * x + pose.m12() * y + pose.m32();
        MemoryUtil.memPutFloat(p, px);
        MemoryUtil.memPutFloat(p + 4, py);
        MemoryUtil.memPutFloat(p + 8, pz);
        MemoryUtil.memPutFloat(p + 12, localX);
        MemoryUtil.memPutFloat(p + 16, localY);
        MemoryUtil.memPutFloat(p + 20, halfX);
        MemoryUtil.memPutFloat(p + 24, halfY);
        MemoryUtil.memPutFloat(p + 28, r0);
        MemoryUtil.memPutFloat(p + 32, r1);
        MemoryUtil.memPutFloat(p + 36, r2);
        MemoryUtil.memPutFloat(p + 40, r3);
        MemoryUtil.memPutFloat(p + 44, s0);
        MemoryUtil.memPutFloat(p + 48, s1);
        MemoryUtil.memPutFloat(p + 52, s2);
        MemoryUtil.memPutFloat(p + 56, s3);
        MemoryUtil.memPutFloat(p + 60, g0);
        MemoryUtil.memPutFloat(p + 64, g1);
        MemoryUtil.memPutFloat(p + 68, g2);
        MemoryUtil.memPutFloat(p + 72, g3);
        MemoryUtil.memPutFloat(p + 76, c0);
        MemoryUtil.memPutFloat(p + 80, c1);
        MemoryUtil.memPutFloat(p + 84, c2);
        MemoryUtil.memPutFloat(p + 88, c3);
        MemoryUtil.memPutFloat(p + 92, e0);
        MemoryUtil.memPutFloat(p + 96, e1);
        MemoryUtil.memPutFloat(p + 100, e2);
        MemoryUtil.memPutFloat(p + 104, e3);
        putColor(p + 108, fill0);
        putColor(p + 112, fill1);
        putColor(p + 116, border);
        buffer.position(buffer.position() + SdfRenderer.STRIDE);
    }

    void draw() {
        if (quadCount == 0) {
            return;
        }
        buffer.flip();
        try {
            SdfRenderer.draw(buffer, quadCount, segments, segmentCount);
        } finally {
            buffer.clear();
            clear();
        }
    }

    private static void putColor(long pointer, int argb) {
        MemoryUtil.memPutByte(pointer, (byte) (argb >> 16));
        MemoryUtil.memPutByte(pointer + 1, (byte) (argb >> 8));
        MemoryUtil.memPutByte(pointer + 2, (byte) argb);
        MemoryUtil.memPutByte(pointer + 3, (byte) (argb >>> 24));
    }

    private void ensureCapacity(int quads) {
        int needed = quads * 4 * SdfRenderer.STRIDE;
        if (needed <= buffer.capacity()) {
            return;
        }
        int capacity = buffer.capacity();
        while (capacity < needed) {
            capacity *= 2;
        }
        int position = buffer.position();
        buffer = MemoryUtil.memRealloc(buffer, capacity);
        buffer.limit(capacity);
        buffer.position(position);
    }

    @Override
    public void close() {
        MemoryUtil.memFree(buffer);
    }

    static final class Segment {
        int textureId = -1;
        int firstQuad;
        int quadCount;
    }
}
