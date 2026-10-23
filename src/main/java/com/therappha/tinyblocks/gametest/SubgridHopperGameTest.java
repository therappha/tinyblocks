package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Regression coverage for issue #23: a hopper piece feeding a chest piece directly below it,
 * both inside the same subgrid. tick()'s own comment on this exact scenario ("Getting hopper-
 * to-chest-within-one-subgrid working needs a second, local-anchor-based construction") flags it
 * as a known accepted gap rather than a confirmed fix, despite #23 being closed — this test is
 * what actually settles which one is true.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridHopperGameTest {

    private static final String PLATFORM = "empty_platform";

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void hopperFeedsAnItemIntoTheChestBelowIt(GameTestHelper helper) {
        BlockPos subgridPos = new BlockPos(2, 1, 2);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, subgridPos);
        ServerLevel level = helper.getLevel();

        BlockPos chestAnchor = new BlockPos(4, 0, 4);
        BlockPos hopperAnchor = chestAnchor.above();

        SubgridTestSupport.placePiece(be, level, chestAnchor, Blocks.CHEST.defaultBlockState());
        PlacedPiece hopperPiece = SubgridTestSupport.placePiece(be, level, hopperAnchor,
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));

        helper.startSequence()
                .thenExecute(() -> {
                    BlockEntity hopperBE = SubgridTestSupport.blockEntityOf(be, hopperPiece, level);
                    helper.assertTrue(hopperBE instanceof Container, "hopper piece has no real Container BlockEntity");
                    ((Container) hopperBE).setItem(0, new ItemStack(Items.APPLE));
                })
                .thenIdle(30) // hopper transfer has an 8-tick cooldown; plenty of margin
                .thenExecute(() -> {
                    PlacedPiece chestPiece = be.getPieceAt(chestAnchor.getX(), chestAnchor.getY(), chestAnchor.getZ());
                    helper.assertTrue(chestPiece != null, "chest piece disappeared");
                    BlockEntity chestBE = SubgridTestSupport.blockEntityOf(be, chestPiece, level);
                    helper.assertTrue(chestBE instanceof Container container && !container.isEmpty(),
                            "chest never received the item from the hopper above it");
                })
                .thenSucceed();
    }

    /**
     * Issue #55: the hopper sits on the bottom layer of its subgrid and points DOWN at a chest
     * piece on the top layer of the subgrid directly beneath it. They should behave like two
     * ordinary vertical neighbors, without knowing they live in different SubgridBlocks.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void hopperFeedsAChestPieceInTheSubgridBelow(GameTestHelper helper) {
        SubgridBlockEntity lower = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        SubgridBlockEntity upper = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 2, 2));
        ServerLevel level = helper.getLevel();
        int top = lower.getGridSize() - 1;

        BlockPos chestAnchor = new BlockPos(4, top, 4);
        BlockPos hopperAnchor = new BlockPos(4, 0, 4);

        SubgridTestSupport.placePiece(lower, level, chestAnchor, Blocks.CHEST.defaultBlockState());
        PlacedPiece hopperPiece = SubgridTestSupport.placePiece(upper, level, hopperAnchor,
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));

        helper.startSequence()
                .thenExecute(() -> {
                    BlockEntity hopperBE = SubgridTestSupport.blockEntityOf(upper, hopperPiece, level);
                    helper.assertTrue(hopperBE instanceof Container, "hopper piece has no real Container BlockEntity");
                    ((Container) hopperBE).setItem(0, new ItemStack(Items.APPLE));
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    PlacedPiece chestPiece = lower.getPieceAt(chestAnchor.getX(), chestAnchor.getY(), chestAnchor.getZ());
                    helper.assertTrue(chestPiece != null, "chest piece disappeared");
                    BlockEntity chestBE = SubgridTestSupport.blockEntityOf(lower, chestPiece, level);
                    helper.assertTrue(chestBE instanceof Container container && !container.isEmpty(),
                            "chest in the subgrid below never received the item from the hopper above it");
                })
                .thenSucceed();
    }

    /** Issue #55: same hopper, but the container below is a REAL chest block, not a subgrid piece. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void hopperFeedsARealChestBelowTheSubgrid(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(2, 1, 2);
        helper.setBlock(chestPos, Blocks.CHEST);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, chestPos.above());
        ServerLevel level = helper.getLevel();

        PlacedPiece hopperPiece = SubgridTestSupport.placePiece(be, level, new BlockPos(4, 0, 4),
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));

        helper.startSequence()
                .thenExecute(() -> {
                    BlockEntity hopperBE = SubgridTestSupport.blockEntityOf(be, hopperPiece, level);
                    helper.assertTrue(hopperBE instanceof Container, "hopper piece has no real Container BlockEntity");
                    ((Container) hopperBE).setItem(0, new ItemStack(Items.APPLE));
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    BlockEntity chestBE = level.getBlockEntity(helper.absolutePos(chestPos));
                    helper.assertTrue(chestBE instanceof Container container && !container.isEmpty(),
                            "real chest below the subgrid never received the item from the hopper piece");
                })
                .thenSucceed();
    }

    /** Issue #55: hopper -> hopper across the boundary (the receiving side is itself a ticking hopper piece). */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void hopperFeedsAHopperPieceInTheSubgridBelow(GameTestHelper helper) {
        SubgridBlockEntity lower = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        SubgridBlockEntity upper = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 2, 2));
        ServerLevel level = helper.getLevel();
        int top = lower.getGridSize() - 1;

        BlockPos lowerAnchor = new BlockPos(4, top, 4);
        BlockPos upperAnchor = new BlockPos(4, 0, 4);
        PlacedPiece lowerHopper = SubgridTestSupport.placePiece(lower, level, lowerAnchor,
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        PlacedPiece upperHopper = SubgridTestSupport.placePiece(upper, level, upperAnchor,
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));

        helper.startSequence()
                .thenExecute(() -> {
                    BlockEntity hopperBE = SubgridTestSupport.blockEntityOf(upper, upperHopper, level);
                    helper.assertTrue(hopperBE instanceof Container, "upper hopper piece has no Container BlockEntity");
                    ((Container) hopperBE).setItem(0, new ItemStack(Items.APPLE));
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    BlockEntity upperBE = upperHopper.runtimeBlockEntity instanceof BlockEntity b ? b : null;
                    BlockEntity lowerBE = lowerHopper.runtimeBlockEntity instanceof BlockEntity b ? b : null;
                    helper.assertTrue(upperBE instanceof Container up && up.isEmpty(),
                            "upper hopper still holds the item: it never passed it down");
                    helper.assertTrue(lowerBE instanceof Container low && !low.isEmpty(),
                            "hopper piece in the subgrid below never received the item");
                })
                .thenSucceed();
    }
}
