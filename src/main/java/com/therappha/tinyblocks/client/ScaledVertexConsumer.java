package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * #TODO remove before release (or fold into a permanent comment) once verified live —
 * water-rendering investigation.
 *
 * LiquidBlockRenderer#tesselate (unlike ModelBlockRenderer#renderModel, what renderSingleBlock
 * uses) takes no PoseStack at all — it writes straight into the VertexConsumer via the
 * position-only addVertex(x, y, z)/setNormal(x, y, z) overloads. Those overloads carry NO
 * transform of their own; a buffer obtained via bufferSource.getBuffer(renderType) only ends up
 * positioned correctly on screen when something explicitly pushes each vertex through the
 * current PoseStack.Pose (this is what ModelBlockRenderer does internally for renderSingleBlock —
 * tesselate has no equivalent). Scaling the raw coordinates alone (an earlier attempt) still left
 * out that whole camera/BE-origin transform, which is why the fluid rendered "floating" near the
 * camera instead of at the subgrid's actual position — visually like a layer on top of
 * everything, only visible from odd angles, since the raw [0,1]-ish coordinates were being
 * interpreted as already-final render-space positions instead of BE-local ones.
 *
 * Fix: capture the SubgridRenderer's own base PoseStack.Pose (the BE's un-modified render origin)
 * once, and route every position/normal this receives through VertexConsumer's own
 * addVertex(PoseStack.Pose, x, y, z) / setNormal(PoseStack.Pose, x, y, z) default methods, which
 * do exactly this transform-then-forward (confirmed via decompiling 1.21.1's VertexConsumer:
 * default addVertex(Pose, x,y,z) -> addVertex(pose.pose(), x,y,z) -> matrix.transformPosition(...)
 * -> addVertex(transformed x,y,z) on the real buffer). tesselate's raw coordinates are already
 * piece.anchor + a [0,1] fractional offset (see class history/git blame if this comment survives
 * past #TODO removal), so scale is still applied first, same as before — only the missing pose
 * transform is new here.
 */
final class ScaledVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float scale;
    private final PoseStack.Pose pose;

    ScaledVertexConsumer(VertexConsumer delegate, float scale, PoseStack.Pose pose) {
        this.delegate = delegate;
        this.scale = scale;
        this.pose = pose;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        return delegate.addVertex(pose, x * scale, y * scale, z * scale);
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        return delegate.setColor(red, green, blue, alpha);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return delegate.setUv(u, v);
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return delegate.setUv1(u, v);
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return delegate.setUv2(u, v);
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return delegate.setNormal(pose, x, y, z);
    }
}
