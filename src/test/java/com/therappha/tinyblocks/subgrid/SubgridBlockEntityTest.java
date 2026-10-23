package com.therappha.tinyblocks.subgrid;

import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubgridBlockEntityTest {

    private static SubgridBlockEntity newEntity() {
        return newEntity(BlockPos.ZERO);
    }

    private static SubgridBlockEntity newEntity(BlockPos pos) {
        // BlockEntity's own constructor validates type.isValid(state) — needs the real registered
        // type/block, not a stand-in like Blocks.STONE, or construction itself throws.
        return new SubgridBlockEntity(Registration.SUBGRID_BLOCK_ENTITY.get(), pos,
                Registration.SUBGRID_BLOCK.get().defaultBlockState());
    }

    private static PlacedPiece piece(BlockPos anchor) {
        return new PlacedPiece(VanillaBlockPiece.INSTANCE, anchor, Direction.NORTH);
    }

    // --- pending drops (#37's mechanism) ------------------------------------------------------

    @Test
    void deferredDropFiresImmediatelyWhenNothingIsAnimating() {
        SubgridBlockEntity be = newEntity();
        be.deferDrop(Blocks.STONE.defaultBlockState(), Vec3.ZERO, List.of(new ItemStack(Items.STONE)), 100L);
        assertEquals(1, be.pendingDropCount());

        List<SubgridBlockEntity.PendingDrop> ready = be.drainReadyDrops(100L);
        assertEquals(1, ready.size());
        assertEquals(0, be.pendingDropCount());
    }

    @Test
    void deferredDropWaitsWhileSomethingIsStillAnimating() {
        SubgridBlockEntity be = newEntity();
        PlacedPiece animating = piece(BlockPos.ZERO);
        be.captureStarted(animating);

        be.deferDrop(Blocks.STONE.defaultBlockState(), Vec3.ZERO, List.of(new ItemStack(Items.STONE)), 100L);
        assertEquals(0, be.drainReadyDrops(105L).size(),
                "should still be held while animating and within the safety window");
        assertEquals(1, be.pendingDropCount());

        be.captureEnded(animating);
        assertEquals(1, be.drainReadyDrops(106L).size(), "released as soon as nothing's animating anymore");
    }

    @Test
    void safetyDeadlineFiresEvenIfStillAnimating() {
        SubgridBlockEntity be = newEntity();
        PlacedPiece animating = piece(BlockPos.ZERO);
        be.captureStarted(animating);
        be.deferDrop(Blocks.STONE.defaultBlockState(), Vec3.ZERO, List.of(new ItemStack(Items.STONE)), 0L);

        assertEquals(0, be.drainReadyDrops(199L).size());
        assertEquals(1, be.drainReadyDrops(200L).size(),
                "safety deadline must fire the drop even if activeCaptures never emptied");
    }

    @Test
    void cancelPendingDropRemovesAMatchingStateBeforeItFires() {
        SubgridBlockEntity be = newEntity();
        PlacedPiece animating = piece(BlockPos.ZERO);
        be.captureStarted(animating);
        be.deferDrop(Blocks.STONE.defaultBlockState(), Vec3.ZERO, List.of(new ItemStack(Items.STONE)), 0L);

        be.cancelPendingDrop(Blocks.STONE.defaultBlockState());
        assertEquals(0, be.pendingDropCount());
        assertEquals(0, be.drainReadyDrops(1000L).size());
    }

    @Test
    void emptyDropsListIsNeverQueued() {
        SubgridBlockEntity be = newEntity();
        be.deferDrop(Blocks.STONE.defaultBlockState(), Vec3.ZERO, List.of(), 0L);
        assertEquals(0, be.pendingDropCount());
    }

    @Test
    void hasActiveCapturesTracksStartAndEnd() {
        SubgridBlockEntity be = newEntity();
        PlacedPiece p = piece(BlockPos.ZERO);
        assertFalse(be.hasActiveCaptures());
        be.captureStarted(p);
        assertTrue(be.hasActiveCaptures());
        be.captureEnded(p);
        assertFalse(be.hasActiveCaptures());
    }

    // --- grid bookkeeping ----------------------------------------------------------------------

    @Test
    void placeGetAndRemoveRoundTrip() {
        SubgridBlockEntity be = newEntity();
        PlacedPiece p = piece(new BlockPos(1, 2, 3));
        assertTrue(be.placePiece(p));
        assertSame(p, be.getPieceAt(1, 2, 3));
        assertSame(p, be.removePieceAt(1, 2, 3));
        assertNull(be.getPieceAt(1, 2, 3));
    }

    @Test
    void placePieceRefusesAnOccupiedCell() {
        SubgridBlockEntity be = newEntity();
        assertTrue(be.placePiece(piece(new BlockPos(0, 0, 0))));
        assertFalse(be.placePiece(piece(new BlockPos(0, 0, 0))));
    }

    @Test
    void removingAMiddlePieceReindexesEveryoneAfterIt() {
        // rebuildAfterRemove rebuilds cellOwner from scratch after pieces.remove(idx) shifts every
        // later piece's own list index down by one — if that reindex ever regresses, a piece
        // "after" the removed one would silently point at the wrong (or no) cellOwner entry.
        SubgridBlockEntity be = newEntity();
        PlacedPiece a = piece(new BlockPos(0, 0, 0));
        PlacedPiece b = piece(new BlockPos(1, 0, 0));
        PlacedPiece c = piece(new BlockPos(2, 0, 0));
        be.placePiece(a);
        be.placePiece(b);
        be.placePiece(c);

        be.removePieceAt(1, 0, 0); // remove the middle one (b)

        assertNull(be.getPieceAt(1, 0, 0));
        assertSame(a, be.getPieceAt(0, 0, 0));
        assertSame(c, be.getPieceAt(2, 0, 0));
    }

    @Test
    void realPositionOfIsTheCellCenterNotTheWholeBlockOrigin() {
        SubgridBlockEntity be = newEntity(new BlockPos(5, 10, 3));
        Vec3 center = be.realPositionOf(new BlockPos(2, 0, 0)); // SUBGRID_BLOCK is gridSize 8, cell = 1/8
        assertEquals(5 + (2 + 0.5) / 8.0, center.x, 1e-9);
        assertEquals(10 + 0.5 / 8.0, center.y, 1e-9);
        assertEquals(3 + 0.5 / 8.0, center.z, 1e-9);
    }

    // --- cross-grid resolution (CellRef, issue #42) ---------------------------------------------

    @Test
    void resolveInBoundsReturnsLocalWithoutTouchingLevel() {
        // newEntity() never calls setLevel — an in-bounds resolve must not depend on it at all.
        SubgridBlockEntity be = newEntity();
        SubgridBlockEntity.CellRef ref = be.resolve(2, 3, 4);
        assertTrue(ref instanceof SubgridBlockEntity.CellRef.Local);
        var local = (SubgridBlockEntity.CellRef.Local) ref;
        assertEquals(2, local.x());
        assertEquals(3, local.y());
        assertEquals(4, local.z());
    }

    @Test
    void resolveSingleAxisOverflowWithoutServerLevelIsOutOfScope() {
        // Matches crossGridNeighborAt/realBlockStateAt's pre-refactor behavior: no ServerLevel to
        // check the real world against means the boundary can't be resolved at all.
        SubgridBlockEntity be = newEntity(); // gridSize 8, valid range [0,7]
        assertTrue(be.resolve(-1, 0, 0) instanceof SubgridBlockEntity.CellRef.OutOfScope);
        assertTrue(be.resolve(8, 0, 0) instanceof SubgridBlockEntity.CellRef.OutOfScope);
    }

    @Test
    void resolveDiagonalOverflowIsOutOfScopeRegardlessOfLevel() {
        SubgridBlockEntity be = newEntity();
        assertTrue(be.resolve(-1, -1, 0) instanceof SubgridBlockEntity.CellRef.OutOfScope);
        assertTrue(be.resolve(8, 8, 8) instanceof SubgridBlockEntity.CellRef.OutOfScope);
    }

    @Test
    void resolveWithKnownDirectionWithoutServerLevelIsOutOfScope() {
        SubgridBlockEntity be = newEntity();
        assertTrue(be.resolve(Direction.EAST, 8, 0, 0) instanceof SubgridBlockEntity.CellRef.OutOfScope);
    }

    @Test
    void resolveOrCreateInBoundsNeverTouchesRealLevel() {
        // Passing null realLevel would NPE the instant it's dereferenced — this only passes if the
        // in-bounds short-circuit genuinely never touches the parameter, matching resolve()'s own
        // in-bounds passthrough.
        SubgridBlockEntity be = newEntity();
        SubgridBlockEntity.CellRef ref = be.resolveOrCreate(0, 0, 0, null);
        assertTrue(ref instanceof SubgridBlockEntity.CellRef.Local);
    }

    // Diagonal overflow used to short-circuit to OutOfScope without ever touching realLevel — now
    // (issue #46) resolveOrCreate genuinely attempts to resolve it via chaining, same as resolve(),
    // so it necessarily touches realLevel. That needs a real ServerLevel to test meaningfully;
    // covered by SubgridCrossBoundaryGameTest instead of a null-realLevel unit test.

    @Test
    void wrapHandlesAnyOverflowMagnitudeNotJustOneStep() {
        assertEquals(7, SubgridBlockEntity.wrap(-1, 7));
        assertEquals(0, SubgridBlockEntity.wrap(8, 7));
        assertEquals(3, SubgridBlockEntity.wrap(3, 7), "in-range values pass through unchanged");
        // Issue #34's live follow-up: a piston chain-pushing a second block past the first needs
        // 8 and 9 to land on DIFFERENT wrapped cells (0 and 1), not collapse onto the same one.
        assertEquals(1, SubgridBlockEntity.wrap(9, 7), "one grid-width further than a single overflow");
        assertEquals(6, SubgridBlockEntity.wrap(-2, 7), "two steps under, wrapping from the far edge");
    }

    // --- chained resolution helpers (issue #43 / #34) ------------------------------------------

    @Test
    void singleStepOverflowAxisIdentifiesEachOverflowingEdge() {
        SubgridBlockEntity be = newEntity(); // gridSize 8, valid range [0,7]
        assertEquals(Direction.WEST, be.singleStepOverflowAxis(-1, 0, 0));
        assertEquals(Direction.EAST, be.singleStepOverflowAxis(8, 0, 0));
        assertEquals(Direction.DOWN, be.singleStepOverflowAxis(0, -1, 0));
        assertEquals(Direction.UP, be.singleStepOverflowAxis(0, 8, 0));
        assertEquals(Direction.NORTH, be.singleStepOverflowAxis(0, 0, -1));
        assertEquals(Direction.SOUTH, be.singleStepOverflowAxis(0, 0, 8));
    }

    @Test
    void singleStepOverflowAxisPicksXFirstWhenMultipleAxesOverflow() {
        SubgridBlockEntity be = newEntity();
        assertEquals(Direction.EAST, be.singleStepOverflowAxis(8, -1, 4),
                "matches issue #43's exact repro coordinate — a corner overflowing both X and Y");
    }

    @Test
    void singleStepOverflowAxisAcceptsMoreThanOneStepOver() {
        // Was refused (returned null -> OutOfScope) before issue #34's live follow-up: a piston
        // chain-pushing a second block past the first reads a position a full grid-width further
        // still, which is a real, correctly-resolvable query, not out of scope.
        SubgridBlockEntity be = newEntity();
        assertEquals(Direction.EAST, be.singleStepOverflowAxis(9, 0, 4));
    }

    @Test
    void foldRemainingIntoRealWorldAppliesEveryOtherOverflowingAxis() {
        BlockPos base = new BlockPos(10, 20, 30);
        // Matches issue #43's exact repro: X already resolved via EAST, Y (-1) still needs folding in.
        BlockPos result = SubgridBlockEntity.foldRemainingIntoRealWorld(base, Direction.EAST, 8, -1, 4, 8);
        assertEquals(new BlockPos(10, 19, 30), result);
    }

    @Test
    void foldRemainingIntoRealWorldIsANoOpWhenNoOtherAxisOverflows() {
        BlockPos base = new BlockPos(10, 20, 30);
        BlockPos result = SubgridBlockEntity.foldRemainingIntoRealWorld(base, Direction.EAST, 8, 3, 4, 8);
        assertEquals(base, result);
    }

    @Test
    void foldRemainingIntoRealWorldStepsMultipleBlocksForLargerMagnitude() {
        BlockPos base = new BlockPos(10, 20, 30);
        // Y overflowing by two full grid-widths (16 on an 8-cell grid) should step 2 real blocks up.
        BlockPos result = SubgridBlockEntity.foldRemainingIntoRealWorld(base, Direction.EAST, 8, 16, 4, 8);
        assertEquals(new BlockPos(10, 22, 30), result);
    }

    @Test
    void resolveCompoundOverflowWithoutServerLevelIsOutOfScope() {
        // Same as the single-axis case: no ServerLevel to resolve against means the whole chain
        // bails immediately, before any per-axis analysis.
        SubgridBlockEntity be = newEntity();
        assertTrue(be.resolve(8, -1, 4) instanceof SubgridBlockEntity.CellRef.OutOfScope);
    }

    // --- load cost (issue #50) ---------------------------------------------------------------

    @Test
    void loadingManyPiecesRebuildsTheShapeOnlyOnce() {
        var registries = net.minecraft.data.registries.VanillaRegistries.createLookup();
        SubgridBlockEntity source = newEntity();
        for (int x = 0; x < 5; x++) {
            PlacedPiece p = piece(new BlockPos(x, 0, 0));
            p.runtimeState = Blocks.STONE.defaultBlockState();
            assertTrue(source.placePiece(p));
        }
        net.minecraft.nbt.CompoundTag tag = source.getUpdateTag(registries);

        SubgridBlockEntity loaded = newEntity();
        loaded.loadAdditional(tag, registries);

        assertEquals(5, loaded.getPieces().size());
        assertEquals(1, loaded.shapeRebuildCount,
                "loading N pieces must rebuild the O(gridSize^3) shape once, not once per piece");
        assertFalse(loaded.getCachedShape().isEmpty());
    }
}
