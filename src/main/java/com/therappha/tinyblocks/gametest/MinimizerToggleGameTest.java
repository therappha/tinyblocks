package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A Minimizer switched off is as good as not held: a locked one stops guarding what can be broken. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class MinimizerToggleGameTest {

    @GameTest(template = "empty_platform")
    public void aMinimizerSwitchedOffStopsActing(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack stack = new ItemStack(Registration.MINIMIZER.get());
        player.setItemInHand(InteractionHand.OFF_HAND, stack);
        helper.assertTrue(MinimizerItem.isOn(stack), "a new Minimizer is off");
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlock(at, Blocks.STONE.defaultBlockState(), 3);
        MinimizerItem.setLocked(stack, true);
        helper.assertTrue(!MinimizerItem.mayBreak(player, helper.getLevel(), at), "locked, a normal block shouldn't break");
        MinimizerItem.toggleOn(player);
        helper.assertTrue(!MinimizerItem.isOn(stack), "still on after the key");
        helper.assertTrue(MinimizerItem.mayBreak(player, helper.getLevel(), at), "switched off, its lock still guards");
        MinimizerItem.toggleOn(player);
        helper.assertTrue(MinimizerItem.isOn(stack), "still off after the key again");
        helper.assertTrue(!new ItemStack(Blocks.STONE).isEmpty() && !MinimizerItem.isOn(new ItemStack(Blocks.STONE)), "stone counts as a Minimizer");
        helper.succeed();
    }

    /** Off, a block clicked against a grid is a whole block, past the grids in the way, and no piece goes in them. */
    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void aMinimizerSwitchedOffPlacesWholeBlocksAgainstGrids(GameTestHelper helper) {
        BlockPos low = new BlockPos(2, 2, 2), high = low.above();
        var lowGrid = SubgridTestSupport.placeSubgrid(helper, low);
        var highGrid = SubgridTestSupport.placeSubgrid(helper, high);
        SubgridTestSupport.placePiece(lowGrid, helper.getLevel(), new BlockPos(3, 7, 3), Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(highGrid, helper.getLevel(), new BlockPos(3, 0, 3), Blocks.STONE.defaultBlockState());
        var player = SubgridTestSupport.serverPlayer(helper, lowGrid);
        ItemStack minimizer = new ItemStack(Registration.MINIMIZER.get());
        player.setItemInHand(InteractionHand.OFF_HAND, minimizer);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.OAK_PLANKS, 4));
        MinimizerItem.toggleOn(player);
        SubgridTestSupport.rightClick(lowGrid, player, SubgridTestSupport.topHit(lowGrid, new BlockPos(3, 7, 3)));
        helper.assertBlockPresent(Blocks.OAK_PLANKS, high.above());
        helper.assertTrue(lowGrid.getPieces().size() == 1 && highGrid.getPieces().size() == 1, "a piece went into a grid");
        helper.succeed();
    }
}
