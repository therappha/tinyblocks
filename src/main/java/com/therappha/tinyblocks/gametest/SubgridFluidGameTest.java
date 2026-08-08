package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Regression coverage for the fluid fix (PR #31, closes #20-era water-never-flowed bugs): a
 * water source piece placed inside a subgrid should actually spread into its neighboring cells
 * via the real scheduled-fluid-tick path, not just sit there as a static, non-flowing texture.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridFluidGameTest {

    private static final String PLATFORM = "empty_platform";

    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public void waterSourceSpreadsIntoNeighboringCells(GameTestHelper helper) {
        BlockPos subgridPos = new BlockPos(2, 1, 2);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, subgridPos);
        ServerLevel level = helper.getLevel();

        // Placed on a solid floor cell so the water source has something to sit on and several
        // empty neighbors (east/west/south/north) to spread into — mirrors a water source placed
        // against the subgrid's own real floor rather than floating with no support.
        BlockPos floorAnchor = new BlockPos(4, 0, 4);
        BlockPos sourceAnchor = floorAnchor.above();
        SubgridTestSupport.placePiece(be, level, floorAnchor, Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(be, level, sourceAnchor, Blocks.WATER.defaultBlockState());

        helper.startSequence()
                .thenIdle(20) // several fluid-tick cycles — plenty for at least one spread step
                .thenExecute(() -> {
                    boolean anyNeighborWet = false;
                    for (Direction dir : new Direction[] {Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
                        var neighborState = SubgridTestSupport.stateOf(be, sourceAnchor.relative(dir));
                        if (neighborState != null && !neighborState.getFluidState().isEmpty()) {
                            anyNeighborWet = true;
                            break;
                        }
                    }
                    helper.assertTrue(anyNeighborWet, "water source never spread into any neighboring cell");
                })
                .thenSucceed();
    }

    /**
     * Regression coverage for PR #31's actual scenario, not just spreading within one grid: a
     * water source right at a subgrid's own edge must be able to spread PAST that edge, across
     * the real-world boundary, into a brand new adjacent SubgridBlockEntity — same
     * placePieceCrossBoundary mechanism SubgridCrossBoundaryGameTest exercises for a pushed
     * piston block, just reached via the fluid-tick path instead.
     *
     * Previously required = false, believed to share #34's root cause. CORRECTED: not an engine
     * bug at all — empty_platform's own stone extends 2 blocks past the subgrid's edge before
     * hitting the room's barrier wall, confirmed live via a pristine (pre-test) real-block-state
     * check, so there was never actually open air here for water to spread into. Fixed by
     * explicitly clearing that one real-world cell instead of reshaping the shared structure.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void waterSourceAtTheEdgeSpreadsIntoAnAdjacentSubgrid(GameTestHelper helper) {
        BlockPos subgridPos = new BlockPos(2, 1, 2);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, subgridPos);
        ServerLevel level = helper.getLevel();

        int max = be.getGridSize() - 1;
        BlockPos edgeAnchor = new BlockPos(max, 0, 4);

        BlockPos neighborSubgridPos = subgridPos.relative(Direction.EAST);
        helper.setBlock(neighborSubgridPos, Blocks.AIR.defaultBlockState());

        SubgridTestSupport.placePiece(be, level, edgeAnchor, Blocks.WATER.defaultBlockState());

        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    if (!(level.getBlockEntity(helper.absolutePos(neighborSubgridPos)) instanceof SubgridBlockEntity neighbor)) {
                        helper.fail("water never crossed into a new adjacent subgrid", neighborSubgridPos);
                        return;
                    }
                    var landedState = SubgridTestSupport.stateOf(neighbor, new BlockPos(0, 0, 4));
                    helper.assertTrue(landedState != null && !landedState.getFluidState().isEmpty(),
                            "the neighboring subgrid exists but has no water piece where the flow should have landed");
                })
                .thenSucceed();
    }
}
