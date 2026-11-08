package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.therappha.tinyblocks.client.flywheel.FlywheelCompat;
import com.therappha.tinyblocks.client.flywheel.FlywheelLoaded;
import com.therappha.tinyblocks.compat.create.client.GearConnectorDrawing;
import com.therappha.tinyblocks.subgrid.GridSight;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubLevels;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.Minecraft;
import java.util.List;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

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
        // Captured once, before any
        // per-piece pushPose/translate below — this is the BE's own unmodified render origin
        // (camera-relative, exactly what renderSingleBlock's pipeline also builds on top of via
        // its own translate+scale). See ScaledVertexConsumer's doc comment for why renderLiquid
        // needs this passed through explicitly instead of just relying on the ambient poseStack.
        PoseStack.Pose basePose = poseStack.last();

        // Static pieces come from a cached, culled mesh (StaticMesh); only the rest go through the loop below.
        StaticMesh mesh = StaticMesh.of(be, blockRenderer);
        // In the game's own level they're in the chunk (SubgridChunkMesh), once a compile has taken them. A schematic's
        // preview or a contraption draws the grid from a level of its own, where no chunk will.
        boolean ownLevel = be.getLevel() == Minecraft.getInstance().level;
        if (ownLevel) mesh.requestChunk(be);
        if (!ownLevel || !mesh.inChunk) mesh.draw(poseStack, bufferSource, packedLight, packedOverlay);
        if (crackedPiece != null && mesh.baked.contains(crackedPiece)) {
            BlockState state = crackedPiece.definition.renderState(crackedPiece);
            MultiBufferSource crumbleSource = crumbling(bufferSource, crackStage);
            for (PieceTransforms.Slice slice : PieceTransforms.slices(crackedPiece, be.getGridSize())) {
                pushSlice(poseStack, slice);
                blockRenderer.renderSingleBlock(state, poseStack, crumbleSource, packedLight, packedOverlay);
                poseStack.popPose();
            }
        }

        // Past vanilla's distance for a block entity nothing else is drawn here: this renderer only gets that far to
        // notice a changed grid, for the chunk's sake.
        if (ownLevel && !GridSight.drawnPerFrame(seenDistance(be))) return;

        for (PlacedPiece piece : mesh.dynamic) {
            BlockState state = piece.definition.renderState(piece);
            if (state == null) continue;
            boolean staticDrawn = mesh.baked.contains(piece);

            // A fluid's BlockState
            // has RenderShape.INVISIBLE (same as any real water/lava), so renderSingleBlock below
            // is a deliberate no-op for it — fluids render through a completely separate path
            // (see SubgridFluidView's doc comment) that needs real neighbor-height sampling, not
            // just this one BlockState. renderLiquid takes no PoseStack (see ScaledVertexConsumer's
            // doc comment) — the scale has to be applied by wrapping the buffer instead.
            FluidState fluidState = state.getFluidState();
            if (!fluidState.isEmpty()) {
                if (fluidView == null) fluidView = viewOf(be);
                RenderType fluidRenderType = ItemBlockRenderTypes.getRenderLayer(fluidState);
                var fluidBuffer = new ScaledVertexConsumer(bufferSource.getBuffer(fluidRenderType), cell, basePose);
                blockRenderer.renderLiquid(piece.anchor, fluidView, fluidBuffer, state, fluidState);
            }

            if (piece.runtimeBlockEntity == null && piece.definition instanceof VanillaBlockPiece && be.getLevel() != null) {
                VanillaBlockPiece.blockEntityFor(piece, be, be.getLevel());
            }
            var modelData = staticDrawn ? ModelData.EMPTY : gatheredModelData(be, piece, state, blockRenderer);
            // One slice for an ordinary piece; a shaft connector draws its funnel slice by slice (PieceTransforms).
            List<PieceTransforms.Slice> slices = PieceTransforms.slices(piece, be.getGridSize(), PieceTransforms.acrossOf(be, piece));
            if (PieceTransforms.isGearConnector(be, piece)) {
                if (piece.runtimeBlockEntity instanceof KineticBlockEntity gear
                        && !(FlywheelLoaded.isLoaded()
                                && FlywheelCompat.drawnByVisual(be.getLevel(), gear))) {
                    for (int i = 0; i < slices.size(); i++) {
                        pushSlice(poseStack, slices.get(i));
                        GearConnectorDrawing.draw(gear, i, poseStack, bufferSource,
                                packedLight, be.getBlockPos().relative(piece.facing));
                        poseStack.popPose();
                    }
                }
                continue;
            }
            for (PieceTransforms.Slice slice : slices) {
                pushSlice(poseStack, slice);

                if (!staticDrawn) blockRenderer.renderSingleBlock(state, poseStack, bufferSource, packedLight, packedOverlay, modelData, null);

                // Some vanilla blocks (a piston's moving-block animation, a chest lid, an enchanting
                // table's book) carry ALL of their real visual on a BlockEntityRenderer rather than a
                // static baked model — renderSingleBlock above already no-ops for these
                // (RenderShape.INVISIBLE, or just has nothing interesting to show statically). A piston
                // gets its runtimeBlockEntity from the dedicated animation payload (see syncBlockEntity/
                // applyClientAnimation) for smooth per-tick progress; for everything else, lazily build
                // the SAME phantom BlockEntity blockEntityFor already builds server-side, straight from
                // this piece's synced extraData "be" NBT — no piston-specific plumbing needed, exactly
                // how vanilla's own BlockEntityRenderDispatcher works generically off whatever
                // BlockEntity happens to be in the chunk. Cheap after the first call (blockEntityFor
                // itself caches onto piece.runtimeBlockEntity).
                if (piece.runtimeBlockEntity instanceof BlockEntity blockEntity
                        && !(FlywheelLoaded.isLoaded()
                                && FlywheelCompat.drawnByVisual(be.getLevel(), blockEntity))) {
                    BlockEntityRenderer<BlockEntity> ber =
                            Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity);
                    if (ber != null) {
                        drawingPhantom = true;
                        try {
                            ber.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
                        } finally {
                            drawingPhantom = false;
                        }
                    }
                }

                // A piece with a baked model got its crack with the static mesh above.
                if (piece == crackedPiece && !staticDrawn) {
                    blockRenderer.renderSingleBlock(state, poseStack, crumbling(bufferSource, crackStage), packedLight, packedOverlay,
                            modelData, null);
                }

                poseStack.popPose();
            }
        }
    }

    /** Pushes the pose and moves it into {@code slice}; the caller pops. */
    private static void pushSlice(PoseStack poseStack, PieceTransforms.Slice slice) {
        poseStack.pushPose();
        poseStack.translate(slice.translation().x, slice.translation().y, slice.translation().z);
        poseStack.scale(slice.scale().x, slice.scale().y, slice.scale().z);
    }

    /** Every buffer of {@code bufferSource} as the crack texture of {@code stage}. */
    private static MultiBufferSource crumbling(MultiBufferSource bufferSource, int stage) {
        RenderType crumbleType = RenderType.crumbling(ResourceLocation.withDefaultNamespace(
                "textures/block/destroy_stage_" + stage + ".png"));
        return t -> bufferSource.getBuffer(crumbleType);
    }

    /** From the camera to the grid where it's seen: it may be on a sub-level. */
    private static double seenDistance(SubgridBlockEntity be) {
        return SubLevels.inWorld(be.getLevel(), Vec3.atCenterOf(be.getBlockPos()))
                .distanceTo(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }

    /** The cells of a grid, as a model or a fluid reads its neighbours. */
    private static SubgridFluidView viewOf(SubgridBlockEntity be) {
        // The grid's own level: a schematic's preview or a contraption draws it from a level of their own.
        return new SubgridFluidView(be, be.getLevel() != null ? be.getLevel() : Minecraft.getInstance().level);
    }

    /**
     * What a piece's model draws from, gathered as a chunk gathers it for a real block: what its block entity hands it,
     * and what the model looks up around it (Create's pipes draw their joints from their neighbours, and hand nothing
     * from the block entity). Only for a piece with a block entity, as a chunk only asks for one.
     */
    static ModelData gatheredModelData(SubgridBlockEntity be, PlacedPiece piece, BlockState state, BlockRenderDispatcher blockRenderer) {
        if (!(piece.runtimeBlockEntity instanceof BlockEntity)) return ModelData.EMPTY;
        ModelData entityData = modelDataOf(piece);
        return blockRenderer.getBlockModel(state).getModelData(viewOf(be), piece.anchor, state, entityData != null ? entityData : ModelData.EMPTY);
    }

    /**
     * What a piece's block entity hands its model, or null if nothing: in a chunk a model gets this from the block
     * entity at its position, and a model built on it (a block taking the look of another) can have no quads without.
     */
    @Nullable
    static ModelData modelDataOf(PlacedPiece piece) {
        if (!(piece.runtimeBlockEntity instanceof BlockEntity blockEntity)) return null;
        var data = blockEntity.getModelData();
        return data.getProperties().isEmpty() ? null : data;
    }

    /**
     * True while a piece's block entity renderer runs. Its block entity sits at a virtual position far from the
     * camera, so renderers that skip what is far away by that position (Create's frequency and filter items) are
     * told it is near; the SubgridBlock itself was already culled by distance like any block entity.
     */
    public static boolean drawingPhantom;

    /** Past vanilla's 64 blocks too, or a changed grid out there would never ask for its section to be rebuilt. */
    @Override
    public int getViewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    }

    @Override
    public boolean shouldRenderOffScreen(SubgridBlockEntity be) {
        return false;
    }
}
