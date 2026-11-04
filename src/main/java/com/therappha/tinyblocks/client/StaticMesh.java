package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therappha.tinyblocks.compat.create.ShaftConnectorPiece;
import com.therappha.tinyblocks.subgrid.PieceFaceCulling;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private record PieceQuads(Vector3f translation, Vector3f scale, float r, float g, float b,
                              List<BakedQuad> quads) {}

    final int version;
    /** One model, to notice a resource reload (models replaced) without a version change. */
    private final BlockState sentinelState;
    private final BakedModel sentinelModel;
    final Set<PlacedPiece> baked = Collections.newSetFromMap(new IdentityHashMap<>());
    final List<PlacedPiece> dynamic = new ArrayList<>();
    private final Map<RenderType, List<PieceQuads>> batches = new LinkedHashMap<>();
    /** The same pieces by chunk render type, which is all a section's buffers take. */
    private final Map<RenderType, List<PieceQuads>> chunkBatches = new LinkedHashMap<>();
    /** Whether a section compile has taken this mesh, and whether one was asked for. */
    boolean inChunk;
    private boolean chunkRequested;
    int quadCount;

    private StaticMesh(SubgridBlockEntity be, BlockRenderDispatcher blockRenderer) {
        this.version = be.pieceVersion();
        BlockState sentinel = null;
        BakedModel sentinelModelFound = null;
        var colors = Minecraft.getInstance().getBlockColors();
        RandomSource random = RandomSource.create();
        for (PlacedPiece piece : be.getPieces()) {
            BlockState state = piece.definition.renderState(piece);
            if (state == null) continue;
            boolean single = ShaftConnectorPiece.faceOf(piece) == null;
            boolean model = state.getRenderShape() == RenderShape.MODEL;
            boolean hasEntity = state.getBlock() instanceof EntityBlock;
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
            float r = Mth.clamp((color >> 16 & 0xFF) / 255f, 0, 1);
            float g = Mth.clamp((color >> 8 & 0xFF) / 255f, 0, 1);
            float b = Mth.clamp((color & 0xFF) / 255f, 0, 1);
            PieceTransforms.Slice slice = PieceTransforms.slices(piece, be.getGridSize()).get(0);
            var modelData = ModelData.EMPTY;
            for (RenderType rt : bakedModel.getRenderTypes(state, RandomSource.create(42), modelData)) {
                List<BakedQuad> quads = new ArrayList<>();
                for (Direction side : Direction.values()) {
                    if (PieceFaceCulling.hidden(be, piece, state, side)) continue;
                    random.setSeed(42L);
                    quads.addAll(bakedModel.getQuads(state, side, random, modelData, rt));
                }
                random.setSeed(42L);
                quads.addAll(bakedModel.getQuads(state, null, random, modelData, rt));
                if (quads.isEmpty()) continue;
                quadCount += quads.size();
                PieceQuads pieceQuads = new PieceQuads(slice.translation(), slice.scale(), r, g, b, quads);
                batches.computeIfAbsent(RenderTypeHelper.getEntityRenderType(rt, false),
                                k -> new ArrayList<>())
                        .add(pieceQuads);
                chunkBatches.computeIfAbsent(rt, k -> new ArrayList<>()).add(pieceQuads);
            }
            baked.add(piece);
        }
        this.sentinelState = sentinel;
        this.sentinelModel = sentinelModelFound;
    }

    static StaticMesh of(SubgridBlockEntity be, BlockRenderDispatcher blockRenderer) {
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
        Minecraft.getInstance().levelRenderer.setSectionDirty(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /**
     * Writes this mesh in a section being compiled, off the render thread. {@code x, y, z}: the grid in the section.
     * A chunk's shader has no directional light, so each quad gets its side's shade here, as a block model does.
     */
    void writeToChunk(AddSectionGeometryEvent.SectionRenderingContext context,
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
                            (tinted ? piece.b() : 1) * shade, 1.0F, light, OverlayTexture.NO_OVERLAY);
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
