package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Regression coverage for issue #34: a piston pushing a block past a subgrid's own edge, into
 * real-world open air, must auto-create a new adjacent SubgridBlockEntity there and land the
 * pushed piece inside it — not silently drop the write. Deliberately leaves the neighboring
 * real-world cell as plain air beforehand (not a pre-placed subgrid) since #34's own report was
 * specifically about pushing into open air.
 *
 * Previously believed broken two different ways in sequence — both corrected:
 * 1. A misdiagnosed "piston never extends" (an artifact of GameTestSequence's own same-tick
 *    thenExecute failure-overwriting, not a real engine bug — see issue #34's history).
 * 2. Once #42/#43 landed, the piston DID extend correctly, but the cross-boundary landing still
 *    failed — turned out to be empty_platform's own stone extending 2 blocks past the subgrid's
 *    edge before hitting the room's barrier wall, confirmed live via a pristine (pre-test)
 *    real-block-state check. There was never actually open air here to push into; not an engine
 *    bug. Fixed by explicitly clearing that one real-world cell instead of reshaping the shared
 *    structure (used by several other tests that never look past the subgrid's own edge).
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridCrossBoundaryGameTest {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String PLATFORM = "empty_platform";

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void pistonPushAcrossSubgridBoundaryCreatesTheNeighborAndLandsThePiece(GameTestHelper helper) {
        BlockPos subgridPos = new BlockPos(2, 1, 2);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, subgridPos);
        ServerLevel level = helper.getLevel();

        int max = be.getGridSize() - 1; // 7 for the default 8x8x8 grid
        BlockPos pistonAnchor = new BlockPos(max - 1, 0, 4);
        BlockPos pushedAnchor = new BlockPos(max, 0, 4); // right at the edge — gets pushed OFF the grid
        BlockPos powerAnchor = pistonAnchor.relative(Direction.WEST);

        SubgridTestSupport.placePiece(be, level, pistonAnchor,
                Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        SubgridTestSupport.placePiece(be, level, pushedAnchor, Blocks.OAK_LOG.defaultBlockState());

        BlockPos neighborSubgridPos = subgridPos.relative(Direction.EAST);
        // empty_platform's own stone extends 2 blocks past the subgrid's edge before hitting the
        // room's barrier wall — confirmed live, not actually open air anywhere in this direction
        // within the structure's bounds. Every other test sharing this platform is fine with that
        // (they never look past the subgrid's own edge); only a cross-boundary test cares, so
        // clear this one cell explicitly rather than reshaping the shared structure.
        helper.setBlock(neighborSubgridPos, Blocks.AIR.defaultBlockState());
        LOGGER.info("[XTEST-crossboundary] subgrid at {} (abs {}), piston {} pushed {} power {} expecting neighbor at abs {}",
                be.getBlockPos(), helper.absolutePos(subgridPos), pistonAnchor, pushedAnchor, powerAnchor, helper.absolutePos(neighborSubgridPos));

        helper.startSequence()
                .thenExecute(() -> SubgridTestSupport.placePiece(be, level, powerAnchor, Blocks.REDSTONE_BLOCK.defaultBlockState()))
                .thenIdle(20)
                .thenExecute(() -> {
                    // A normal (non-sticky) piston's HEAD legitimately occupies the pushed block's
                    // old cell once extended — the oak_log itself moves one further, to the
                    // neighboring subgrid checked below. Pins down that the piston circuit itself
                    // (power -> extend -> head lands) works, so any remaining failure is isolated
                    // to the cross-boundary landing, not the push mechanics.
                    var pistonState = SubgridTestSupport.stateOf(be, pistonAnchor);
                    var pushedCellState = SubgridTestSupport.stateOf(be, pushedAnchor);
                    LOGGER.info("[XTEST-crossboundary] after idle: pistonState={} pushedCellState={}",
                            pistonState, pushedCellState);
                    helper.assertTrue(pistonState != null && pistonState.getValue(PistonBaseBlock.EXTENDED),
                            "piston never extended at all (state=" + pistonState + ")");
                    helper.assertTrue(pushedCellState != null && pushedCellState.is(Blocks.PISTON_HEAD),
                            "the piston head never took over the pushed cell (state=" + pushedCellState + ")");
                })
                .thenExecute(() -> {
                    BlockPos abs = helper.absolutePos(neighborSubgridPos);
                    var neighborBE = level.getBlockEntity(abs);
                    LOGGER.info("[XTEST-crossboundary] checking abs {} for neighbor SubgridBlockEntity: found={} realBlockState={}",
                            abs, neighborBE, level.getBlockState(abs));
                    if (!(neighborBE instanceof SubgridBlockEntity neighbor)) {
                        helper.fail("no SubgridBlockEntity was created across the boundary", neighborSubgridPos);
                        return;
                    }
                    PlacedPiece landed = neighbor.getPieceAt(0, 0, 4);
                    helper.assertTrue(landed != null, "the pushed block never landed in the new neighboring subgrid");
                })
                .thenSucceed();
    }

    /**
     * Live-reported follow-up: with a pre-existing block already occupying the neighbor cell a
     * pushed block would land on, the piston extended but the pushed block seemed to vanish.
     * Root cause: wrap() collapsed ANY overflow magnitude to the boundary (0 or max) instead of
     * the correct wrapped cell — so the oak_log's landing (origin-relative position 8) and the
     * pre-existing stone's own chain-pushed destination (position 9, one grid-width further)
     * both wrapped onto the SAME neighbor cell (0,0,4) instead of two different ones (0,0,4 and
     * 1,0,4), and the second write silently clobbered the first. Fixed in wrap() itself (see its
     * doc) — this is a real chain push, vanilla correctly pushes the stone one further to make
     * room, exactly like pushing two blocks in a row anywhere else.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void pistonPushIntoAnAlreadyOccupiedNeighborCell(GameTestHelper helper) {
        BlockPos subgridPos = new BlockPos(2, 1, 2);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, subgridPos);
        ServerLevel level = helper.getLevel();

        int max = be.getGridSize() - 1;
        BlockPos pistonAnchor = new BlockPos(max - 1, 0, 4);
        BlockPos pushedAnchor = new BlockPos(max, 0, 4);
        BlockPos powerAnchor = pistonAnchor.relative(Direction.WEST);

        SubgridTestSupport.placePiece(be, level, pistonAnchor,
                Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        SubgridTestSupport.placePiece(be, level, pushedAnchor, Blocks.OAK_LOG.defaultBlockState());

        // Pre-existing neighbor, already occupied at the exact cell the pushed block would land on.
        BlockPos neighborSubgridPos = subgridPos.relative(Direction.EAST);
        helper.setBlock(neighborSubgridPos, Blocks.AIR.defaultBlockState());
        SubgridBlockEntity neighbor = SubgridTestSupport.placeSubgrid(helper, neighborSubgridPos);
        SubgridTestSupport.placePiece(neighbor, level, new BlockPos(0, 0, 4), Blocks.STONE.defaultBlockState());

        LOGGER.info("[XTEST-crossboundary-occupied] origin {} (abs {}), neighbor {} (abs {}) pre-occupied at (0,0,4)",
                be.getBlockPos(), helper.absolutePos(subgridPos), neighbor.getBlockPos(), helper.absolutePos(neighborSubgridPos));

        helper.startSequence()
                .thenExecute(() -> SubgridTestSupport.placePiece(be, level, powerAnchor, Blocks.REDSTONE_BLOCK.defaultBlockState()))
                .thenIdle(20)
                .thenExecute(() -> {
                    var pistonState = SubgridTestSupport.stateOf(be, pistonAnchor);
                    var pushedCellState = SubgridTestSupport.stateOf(be, pushedAnchor);
                    var landedCellState = SubgridTestSupport.stateOf(neighbor, new BlockPos(0, 0, 4));
                    var chainedCellState = SubgridTestSupport.stateOf(neighbor, new BlockPos(1, 0, 4));
                    LOGGER.info("[XTEST-crossboundary-occupied] after idle: pistonState={} pushedCellState={} landedCellState={} chainedCellState={}",
                            pistonState, pushedCellState, landedCellState, chainedCellState);
                    helper.assertTrue(pistonState != null && pistonState.getValue(PistonBaseBlock.EXTENDED),
                            "piston never extended at all (state=" + pistonState + ")");
                    helper.assertTrue(pushedCellState != null && pushedCellState.is(Blocks.PISTON_HEAD),
                            "the piston head never took over the pushed cell (state=" + pushedCellState + ")");
                    helper.assertTrue(landedCellState != null && landedCellState.is(Blocks.OAK_LOG),
                            "the pushed oak_log never landed in the neighbor's cell (state=" + landedCellState + ")");
                    helper.assertTrue(chainedCellState != null && chainedCellState.is(Blocks.STONE),
                            "the pre-existing stone was never chain-pushed one further (state=" + chainedCellState + ")");
                })
                .thenSucceed();
    }
}
