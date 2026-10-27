package com.therappha.tinyblocks.compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * How a gear connector (ShaftConnectorPiece, even grids) is drawn, slice by slice (PieceTransforms#slices): slice 0
 * is the gear — Create's large cogwheel without its shaft, nothing sticking into the grid — and slice 1 a full-size
 * shaft end from the face to the gear, meeting the shaft outside. Both turn with the connector's phantom; the shaft
 * end at the outside shaft's angle, so their edges line up.
 */
public final class GearConnectorDrawing {

    private GearConnectorDrawing() {}

    private static PartialModel modelOf(int slice) {
        return slice == 0 ? AllPartialModels.SHAFTLESS_LARGE_COGWHEEL : AllPartialModels.SHAFT;
    }

    /**
     * The angle offset of the shaft end: that of a shaft at {@code outside}, the real position just past the face,
     * so it turns in step with the shaft there (Create offsets a shaft's angle by its position).
     */
    private static float shaftEndOffset(KineticBlockEntity phantom, BlockPos outside) {
        Direction.Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(phantom);
        return KineticBlockEntityVisual.rotationOffset(KineticBlockEntityRenderer.shaft(axis), axis, outside)
                + phantom.getRotationAngleOffset(axis);
    }

    /** The Flywheel visual of {@code slice}, in the slice's embedding; {@code outside}: the real position past the face. */
    public static BlockEntityVisual<?> visual(VisualizationContext embedding, KineticBlockEntity phantom, int slice,
                                              float partialTick, BlockPos outside) {
        if (slice == 0) return new SingleAxisRotatingVisual<>(embedding, phantom, partialTick, Models.partial(modelOf(0)));
        return new ShaftEnd(embedding, phantom, partialTick, shaftEndOffset(phantom, outside));
    }

    /** The shaft end's visual: Create's rotating shaft, held at the outside shaft's angle offset. */
    private static final class ShaftEnd extends SingleAxisRotatingVisual<KineticBlockEntity> {
        private final float offset;

        ShaftEnd(VisualizationContext embedding, KineticBlockEntity phantom, float partialTick, float offset) {
            super(embedding, phantom, partialTick, Models.partial(AllPartialModels.SHAFT));
            this.offset = offset;
            keepOffset();
        }

        private void keepOffset() {
            rotatingModel.setRotationOffset(offset).setChanged();
        }

        @Override
        public void update(float partialTick) {
            super.update(partialTick); // resets the offset from the phantom's own position
            keepOffset();
        }
    }

    /** Draws {@code slice} without Flywheel, into the slice's pose; {@code outside}: the real position past the face. */
    public static void draw(KineticBlockEntity phantom, int slice, PoseStack ms, MultiBufferSource buffers, int light,
                            BlockPos outside) {
        Direction.Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(phantom);
        Direction facing = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        SuperByteBuffer buffer = CachedBuffers.partialFacingVertical(modelOf(slice), phantom.getBlockState(), facing);
        if (slice == 0) {
            KineticBlockEntityRenderer.renderRotatingBuffer(phantom, buffer, ms, buffers.getBuffer(RenderType.solid()), light);
            return;
        }
        float time = AnimationTickHolder.getRenderTime(phantom.getLevel());
        float angle = ((time * phantom.getSpeed() * 3f / 10 + shaftEndOffset(phantom, outside)) % 360) / 180 * (float) Math.PI;
        KineticBlockEntityRenderer.kineticRotationTransform(buffer, phantom, axis, angle, light)
                .renderInto(ms, buffers.getBuffer(RenderType.solid()));
    }
}
