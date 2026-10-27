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

    /** Without an owning grid, positions are their own: no mapping, no signal from outside, nothing to resync. */
    @Test
    void aBareCellSpaceMapsPositionsToThemselves() {
        FakeCellGetter cells = new FakeCellGetter();
        BlockPos pos = new BlockPos(3, -2, 7);
        assertEquals(pos, cells.toWorld(pos));
        assertEquals(pos, cells.toLocal(pos));
        assertEquals(pos, cells.resolveLocal(pos));
        assertEquals(null, cells.outsideSignal(pos, net.minecraft.core.Direction.UP, false));
        cells.blockUpdatedAt(pos); // no grid to tell
        assertEquals(16, cells.getHeight());
        assertEquals(0, cells.getMinBuildHeight());
    }

    /** Block entities written and removed during a call are captured, for the call's caller to apply. */
    @Test
    void blockEntitiesSetAndRemovedAreCaptured() {
        FakeCellGetter cells = new FakeCellGetter();
        BlockPos at = new BlockPos(1, 1, 1);
        var chest = new net.minecraft.world.level.block.entity.ChestBlockEntity(at, Blocks.CHEST.defaultBlockState());

        cells.setBlockEntity(at, chest);
        assertEquals(chest, cells.getBlockEntity(at));
        assertEquals(chest, cells.touchedBlockEntities().get(at));

        cells.removeBlockEntityAt(at);
        assertEquals(null, cells.getBlockEntity(at));
        assertEquals(true, cells.removedBlockEntityPositions().contains(at));

        cells.setBlockEntity(at, chest); // set again: no longer counted as removed
        assertEquals(false, cells.removedBlockEntityPositions().contains(at));
    }

    /** A cell's fluid is its block state's, and every write shows in touchedCells. */
    @Test
    void fluidComesFromTheStateAndWritesAreTracked() {
        FakeCellGetter cells = new FakeCellGetter();
        BlockPos at = new BlockPos(2, 0, 2);
        cells.set(at, Blocks.WATER.defaultBlockState());
        assertEquals(net.minecraft.world.level.material.Fluids.WATER, cells.getFluidState(at).getType()); // not by tag: no tags without a datapack
        assertEquals(true, cells.getFluidState(new BlockPos(5, 5, 5)).isEmpty());
        assertEquals(Blocks.WATER.defaultBlockState(), cells.touchedCells().get(at));
    }
}
