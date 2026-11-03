package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A grid's static pieces as baked quads (issue #64): built once per change of its pieces
 * (SubgridBlockEntity#pieceVersion) instead of looking every piece's model up and walking its quads every frame,
 * and without the faces a neighbouring piece in the grid hides (PieceFaceCulling) — in a dense grid most of them.
 * Exactly what renderSingleBlock drew for a MODEL piece: its model's quads per render type, tinted with the
 * block's colour, in the piece's cell. A piece with a block entity, a fluid, another render shape or more than one
 * slice still takes the per-frame path ({@link #dynamic}); one with a block entity keeps its model here, unless
 * that model reads the block entity's model data, which changes without the pieces changing.
 */
final class StaticMesh {
    private record PieceQuads(org.joml.Vector3f translation, org.joml.Vector3f scale, float r, float g, float b,
                              List<net.minecraft.client.renderer.block.model.BakedQuad> quads) {}

    final int version;
    /** One model, to notice a resource reload (models replaced) without a version change. */
    private final BlockState sentinelState;
    private final net.minecraft.client.resources.model.BakedModel sentinelModel;
    final java.util.Set<PlacedPiece> baked = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    final List<PlacedPiece> dynamic = new java.util.ArrayList<>();
    private final java.util.Map<RenderType, List<PieceQuads>> batches = new java.util.LinkedHashMap<>();
    /** The same pieces by chunk render type, which is all a section's buffers take. */
    private final java.util.Map<RenderType, List<PieceQuads>> chunkBatches = new java.util.LinkedHashMap<>();
    /** Whether a section compile has taken this mesh, and whether one was asked for. */
    boolean inChunk;
    private boolean chunkRequested;
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
            // Built here, not left to the first frame: whether its model can be cached depends on it.
            if (hasEntity && piece.runtimeBlockEntity == null && piece.definition instanceof VanillaBlockPiece && be.getLevel() != null) {
                VanillaBlockPiece.blockEntityFor(piece, be, be.getLevel());
            }
            if (!single || !state.getFluidState().isEmpty() || !model || hasEntity || piece.runtimeBlockEntity != null) {
                dynamic.add(piece);
            }
            if (!single || !model || !state.getFluidState().isEmpty() || SubgridRenderer.modelDataOf(piece) != null) continue;

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
                PieceQuads pieceQuads = new PieceQuads(slice.translation(), slice.scale(), r, g, b, quads);
                batches.computeIfAbsent(net.neoforged.neoforge.client.RenderTypeHelper.getEntityRenderType(rt, false),
                                k -> new java.util.ArrayList<>())
                        .add(pieceQuads);
                chunkBatches.computeIfAbsent(rt, k -> new java.util.ArrayList<>()).add(pieceQuads);
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

    /** Asks once for this grid's section to be compiled again, when this mesh isn't the one in it. */
    void requestChunk(SubgridBlockEntity be) {
        if (inChunk || chunkRequested) return;
        chunkRequested = true;
        BlockPos pos = be.getBlockPos();
        Minecraft.getInstance().levelRenderer.setSectionDirty(net.minecraft.core.SectionPos.blockToSectionCoord(pos.getX()),
                net.minecraft.core.SectionPos.blockToSectionCoord(pos.getY()),
                net.minecraft.core.SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /**
     * Writes this mesh in a section being compiled, off the render thread. {@code x, y, z}: the grid in the section.
     * A chunk's shader has no directional light, so each quad gets its side's shade here, as a block model does.
     */
    void writeToChunk(net.neoforged.neoforge.client.event.AddSectionGeometryEvent.SectionRenderingContext context,
                      int x, int y, int z, int light) {
        PoseStack poseStack = context.getPoseStack();
        var region = context.getRegion();
        for (var batch : chunkBatches.entrySet()) {
            var consumer = context.getOrCreateChunkBuffer(batch.getKey());
            for (PieceQuads piece : batch.getValue()) {
                poseStack.pushPose();
                poseStack.translate(x + piece.translation().x, y + piece.translation().y, z + piece.translation().z);
                poseStack.scale(piece.scale().x, piece.scale().y, piece.scale().z);
                PoseStack.Pose pose = poseStack.last();
                for (var quad : piece.quads()) {
                    float shade = region.getShade(quad.getDirection(), quad.isShade());
                    boolean tinted = quad.isTinted();
                    consumer.putBulkData(pose, quad, (tinted ? piece.r() : 1) * shade, (tinted ? piece.g() : 1) * shade,
                            (tinted ? piece.b() : 1) * shade, 1.0F, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                }
                poseStack.popPose();
            }
        }
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
