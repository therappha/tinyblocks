package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

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

        for (PlacedPiece piece : be.getPieces()) {
            BlockState state = piece.definition.renderState(piece);
            if (state == null) continue;

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
