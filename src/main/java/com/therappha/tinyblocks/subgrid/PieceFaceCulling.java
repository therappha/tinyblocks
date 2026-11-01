package com.therappha.tinyblocks.subgrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Which faces of a piece's model are hidden by the piece in the next cell of the same grid, as vanilla's
 * Block#shouldRenderFace decides it between two blocks (issue #64): two stone pieces side by side don't draw the
 * faces they share. Only inside one grid — a face on the grid's edge is always drawn, so nothing outside the grid
 * can make a cached mesh stale. Common code, so the server-side cost tests can count faces too.
 */
public final class PieceFaceCulling {

    private PieceFaceCulling() {}

    /** Whether the {@code side} face of {@code piece}'s model, holding {@code state}, is hidden by its neighbour. */
    public static boolean hidden(SubgridBlockEntity be, PlacedPiece piece, BlockState state, Direction side) {
        if (!isSingleCell(piece)) return false;
        int nx = piece.anchor.getX() + side.getStepX();
        int ny = piece.anchor.getY() + side.getStepY();
        int nz = piece.anchor.getZ() + side.getStepZ();
        if (!be.inBounds(nx, ny, nz)) return false;
        PlacedPiece next = be.getPieceAt(nx, ny, nz);
        if (next == null || !isSingleCell(next)) return false;
        BlockState neighbour = next.definition.renderState(next);
        if (neighbour == null || neighbour.isAir()) return false;
        try {
            if (state.skipRendering(neighbour, side)) return true;
            if (!neighbour.canOcclude()) return false;
            VoxelShape own = state.getFaceOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, side);
            if (own.isEmpty()) return false;
            VoxelShape theirs = neighbour.getFaceOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, side.getOpposite());
            return !Shapes.joinIsNotEmpty(own, theirs, BooleanOp.ONLY_FIRST);
        } catch (RuntimeException e) {
            return false; // a shape that needs a real world around it: draw the face
        }
    }

    private static boolean isSingleCell(PlacedPiece piece) {
        return piece.footprint.getX() == 1 && piece.footprint.getY() == 1 && piece.footprint.getZ() == 1;
    }

    /** How many of the grid's piece faces a renderer draws once the hidden ones are skipped (6 per piece without). */
    public static int countDrawnFaces(SubgridBlockEntity be) {
        int drawn = 0;
        for (PlacedPiece piece : be.getPieces()) {
            BlockState state = piece.definition.renderState(piece);
            if (state == null) continue;
            for (Direction side : Direction.values()) {
                if (!hidden(be, piece, state, side)) drawn++;
            }
        }
        return drawn;
    }
}
