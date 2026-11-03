package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Fluids stay where they are held: not out of a closed grid, not into the real world, and not into a neighbouring grid
 * unless that grid is open to them. Each way across a face is tried from both of its sides.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridFluidLeakGameTest {

    private static final int SIZE = 8;
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();

    /** A clear space around a position, with the template's own blocks out of the way. */
    private static void clearAround(GameTestHelper helper, BlockPos at, int reach) {
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-reach, 0, -reach), at.offset(reach, reach, reach))) {
            helper.setBlock(pos, Blocks.AIR);
        }
    }

    private static SubgridBlockEntity grid(GameTestHelper helper, BlockPos at, int size) {
        helper.setBlock(at, Blocks.AIR);
        return SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(at), size);
    }

    /** Every cell on the outside of the grid, stone. */
    private static void shell(SubgridBlockEntity be, ServerLevel level) {
        int max = be.getGridSize() - 1;
        for (BlockPos cell : BlockPos.betweenClosed(0, 0, 0, max, max, max)) {
            boolean outside = cell.getX() == 0 || cell.getX() == max || cell.getY() == 0 || cell.getY() == max || cell.getZ() == 0 || cell.getZ() == max;
            if (outside) SubgridTestSupport.placePiece(be, level, cell.immutable(), STONE);
        }
    }

    private static boolean anyFluid(SubgridBlockEntity be) {
        return be.getPieces().stream().anyMatch(p -> !p.definition.renderState(p).getFluidState().isEmpty());
    }

    private static void assertNoRealFluidAround(GameTestHelper helper, BlockPos at, int reach) {
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-reach, -1, -reach), at.offset(reach, reach, reach))) {
            helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(pos)).isEmpty(), "a real fluid at " + pos.toShortString());
        }
    }

    private static void assertNoGridAround(GameTestHelper helper, BlockPos at, int reach) {
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-reach, -1, -reach), at.offset(reach, reach, reach))) {
            if (pos.equals(at)) continue;
            helper.assertTrue(!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof SubgridBlockEntity), "a new grid at " + pos.toShortString());
        }
    }

    private static void closedGridHolds(GameTestHelper helper, BlockState fluid, int ticks) {
        BlockPos at = new BlockPos(3, 3, 3);
        clearAround(helper, at, 2);
        SubgridBlockEntity be = grid(helper, at, SIZE);
        shell(be, helper.getLevel());
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(3, SIZE - 2, 3), fluid);
        helper.startSequence().thenIdle(ticks).thenExecute(() -> {
            assertNoRealFluidAround(helper, at, 2);
            assertNoGridAround(helper, at, 2);
            for (BlockPos cell : BlockPos.betweenClosed(0, 0, 0, SIZE - 1, 0, SIZE - 1)) {
                helper.assertTrue(STONE.equals(SubgridTestSupport.stateOf(be, cell)), "the floor changed at " + cell.toShortString());
            }
            helper.assertTrue(SubgridTestSupport.stateOf(be, new BlockPos(3, 1, 3)) != null, "the fluid never ran inside the grid");
        }).thenSucceed();
    }

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void waterStaysInAClosedGrid(GameTestHelper helper) {
        closedGridHolds(helper, WATER, 80);
    }

    @GameTest(template = "empty_platform", timeoutTicks = 400)
    public void lavaStaysInAClosedGrid(GameTestHelper helper) {
        closedGridHolds(helper, LAVA, 200);
    }

    /** Open to the air, water goes on in tiny cells, in a new grid: it never turns into a real block of water. */
    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void waterAtAnOpenFaceNeverBecomesARealBlock(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 3, 3);
        clearAround(helper, at, 2);
        SubgridBlockEntity be = grid(helper, at, SIZE);
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(SIZE - 1, 0, 3), STONE);
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(SIZE - 1, 1, 3), WATER);
        helper.startSequence().thenIdle(60).thenExecute(() -> assertNoRealFluidAround(helper, at, 2)).thenSucceed();
    }

    private static void realFluidLeavesAClosedGridAlone(GameTestHelper helper, BlockState fluid, int ticks) {
        BlockPos at = new BlockPos(3, 3, 3);
        clearAround(helper, at, 2);
        SubgridBlockEntity be = grid(helper, at, SIZE);
        shell(be, helper.getLevel());
        int pieces = be.getPieces().size();
        // Over the grid's face and running down past it, on a floor that leads to it.
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, -1, 2))) helper.setBlock(pos, Blocks.STONE);
        helper.setBlock(at.east(), fluid);
        helper.setBlock(at.above().east(), fluid);
        helper.startSequence().thenIdle(ticks).thenExecute(() -> {
            helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(at)) == be, "the real fluid washed the grid away");
            helper.assertTrue(be.getPieces().size() == pieces, be.getPieces().size() + " pieces, not " + pieces);
            helper.assertTrue(!anyFluid(be), "the real fluid got into a closed grid");
        }).thenSucceed();
    }

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void realWaterLeavesAClosedGridAlone(GameTestHelper helper) {
        realFluidLeavesAClosedGridAlone(helper, WATER, 60);
    }

    @GameTest(template = "empty_platform", timeoutTicks = 300)
    public void realLavaLeavesAClosedGridAlone(GameTestHelper helper) {
        realFluidLeavesAClosedGridAlone(helper, LAVA, 120);
    }

    /** A grid with empty cells on the wet side is not a space for real water, which is a whole block. */
    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void realWaterDoesNotWashAwayAGridWithRoomInIt(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 3, 3);
        clearAround(helper, at, 2);
        SubgridBlockEntity be = grid(helper, at, SIZE);
        PlacedPiece stone = SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(0, 0, 0), STONE);
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, -1, 2))) helper.setBlock(pos, Blocks.STONE);
        helper.setBlock(at.east(), WATER);
        helper.setBlock(at.above().east(), WATER); // and from above, which is where water gets in when it can
        helper.startSequence().thenIdle(60).thenExecute(() -> {
            helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(at)) == be, "real water washed away a grid with a piece in it");
            helper.assertTrue(be.getPieces().contains(stone), "the piece went");
        }).thenSucceed();
    }

    private static void twoGrids(GameTestHelper helper, java.util.function.BiConsumer<SubgridBlockEntity, SubgridBlockEntity> build,
                                 java.util.function.BiConsumer<SubgridBlockEntity, SubgridBlockEntity> check) {
        BlockPos at = new BlockPos(3, 3, 3);
        clearAround(helper, at, 2);
        SubgridBlockEntity west = grid(helper, at, SIZE), east = grid(helper, at.east(), SIZE);
        build.accept(west, east);
        helper.startSequence().thenIdle(60).thenExecute(() -> check.accept(west, east)).thenSucceed();
    }

    /** A wall across the whole of one grid's face toward the other. */
    private static void wall(SubgridBlockEntity be, int x, ServerLevel level) {
        for (BlockPos cell : BlockPos.betweenClosed(x, 0, 0, x, SIZE - 1, SIZE - 1)) SubgridTestSupport.placePiece(be, level, cell.immutable(), STONE);
    }

    /**
     * A floor with a wall on each side, along x. Flowing water heads for the nearest hole, and past any edge of a floor
     * there is one: left open, the water leaves by the side instead of the face being tried.
     */
    private static void trough(SubgridBlockEntity be, ServerLevel level) {
        int size = be.getGridSize(), z = size / 2;
        for (BlockPos cell : BlockPos.betweenClosed(0, 0, z - 1, size - 1, 0, z + 1)) SubgridTestSupport.placePiece(be, level, cell.immutable(), STONE);
        for (int x = 0; x < size; x++) {
            SubgridTestSupport.placePiece(be, level, new BlockPos(x, 1, z - 1), STONE);
            SubgridTestSupport.placePiece(be, level, new BlockPos(x, 1, z + 1), STONE);
        }
    }

    /** A cell of a grid's row, stone: the end of a trough. */
    private static void plug(SubgridBlockEntity be, int x, ServerLevel level) {
        SubgridTestSupport.placePiece(be, level, new BlockPos(x, 1, be.getGridSize() / 2), STONE);
    }

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void waterGoesEastIntoAGridOpenToIt(GameTestHelper helper) {
        twoGrids(helper, (west, east) -> {
            trough(west, helper.getLevel());
            trough(east, helper.getLevel());
            plug(west, 0, helper.getLevel());
            plug(east, SIZE - 1, helper.getLevel());
            SubgridTestSupport.placePiece(west, helper.getLevel(), new BlockPos(SIZE - 2, 1, 4), WATER);
        }, (west, east) -> helper.assertTrue(anyFluid(east), "water never crossed into the open grid east of it"));
    }

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void waterGoesWestIntoAGridOpenToIt(GameTestHelper helper) {
        twoGrids(helper, (west, east) -> {
            trough(west, helper.getLevel());
            trough(east, helper.getLevel());
            plug(west, 0, helper.getLevel());
            plug(east, SIZE - 1, helper.getLevel());
            SubgridTestSupport.placePiece(east, helper.getLevel(), new BlockPos(1, 1, 4), WATER);
        }, (west, east) -> helper.assertTrue(anyFluid(west), "water never crossed into the open grid west of it"));
    }

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void aWallInTheNextGridStopsWaterGoingEast(GameTestHelper helper) {
        twoGrids(helper, (west, east) -> {
            trough(west, helper.getLevel());
            plug(west, 0, helper.getLevel());
            wall(east, 0, helper.getLevel());
            SubgridTestSupport.placePiece(west, helper.getLevel(), new BlockPos(SIZE - 2, 1, 4), WATER);
        }, (west, east) -> {
            helper.assertTrue(SubgridTestSupport.stateOf(west, new BlockPos(SIZE - 1, 1, 4)) != null, "water never reached the wall");
            helper.assertTrue(!anyFluid(east), "water got into a grid walled against it");
        });
    }

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void aWallInTheNextGridStopsWaterGoingWest(GameTestHelper helper) {
        twoGrids(helper, (west, east) -> {
            trough(east, helper.getLevel());
            plug(east, SIZE - 1, helper.getLevel());
            wall(west, SIZE - 1, helper.getLevel());
            SubgridTestSupport.placePiece(east, helper.getLevel(), new BlockPos(1, 1, 4), WATER);
        }, (west, east) -> {
            helper.assertTrue(SubgridTestSupport.stateOf(east, new BlockPos(0, 1, 4)) != null, "water never reached the wall");
            helper.assertTrue(!anyFluid(west), "water got into a grid walled against it");
        });
    }

    /** Cells of two sizes don't line up: water at such a face stops there, with nothing written across it. */
    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void waterDoesNotCrossIntoAGridOfAnotherSize(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 3, 3);
        clearAround(helper, at, 2);
        SubgridBlockEntity fine = grid(helper, at, 16), coarse = grid(helper, at.east(), 8);
        trough(fine, helper.getLevel());
        plug(fine, 0, helper.getLevel());
        trough(coarse, helper.getLevel());
        SubgridTestSupport.placePiece(fine, helper.getLevel(), new BlockPos(14, 1, 8), WATER);
        helper.startSequence().thenIdle(60).thenExecute(() -> {
            helper.assertTrue(!anyFluid(coarse), "water crossed into a grid of another size");
            helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(at.east())) == coarse, "the grid of another size went");
            assertNoRealFluidAround(helper, at, 2);
        }).thenSucceed();
    }

    /** Lava in a grid next to real water, and real lava next to water in a grid: in a world of blocks the two would meet. */
    @GameTest(template = "empty_platform", timeoutTicks = 300)
    public void lavaAtAFaceMeetsRealWaterAcrossIt(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 3, 3);
        clearAround(helper, at, 2);
        SubgridBlockEntity be = grid(helper, at, SIZE);
        trough(be, helper.getLevel());
        helper.setBlock(at.east().below(), Blocks.STONE);
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(SIZE - 1, 1, 4), LAVA);
        helper.setBlock(at.east(), WATER);
        helper.startSequence().thenIdle(60).thenExecute(() -> {
            BlockState there = SubgridTestSupport.stateOf(be, new BlockPos(SIZE - 1, 1, 4));
            helper.assertTrue(there != null && there.is(Blocks.OBSIDIAN), "lava next to real water is still " + there);
        }).thenSucceed();
    }

}
