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
}
