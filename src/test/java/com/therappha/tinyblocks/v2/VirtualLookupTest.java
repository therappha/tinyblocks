package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualLookupTest {

    /** Cell (3,5,3) of an 8-grid at (1000, 70, -2000000): its z is folded into the frame's 2^20-block window. */
    @Test
    void aVirtualPositionLeadsBackToTheSubgridBlockNearThePlayer() {
        // -2000000 folds to 97152 (SubgridBlockEntity#wrapHorizontal), so the grid's virtual origin z is 97152 * 8.
        BlockPos virtual = new BlockPos(1000 * 8 + 3, 70 * 8 + 5, 97152 * 8 + 3);
        List<BlockPos> candidates = VirtualLookup.realCandidates(virtual, 8, new Vec3(1000.5, 71, -1999998));
        assertEquals(List.of(new BlockPos(1000, 70, -2000000)), candidates);
    }

    @Test
    void nothingFarFromThePlayerIsACandidate() {
        BlockPos virtual = new BlockPos(1000 * 8, 70 * 8, 1000 * 8);
        assertTrue(VirtualLookup.realCandidates(virtual, 8, new Vec3(0, 70, 0)).isEmpty());
    }
}
