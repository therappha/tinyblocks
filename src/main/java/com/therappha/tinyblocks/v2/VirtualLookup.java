package com.therappha.tinyblocks.v2;

import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The piece behind a virtual-frame position (SubgridBlockEntity#virtualOrigin), found from the real world near
 * someone. Mods address a block entity by position in their packets — Create's value settings name the block a
 * player configured — and for a piece that position is its phantom's virtual one, which the real level knows
 * nothing about. Only real positions near {@code near} are read, so a virtual position (millions of blocks away)
 * never loads a chunk.
 */
public final class VirtualLookup {

    /** Grid sizes a SubgridBlock can have. */
    private static final int[] SIZES = {2, 4, 8, 16};
    /** How far from {@code near} a SubgridBlock may be: well past any interaction range. */
    private static final double MAX_DISTANCE = 64;

    public record Found(SubgridBlockEntity grid, PlacedPiece piece) {}

    private VirtualLookup() {}

    @Nullable
    public static Found find(Level level, BlockPos virtual, Vec3 near) {
        for (int size : SIZES) {
            for (BlockPos real : realCandidates(virtual, size, near)) {
                if (!level.isLoaded(real) || !(level.getBlockEntity(real) instanceof SubgridBlockEntity grid)) continue;
                if (grid.getGridSize() != size) continue;
                BlockPos cell = virtual.subtract(grid.virtualOrigin());
                if (!grid.inBounds(cell.getX(), cell.getY(), cell.getZ())) continue;
                PlacedPiece piece = grid.getPieceAt(cell.getX(), cell.getY(), cell.getZ());
                if (piece != null) return new Found(grid, piece);
            }
        }
        return null;
    }

    /**
     * Real block positions near {@code near} whose grid of {@code size} would hold {@code virtual}. Vertically
     * there is one; horizontally the frame folds every 2^20 blocks (SubgridBlockEntity#virtualOrigin), so take
     * the fold of the coordinate nearest to {@code near}.
     */
    static List<BlockPos> realCandidates(BlockPos virtual, int size, Vec3 near) {
        int x = nearestFold(Math.floorDiv(virtual.getX(), size), near.x);
        int y = Math.floorDiv(virtual.getY(), size);
        int z = nearestFold(Math.floorDiv(virtual.getZ(), size), near.z);
        BlockPos real = new BlockPos(x, y, z);
        List<BlockPos> out = new ArrayList<>(1);
        if (Vec3.atCenterOf(real).distanceTo(near) <= MAX_DISTANCE) out.add(real);
        return out;
    }

    private static int nearestFold(int folded, double near) {
        long window = SubgridBlockEntity.HORIZONTAL_WINDOW;
        long turns = Math.round((near - folded) / window);
        return (int) (folded + turns * window);
    }
}
