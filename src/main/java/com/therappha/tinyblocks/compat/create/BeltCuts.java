package com.therappha.tinyblocks.compat.create;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A belt doesn't exist in part. Taking only some of the grids a belt runs across is refused: breaking it instead would
 * hand back its items while the saved half went on existing, a way to duplicate belts. A segment put down with nothing
 * to go on to, from a save made before this rule, goes away with the rest of its belt, and gives nothing.
 */
public final class BeltCuts {

    private BeltCuts() {}

    /** Whether a belt runs out of {@code taken} into a grid left behind. */
    public static boolean cutBy(Collection<SubgridBlockEntity> taken) {
        for (SubgridBlockEntity grid : taken) {
            for (PlacedPiece piece : grid.getPieces()) {
                if (!(piece.runtimeState instanceof BlockState state) || !isBelt(state)) continue;
                for (boolean forward : new boolean[] {true, false}) {
                    BlockPos next = nextCell(grid, state, piece.anchor, forward);
                    if (next != null && grid.resolve(next.getX(), next.getY(), next.getZ()) instanceof SubgridBlockEntity.CellRef.InAdjacentGrid a
                            && !taken.contains(a.grid()) && isBelt(grid.realBlockStateAt(next.getX(), next.getY(), next.getZ()))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** After {@code placed} are down: a belt with a segment leading to nothing goes. Create's onRemove takes the rest of it. */
    public static void removeLooseEnds(Collection<SubgridBlockEntity> placed) {
        for (SubgridBlockEntity grid : placed) {
            for (PlacedPiece piece : new ArrayList<>(grid.getPieces())) {
                if (!(piece.runtimeState instanceof BlockState state) || !isBelt(state)) continue;
                BlockPos at = piece.anchor;
                if (grid.getPieceAt(at.getX(), at.getY(), at.getZ()) != piece) continue; // gone with an earlier segment
                if (leadsToNothing(grid, state, at, true) || leadsToNothing(grid, state, at, false)) {
                    grid.removePieceAt(at.getX(), at.getY(), at.getZ());
                }
            }
        }
    }

    private static boolean leadsToNothing(SubgridBlockEntity grid, BlockState state, BlockPos cell, boolean forward) {
        BlockPos next = nextCell(grid, state, cell, forward);
        return next != null && !isBelt(grid.realBlockStateAt(next.getX(), next.getY(), next.getZ()));
    }

    @Nullable
    private static BlockPos nextCell(SubgridBlockEntity grid, BlockState state, BlockPos cell, boolean forward) {
        BlockPos next = BeltBlock.nextSegmentPosition(state, grid.virtualPosOf(cell), forward);
        return next == null ? null : next.subtract(grid.virtualOrigin());
    }

    private static boolean isBelt(BlockState state) {
        return AllBlocks.BELT.has(state);
    }
}
