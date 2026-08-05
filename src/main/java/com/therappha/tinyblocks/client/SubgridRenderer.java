package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class SubgridRenderer implements BlockEntityRenderer<SubgridBlockEntity> {

    public SubgridRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SubgridBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        float cell = 1f / be.gridSize;
        BlockPos pos = be.getBlockPos();

        int crackStage = MiningTracker.getStage(pos);
        int[] crackCell = MiningTracker.getCell();

        PlacedPiece crackedPiece = null;
        if (crackStage >= 0 && crackCell != null) {
            crackedPiece = be.getPieceAt(crackCell[0], crackCell[1], crackCell[2]);
        }

        SubgridFluidView fluidView = null;
        // #TODO remove before release — water-rendering investigation. Captured once, BEFORE any
        // per-piece pushPose/translate below — this is the BE's own unmodified render origin
        // (camera-relative, exactly what renderSingleBlock's pipeline also builds on top of via
        // its own translate+scale). See ScaledVertexConsumer's doc comment for why renderLiquid
        // needs this passed through explicitly instead of just relying on the ambient poseStack.
        PoseStack.Pose basePose = poseStack.last();

        for (PlacedPiece piece : be.getPieces()) {
            BlockState state = piece.definition.renderState(piece);
            if (state == null) continue;

            // #TODO remove before release — water-rendering investigation. A fluid's BlockState
            // has RenderShape.INVISIBLE (same as any real water/lava), so renderSingleBlock below
            // is a deliberate no-op for it — fluids render through a completely separate path
            // (see SubgridFluidView's doc comment) that needs real neighbor-height sampling, not
            // just this one BlockState. renderLiquid takes no PoseStack (see ScaledVertexConsumer's
            // doc comment) — the scale has to be applied by wrapping the buffer instead.
            FluidState fluidState = state.getFluidState();
            if (!fluidState.isEmpty()) {
                if (fluidView == null) fluidView = new SubgridFluidView(be, Minecraft.getInstance().level);
                RenderType fluidRenderType = ItemBlockRenderTypes.getRenderLayer(fluidState);
                var fluidBuffer = new ScaledVertexConsumer(bufferSource.getBuffer(fluidRenderType), cell, basePose);
                blockRenderer.renderLiquid(piece.anchor, fluidView, fluidBuffer, state, fluidState);
            }

            poseStack.pushPose();
            poseStack.translate(piece.anchor.getX() * cell, piece.anchor.getY() * cell, piece.anchor.getZ() * cell);
            poseStack.scale(cell, cell, cell);

            blockRenderer.renderSingleBlock(state, poseStack, bufferSource, packedLight, packedOverlay);

            if (piece == crackedPiece) {
                ResourceLocation crackTex = ResourceLocation.withDefaultNamespace(
                    "textures/block/destroy_stage_" + crackStage + ".png");
                RenderType crumbleType = RenderType.crumbling(crackTex);
                MultiBufferSource crumbleSource = t -> bufferSource.getBuffer(crumbleType);
                blockRenderer.renderSingleBlock(state, poseStack, crumbleSource, packedLight, packedOverlay);
            }

            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(SubgridBlockEntity be) {
        return false;
    }
}
