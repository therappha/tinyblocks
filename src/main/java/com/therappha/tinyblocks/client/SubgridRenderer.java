package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
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

        // Static pieces come from a cached, culled mesh (StaticMesh); only the rest go through the loop below.
        StaticMesh mesh = StaticMesh.of(be, blockRenderer);
        mesh.draw(poseStack, bufferSource, packedLight, packedOverlay);
        if (crackedPiece != null && mesh.baked.contains(crackedPiece)) {
            BlockState state = crackedPiece.definition.renderState(crackedPiece);
            RenderType crumbleType = RenderType.crumbling(ResourceLocation.withDefaultNamespace(
                    "textures/block/destroy_stage_" + crackStage + ".png"));
            MultiBufferSource crumbleSource = t -> bufferSource.getBuffer(crumbleType);
            for (PieceTransforms.Slice slice : PieceTransforms.slices(crackedPiece, be.getGridSize())) {
                poseStack.pushPose();
                poseStack.translate(slice.translation().x, slice.translation().y, slice.translation().z);
                poseStack.scale(slice.scale().x, slice.scale().y, slice.scale().z);
                blockRenderer.renderSingleBlock(state, poseStack, crumbleSource, packedLight, packedOverlay);
                poseStack.popPose();
            }
        }

        for (PlacedPiece piece : mesh.dynamic) {
            BlockState state = piece.definition.renderState(piece);
            if (state == null) continue;
            boolean staticDrawn = mesh.baked.contains(piece);

            // #TODO remove before release — water-rendering investigation. A fluid's BlockState
            // has RenderShape.INVISIBLE (same as any real water/lava), so renderSingleBlock below
            // is a deliberate no-op for it — fluids render through a completely separate path
            // (see SubgridFluidView's doc comment) that needs real neighbor-height sampling, not
            // just this one BlockState. renderLiquid takes no PoseStack (see ScaledVertexConsumer's
            // doc comment) — the scale has to be applied by wrapping the buffer instead.
            FluidState fluidState = state.getFluidState();
            if (!fluidState.isEmpty()) {
                // The grid's own level: a schematic's preview or a contraption draws it from a level of their own.
                if (fluidView == null) fluidView = new SubgridFluidView(be, be.getLevel() != null ? be.getLevel() : Minecraft.getInstance().level);
                RenderType fluidRenderType = ItemBlockRenderTypes.getRenderLayer(fluidState);
                var fluidBuffer = new ScaledVertexConsumer(bufferSource.getBuffer(fluidRenderType), cell, basePose);
                blockRenderer.renderLiquid(piece.anchor, fluidView, fluidBuffer, state, fluidState);
            }

            if (piece.runtimeBlockEntity == null && piece.definition instanceof VanillaBlockPiece && be.getLevel() != null) {
                VanillaBlockPiece.blockEntityFor(piece, be, be.getLevel());
            }
            // One slice for an ordinary piece; a shaft connector draws its funnel slice by slice (PieceTransforms).
            List<PieceTransforms.Slice> slices = PieceTransforms.slices(piece, be.getGridSize(), acrossOf(be, piece));
            if (isGear(be, piece)) {
                if (piece.runtimeBlockEntity instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity gear
                        && !(com.therappha.tinyblocks.client.flywheel.FlywheelLoaded.isLoaded()
                                && com.therappha.tinyblocks.client.flywheel.FlywheelCompat.drawnByVisual(be.getLevel(), gear))) {
                    for (int i = 0; i < slices.size(); i++) {
                        PieceTransforms.Slice slice = slices.get(i);
                        poseStack.pushPose();
                        poseStack.translate(slice.translation().x, slice.translation().y, slice.translation().z);
                        poseStack.scale(slice.scale().x, slice.scale().y, slice.scale().z);
                        com.therappha.tinyblocks.compat.create.client.GearConnectorDrawing.draw(gear, i, poseStack, bufferSource,
                                packedLight, be.getBlockPos().relative(piece.facing));
                        poseStack.popPose();
                    }
                }
                continue;
            }
            for (PieceTransforms.Slice slice : slices) {
                poseStack.pushPose();
                poseStack.translate(slice.translation().x, slice.translation().y, slice.translation().z);
                poseStack.scale(slice.scale().x, slice.scale().y, slice.scale().z);

                if (!staticDrawn) blockRenderer.renderSingleBlock(state, poseStack, bufferSource, packedLight, packedOverlay);

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
                        && !(com.therappha.tinyblocks.client.flywheel.FlywheelLoaded.isLoaded()
                                && com.therappha.tinyblocks.client.flywheel.FlywheelCompat.drawnByVisual(be.getLevel(), blockEntity))) {
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
                    ResourceLocation crackTex = ResourceLocation.withDefaultNamespace(
                        "textures/block/destroy_stage_" + crackStage + ".png");
                    RenderType crumbleType = RenderType.crumbling(crackTex);
                    MultiBufferSource crumbleSource = t -> bufferSource.getBuffer(crumbleType);
                    blockRenderer.renderSingleBlock(state, poseStack, crumbleSource, packedLight, packedOverlay);
                }

                poseStack.popPose();
            }
        }
    }

    /**
     * A grid's static pieces as baked quads (issue #64): built once per change of its pieces
     * (SubgridBlockEntity#pieceVersion) instead of looking every piece's model up and walking its quads every frame,
     * and without the faces a neighbouring piece in the grid hides (PieceFaceCulling) — in a dense grid most of them.
     * Exactly what renderSingleBlock drew for a MODEL piece: its model's quads per render type, tinted with the
     * block's colour, in the piece's cell. A piece with a block entity, a fluid, another render shape or more than one
     * slice still takes the per-frame path ({@link #dynamic}); one with a block entity keeps its model here.
     */
    static final class StaticMesh {
        private record PieceQuads(org.joml.Vector3f translation, org.joml.Vector3f scale, float r, float g, float b,
                                  List<net.minecraft.client.renderer.block.model.BakedQuad> quads) {}

        final int version;
        /** One model, to notice a resource reload (models replaced) without a version change. */
        private final BlockState sentinelState;
        private final net.minecraft.client.resources.model.BakedModel sentinelModel;
        final java.util.Set<PlacedPiece> baked = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final List<PlacedPiece> dynamic = new java.util.ArrayList<>();
        private final java.util.Map<RenderType, List<PieceQuads>> batches = new java.util.LinkedHashMap<>();
        int quadCount;

        private StaticMesh(SubgridBlockEntity be, net.minecraft.client.renderer.block.BlockRenderDispatcher blockRenderer) {
            this.version = be.pieceVersion();
            BlockState sentinel = null;
            net.minecraft.client.resources.model.BakedModel sentinelModelFound = null;
            var colors = Minecraft.getInstance().getBlockColors();
            net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();
            for (PlacedPiece piece : be.getPieces()) {
                BlockState state = piece.definition.renderState(piece);
                if (state == null) continue;
                boolean single = com.therappha.tinyblocks.compat.create.ShaftConnectorPiece.faceOf(piece) == null;
                boolean model = state.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL;
                boolean hasEntity = state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock;
                if (!single || !state.getFluidState().isEmpty() || !model || hasEntity || piece.runtimeBlockEntity != null) {
                    dynamic.add(piece);
                }
                if (!single || !model || !state.getFluidState().isEmpty()) continue;

                var bakedModel = blockRenderer.getBlockModel(state);
                if (sentinel == null) {
                    sentinel = state;
                    sentinelModelFound = bakedModel;
                }
                int color = colors.getColor(state, null, null, 0);
                float r = net.minecraft.util.Mth.clamp((color >> 16 & 0xFF) / 255f, 0, 1);
                float g = net.minecraft.util.Mth.clamp((color >> 8 & 0xFF) / 255f, 0, 1);
                float b = net.minecraft.util.Mth.clamp((color & 0xFF) / 255f, 0, 1);
                PieceTransforms.Slice slice = PieceTransforms.slices(piece, be.getGridSize()).get(0);
                var modelData = net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
                for (RenderType rt : bakedModel.getRenderTypes(state, net.minecraft.util.RandomSource.create(42), modelData)) {
                    List<net.minecraft.client.renderer.block.model.BakedQuad> quads = new java.util.ArrayList<>();
                    for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                        if (com.therappha.tinyblocks.subgrid.PieceFaceCulling.hidden(be, piece, state, side)) continue;
                        random.setSeed(42L);
                        quads.addAll(bakedModel.getQuads(state, side, random, modelData, rt));
                    }
                    random.setSeed(42L);
                    quads.addAll(bakedModel.getQuads(state, null, random, modelData, rt));
                    if (quads.isEmpty()) continue;
                    quadCount += quads.size();
                    batches.computeIfAbsent(net.neoforged.neoforge.client.RenderTypeHelper.getEntityRenderType(rt, false),
                                    k -> new java.util.ArrayList<>())
                            .add(new PieceQuads(slice.translation(), slice.scale(), r, g, b, quads));
                }
                baked.add(piece);
            }
            this.sentinelState = sentinel;
            this.sentinelModel = sentinelModelFound;
        }

        static StaticMesh of(SubgridBlockEntity be, net.minecraft.client.renderer.block.BlockRenderDispatcher blockRenderer) {
            if (be.renderCache instanceof StaticMesh mesh && mesh.version == be.pieceVersion()
                    && (mesh.sentinelState == null || blockRenderer.getBlockModel(mesh.sentinelState) == mesh.sentinelModel)) {
                return mesh;
            }
            StaticMesh mesh = new StaticMesh(be, blockRenderer);
            be.renderCache = mesh;
            return mesh;
        }

        void draw(PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay) {
            for (var batch : batches.entrySet()) {
                var consumer = bufferSource.getBuffer(batch.getKey());
                for (PieceQuads piece : batch.getValue()) {
                    poseStack.pushPose();
                    poseStack.translate(piece.translation().x, piece.translation().y, piece.translation().z);
                    poseStack.scale(piece.scale().x, piece.scale().y, piece.scale().z);
                    PoseStack.Pose pose = poseStack.last();
                    for (var quad : piece.quads()) {
                        if (quad.isTinted()) {
                            consumer.putBulkData(pose, quad, piece.r(), piece.g(), piece.b(), 1.0F, light, overlay);
                        } else {
                            consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, light, overlay);
                        }
                    }
                    poseStack.popPose();
                }
            }
        }
    }

    /**
     * True while a piece's block entity renderer runs. Its block entity sits at a virtual position far from the
     * camera, so renderers that skip what is far away by that position (Create's frequency and filter items) are
     * told it is near; the SubgridBlock itself was already culled by distance like any block entity.
     */
    public static boolean drawingPhantom;

    /**
     * For a shaft connector, the size of a SubgridBlock of the same size across its face, which it meets cell to cell
     * (a straight tiny shaft); 0 otherwise — a real block, or another size's connector, met by a full-size shaft.
     */
    public static int acrossOf(SubgridBlockEntity be, com.therappha.tinyblocks.subgrid.PlacedPiece piece) {
        net.minecraft.core.Direction face = com.therappha.tinyblocks.compat.create.ShaftConnectorPiece.faceOf(piece);
        int across = face == null ? 0 : com.therappha.tinyblocks.compat.create.ShaftConnectorPiece.sizeAcross(be, face);
        return across == be.getGridSize() ? across : 0;
    }

    /** Whether {@code piece} is a gear connector, drawn from Create's parts (GearConnectorDrawing) rather than as its block. */
    static boolean isGear(SubgridBlockEntity be, com.therappha.tinyblocks.subgrid.PlacedPiece piece) {
        return com.therappha.tinyblocks.compat.create.ShaftConnectorPiece.faceOf(piece) != null
                && com.therappha.tinyblocks.compat.create.ShaftConnectorPiece.isGear(be.getGridSize());
    }

    @Override
    public boolean shouldRenderOffScreen(SubgridBlockEntity be) {
        return false;
    }
}
