package com.therappha.tinyblocks.compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;

/**
 * How a gear connector (ShaftConnectorPiece, even grids) is drawn, slice by slice (PieceTransforms#slices): slice 0
 * is the gear — Create's large cogwheel without its shaft, nothing sticking into the grid — and slice 1 a full-size
 * shaft end on the face, meeting the shaft outside. Both turn with the connector's phantom.
 */
public final class GearConnectorDrawing {

    private GearConnectorDrawing() {}

    private static PartialModel modelOf(int slice) {
        return slice == 0 ? AllPartialModels.SHAFTLESS_LARGE_COGWHEEL : AllPartialModels.SHAFT;
    }

    /** The Flywheel visual of {@code slice}, in the slice's embedding. */
    public static BlockEntityVisual<?> visual(VisualizationContext embedding, KineticBlockEntity phantom, int slice, float partialTick) {
        return new SingleAxisRotatingVisual<>(embedding, phantom, partialTick, Models.partial(modelOf(slice)));
    }

    /** Draws {@code slice} without Flywheel, into the slice's pose. */
    public static void draw(KineticBlockEntity phantom, int slice, PoseStack ms, MultiBufferSource buffers, int light) {
        Direction facing = Direction.fromAxisAndDirection(KineticBlockEntityRenderer.getRotationAxisOf(phantom),
                Direction.AxisDirection.POSITIVE);
        KineticBlockEntityRenderer.renderRotatingBuffer(phantom,
                CachedBuffers.partialFacingVertical(modelOf(slice), phantom.getBlockState(), facing),
                ms, buffers.getBuffer(RenderType.solid()), light);
    }
}
