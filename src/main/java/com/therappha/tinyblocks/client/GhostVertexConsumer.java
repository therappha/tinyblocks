package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GhostVertexConsumer implements VertexConsumer {

    private static final int ALPHA = 100; // ~40% opacity

    private final VertexConsumer delegate;
    private final int alpha;

    GhostVertexConsumer(VertexConsumer delegate) {
        this(delegate, ALPHA);
    }

    /** {@code alpha} in 0..255 caps every vertex's opacity; draw into a translucent buffer for it to show. */
    public GhostVertexConsumer(VertexConsumer delegate, int alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        delegate.setColor(r, g, b, Math.min(a, alpha));
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float nx, float ny, float nz) {
        delegate.setNormal(nx, ny, nz);
        return this;
    }
}
