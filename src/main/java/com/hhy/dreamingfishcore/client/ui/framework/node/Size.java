package com.hhy.dreamingfishcore.client.ui.framework.node;

/** 可复用的尺寸结果，避免测量过程分配对象。 */
public final class Size {
    public float width;
    public float height;

    public Size set(float width, float height) {
        this.width = width;
        this.height = height;
        return this;
    }
}
