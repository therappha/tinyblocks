package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.GridResize;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A grid turned to another size looks the same, or is left alone with a reason; the Minimizer's size steps along. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class GridResizeGameTest {

    private static final BlockPos AT = new BlockPos(2, 2, 2);

    private static SubgridBlockEntity grid(GameTestHelper helper, int size) {
        helper.setBlock(AT, Blocks.AIR);
        SubgridBlockEntity be = SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(AT), size);
        if (be == null) throw new IllegalStateException("no grid");
        return be;
    }

    private static SubgridBlockEntity at(GameTestHelper helper) {
        return (SubgridBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(AT));
    }

    @GameTest(template = "empty_platform")
    public void aGridGoesToAFinerSizeAndBack(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 8);
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(3, 1, 2), Blocks.STONE.defaultBlockState());
        helper.assertTrue(GridResize.resize(helper.getLevel(), be, 16).done(), "not resized to 1/16");
        SubgridBlockEntity fine = at(helper);
        helper.assertTrue(fine.getGridSize() == 16 && fine.getPieces().size() == 8, "1/16 holds " + fine.getPieces().size() + " pieces");
        for (BlockPos cell : BlockPos.betweenClosed(6, 2, 4, 7, 3, 5)) {
            helper.assertTrue(Blocks.STONE.defaultBlockState().equals(SubgridTestSupport.stateOf(fine, cell)), "no stone at " + cell);
        }
        helper.assertTrue(GridResize.resize(helper.getLevel(), fine, 8).done(), "not resized back");
        SubgridBlockEntity back = at(helper);
        helper.assertTrue(back.getGridSize() == 8 && back.getPieces().size() == 1, "back holds " + back.getPieces().size() + " pieces");
        helper.assertTrue(Blocks.STONE.defaultBlockState().equals(SubgridTestSupport.stateOf(back, new BlockPos(3, 1, 2))), "the stone moved");
        helper.succeed();
    }

    @GameTest(template = "empty_platform")
    public void whatCantBeResizedIsLeftAlone(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 8);
        SubgridTestSupport.placePiece(be, helper.getLevel(), BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        helper.assertTrue(!GridResize.resize(helper.getLevel(), be, 7).done(), "1/8 to 1/7 don't line up");
        helper.assertTrue(!GridResize.resize(helper.getLevel(), be, 8).done(), "already that size");
        helper.assertTrue(!GridResize.resize(helper.getLevel(), be, 4).done(), "a part of a coarser cell can't go alone");
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(4, 0, 0), Blocks.CHEST.defaultBlockState());
        helper.assertTrue(!GridResize.resize(helper.getLevel(), be, 16).done(), "a chest can't be split");
        helper.assertTrue(at(helper) == be && be.getPieces().size() == 2, "a refused resize changed the grid");
        helper.succeed();
    }

    @GameTest(template = "empty_platform")
    public void theSizeStepsAndStopsAtTheEnds(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Registration.MINIMIZER.get());
        MinimizerItem item = Registration.MINIMIZER.get();
        MinimizerItem.setGridSize(stack, 8);
        item.stepGridSize(stack, 1);
        helper.assertTrue(item.gridSize(stack) == 9, "8 + 1 is " + item.gridSize(stack));
        MinimizerItem.setGridSize(stack, Registration.MAX_GRID_SIZE);
        item.stepGridSize(stack, 1);
        helper.assertTrue(item.gridSize(stack) == Registration.MAX_GRID_SIZE, "went past the largest");
        MinimizerItem.setGridSize(stack, MinimizerItem.NORMAL_SIZE);
        item.stepGridSize(stack, -1);
        helper.assertTrue(item.gridSize(stack) == MinimizerItem.NORMAL_SIZE, "went past the smallest");
        helper.succeed();
    }
}
