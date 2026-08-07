package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Live-in-a-real-world regression coverage for #37: a sticky piston pushing/pulling a block
 * placed as a piece INSIDE a subgrid, the exact scenario the coordinate-space and cross-tick
 * drop-duplication bugs fixed this session were only ever caught in by actually playing the game
 * — a unit test can't exercise onNeighborChanged/tick/applyChanges' real multi-tick cascade at
 * all (see VanillaBlockPieceTest's own scope note).
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false) // reuse the one shared platform structure across every test method, not one per method
public class SubgridPistonGameTest {

    private static final String PLATFORM = "empty_platform";

    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public void stickyPistonRetractDoesNotDuplicateThePulledBlock(GameTestHelper helper) {
        BlockPos subgridPos = new BlockPos(2, 1, 2);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, subgridPos);
        ServerLevel level = helper.getLevel();

        BlockPos pistonAnchor = new BlockPos(4, 0, 4);
        BlockPos blockAnchor = pistonAnchor.above();
        BlockPos powerAnchor = pistonAnchor.relative(Direction.WEST);

        SubgridTestSupport.placePiece(be, level, pistonAnchor,
                Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP));
        SubgridTestSupport.placePiece(be, level, blockAnchor, Blocks.OAK_LOG.defaultBlockState());

        helper.startSequence()
                // A redstone block piece adjacent to the piston is the simplest possible power
                // source (always on, no attachment-face semantics to get wrong like a lever would)
                // — placing it triggers the piston's own onNeighborChanged, exactly like a real
                // redstone signal change would.
                .thenExecute(() -> SubgridTestSupport.placePiece(be, level, powerAnchor, Blocks.REDSTONE_BLOCK.defaultBlockState()))
                .thenIdle(20) // extend fully finishes (animation + finalize) well within this
                .thenExecute(() -> be.removePieceAt(powerAnchor.getX(), powerAnchor.getY(), powerAnchor.getZ()))
                .thenIdle(30) // retract + the pending-drop queue settling (see SubgridBlockEntity#activeCaptures)
                .thenExecute(() -> {
                    helper.assertTrue(be.pendingDropCount() == 0, "a pending drop is still queued after settling");
                    helper.assertItemEntityNotPresent(Items.OAK_LOG);
                    helper.assertTrue(SubgridTestSupport.stateOf(be, blockAnchor) != null
                                    || SubgridTestSupport.stateOf(be, pistonAnchor.relative(Direction.UP)) != null,
                            "the pulled block should still exist as a piece somewhere, not just vanished");
                })
                .thenSucceed();
    }
}
