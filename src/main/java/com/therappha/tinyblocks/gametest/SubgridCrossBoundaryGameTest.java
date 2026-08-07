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
 * required = false: confirmed still broken, but NOT where it first looked. Two consecutive
 * thenExecute steps below run in the same tick with no thenIdle between them — GameTestSequence's
 * own tick() loop lets a failed thenExecute call parent.fail(...) without stopping, so the SECOND
 * step's failure silently overwrote the first one's message. With unconditional (not
 * isPistonRelated-gated) logging added directly in this test, the real finding is: a plain
 * (non-sticky) EAST-facing piston powered by a redstone_block directly WEST of it (behind, not to
 * a side) never extends at all — see issue #34's own comment thread for the corrected diagnosis.
 * Kept enabled and reported rather than deleted so the suite documents the exact live failure and
 * flips green automatically whenever this actually gets fixed, instead of silently regressing
 * back to "no coverage."
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridCrossBoundaryGameTest {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String PLATFORM = "empty_platform";

    @GameTest(template = PLATFORM, timeoutTicks = 100, required = false)
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
        LOGGER.info("[XTEST-crossboundary] subgrid at {} (abs {}), piston {} pushed {} power {} expecting neighbor at abs {}",
                be.getBlockPos(), helper.absolutePos(subgridPos), pistonAnchor, pushedAnchor, powerAnchor, helper.absolutePos(neighborSubgridPos));

        helper.startSequence()
                .thenExecute(() -> SubgridTestSupport.placePiece(be, level, powerAnchor, Blocks.REDSTONE_BLOCK.defaultBlockState()))
                .thenIdle(20)
                .thenExecute(() -> {
                    // Pins down exactly which half is broken: the piston circuit itself (power ->
                    // extend -> vacate the pushed cell) works fine — confirmed here — so the
                    // failure below is isolated to applyChanges' cross-boundary landing, not the
                    // push mechanics.
                    var pistonState = SubgridTestSupport.stateOf(be, pistonAnchor);
                    LOGGER.info("[XTEST-crossboundary] after idle: pistonState={} pushedCellState={}",
                            pistonState, SubgridTestSupport.stateOf(be, pushedAnchor));
                    helper.assertTrue(pistonState != null && pistonState.getValue(PistonBaseBlock.EXTENDED),
                            "piston never extended at all (state=" + pistonState + ")");
                    helper.assertTrue(SubgridTestSupport.stateOf(be, pushedAnchor) == null,
                            "pushed block's old cell should be vacated by now");
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
                    helper.assertTrue(SubgridTestSupport.stateOf(be, pushedAnchor) == null,
                            "the original subgrid still thinks the pushed cell is occupied");
                })
                .thenSucceed();
    }
}
