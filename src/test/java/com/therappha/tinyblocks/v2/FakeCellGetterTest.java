package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeCellGetterTest {

    @Test
    void unsetCellFallsBackToAir() {
        FakeCellGetter cells = new FakeCellGetter();
        assertTrue(cells.getBlockState(BlockPos.ZERO).isAir());
    }

    @Test
    void setThenGetRoundTrips() {
        FakeCellGetter cells = new FakeCellGetter();
        cells.set(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        assertEquals(Blocks.STONE.defaultBlockState(), cells.getBlockState(BlockPos.ZERO));
    }

    @Test
    void setRawAndGetStateRawBypassSubclassAliasing() {
        // A subclass overriding set/getBlockState to alias positions (see SubgridFakeCellGetter)
        // must NOT be able to intercept setRaw/getStateRaw — that's the whole point of the "raw"
        // accessor: internal bookkeeping that already has a correct local coordinate must be able
        // to write/read it directly regardless of whatever aliasing is active for THIS call.
        FakeCellGetter cells = new FakeCellGetter() {
            @Override
            public void set(BlockPos pos, BlockState state) {
                throw new AssertionError("setRaw must not go through the overridable set(...)");
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                throw new AssertionError("getStateRaw must not go through the overridable getBlockState(...)");
            }
        };
        BlockPos anchor = new BlockPos(3, 1, 5);
        cells.setRaw(anchor, Blocks.STONE.defaultBlockState());
        assertEquals(Blocks.STONE.defaultBlockState(), cells.getStateRaw(anchor));
    }

    // --- aliasOffset: the exact coordinate math behind #33/#37 -------------------------------

    @Test
    void aliasOffsetIsIdentityWhenNotAliasing() {
        BlockPos pos = new BlockPos(7, 2, -3);
        assertEquals(pos, FakeCellGetter.aliasOffset(null, BlockPos.ZERO, pos));
    }

    @Test
    void aliasOffsetMapsTheAliasedPositionExactly() {
        // onUse's own entry point: querying aliasFrom itself must land exactly on aliasTo — this
        // is what makes cells.getBlockState(realPos) find the clicked piece at its own anchor.
        BlockPos realPos = new BlockPos(1000, 64, -2000);
        BlockPos anchor = new BlockPos(6, 1, 3);
        assertEquals(anchor, FakeCellGetter.aliasOffset(realPos, anchor, realPos));
    }

    @Test
    void aliasOffsetTranslatesNeighborQueriesByTheSameOffset() {
        // The b834add fix: a neighbor query (e.g. DoorBlock's pos.below()) must land on the
        // sibling piece's own anchor, not just the exact aliased position.
        BlockPos realPos = new BlockPos(1000, 64, -2000);
        BlockPos anchor = new BlockPos(6, 1, 3);
        assertEquals(anchor.below(), FakeCellGetter.aliasOffset(realPos, anchor, realPos.below()));
    }

    @Test
    void aliasOffsetCorruptsAnAlreadyLocalCoordinate() {
        // This is the #33/#37 bug, pinned down: piece.anchor is ALREADY a local coordinate, not a
        // realPos-relative one. Feeding it through aliasOffset while aliasing is active (as
        // populateCells/applyChanges' diff read used to) does NOT round-trip back to itself —
        // proving why those two call sites need setRaw/getStateRaw instead of aliasOffset. If this
        // assertion ever starts failing, aliasOffset's semantics changed and every caller that
        // relies on setRaw/getStateRaw to bypass it needs re-auditing.
        BlockPos realPos = new BlockPos(1000, 64, -2000);
        BlockPos anchor = new BlockPos(6, 1, 3);
        BlockPos otherPieceAnchor = new BlockPos(6, 2, 3);
        assertNotEquals(otherPieceAnchor, FakeCellGetter.aliasOffset(realPos, anchor, otherPieceAnchor));
    }
}
