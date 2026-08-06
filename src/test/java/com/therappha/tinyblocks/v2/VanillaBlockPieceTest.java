package com.therappha.tinyblocks.v2;

import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VanillaBlockPieceTest {

    private static SubgridBlockEntity newEntity() {
        return new SubgridBlockEntity(Registration.SUBGRID_BLOCK_ENTITY.get(), BlockPos.ZERO,
                Registration.SUBGRID_BLOCK.get().defaultBlockState());
    }

    private static PlacedPiece piece(BlockPos anchor) {
        return new PlacedPiece(VanillaBlockPiece.INSTANCE, anchor, Direction.NORTH);
    }

    @Test
    void findsEveryDirectionByGeometricDelta() {
        SubgridBlockEntity be = newEntity();
        BlockPos anchor = new BlockPos(4, 4, 4);
        PlacedPiece center = piece(anchor);
        for (Direction dir : Direction.values()) {
            PlacedPiece neighbor = piece(anchor.relative(dir));
            assertEquals(dir, VanillaBlockPiece.directionTo(be, center, neighbor), "direction " + dir);
        }
    }

    @Test
    void findsTheDirectionOfANeighborThatsAlreadyBeenDetachedFromTheGrid() {
        // The bug directionTo's own doc comment describes: a live neighborFacing lookup finds
        // nothing for a piece that already left be's grid (its own onNeighborChanged already ran,
        // possibly removing it, before this notification fires) — the geometric anchor-delta path
        // must resolve the direction WITHOUT needing changedNeighbor to still be placed anywhere.
        SubgridBlockEntity be = newEntity();
        BlockPos anchor = new BlockPos(2, 2, 2);
        PlacedPiece center = piece(anchor);
        // Deliberately never added to be's grid at all, simulating "already removed by the time
        // this runs" — if directionTo fell back to a live be.neighborFacing lookup first, this
        // would resolve to null.
        PlacedPiece detachedNeighbor = piece(anchor.above());

        assertEquals(Direction.UP, VanillaBlockPiece.directionTo(be, center, detachedNeighbor));
    }

    @Test
    void returnsNullWhenNeitherGeometryNorALiveLookupCanExplainTheNeighbor() {
        SubgridBlockEntity be = newEntity();
        PlacedPiece center = piece(new BlockPos(1, 1, 1));
        PlacedPiece unrelated = piece(new BlockPos(6, 6, 6)); // not adjacent, not in be's grid
        assertNull(VanillaBlockPiece.directionTo(be, center, unrelated));
    }
}
