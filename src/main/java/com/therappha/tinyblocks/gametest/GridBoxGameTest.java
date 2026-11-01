package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.GridBoxBlockEntity;
import com.therappha.tinyblocks.items.GridSaveItem;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A full Grid Save puts a closed box down, opened with a right click. The grid inside is never lost. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class GridBoxGameTest {

    private static final BlockPos FROM = new BlockPos(1, 2, 1), BOX = new BlockPos(3, 2, 3);

    private static ItemStack saved(GameTestHelper helper, ServerPlayer player) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, FROM);
        SubgridTestSupport.placePiece(be, helper.getLevel(), BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        ItemStack save = new ItemStack(Registration.GRID_SAVE.get());
        helper.assertTrue(GridSaveItem.pickUp(helper.getLevel(), helper.absolutePos(FROM), save, player), "nothing taken");
        return save;
    }

    private static void open(GameTestHelper helper, ServerPlayer player) {
        BlockPos at = helper.absolutePos(BOX);
        helper.getLevel().getBlockState(at).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    @GameTest(template = "empty_platform")
    public void aSavedGridGoesDownAsABoxAndOpensIntoTheGrid(GameTestHelper helper) {
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, SubgridTestSupport.placeSubgrid(helper, new BlockPos(0, 2, 4)));
        ItemStack save = saved(helper, player);
        BlockPos at = helper.absolutePos(BOX);
        helper.assertTrue(GridSaveItem.pack(helper.getLevel(), at, Direction.UP, save, player), "no box put down");
        helper.assertBlockPresent(Registration.GRID_BOX.get(), BOX);
        helper.assertTrue(!GridSaveItem.isFull(save), "the item still holds the grid");
        open(helper, player);
        helper.assertTrue(helper.getLevel().getBlockEntity(at) instanceof SubgridBlockEntity grid && grid.getPieces().size() == 1,
                "no grid where the box was");
        helper.succeed();
    }

    @GameTest(template = "empty_platform")
    public void aBoxBrokenGivesTheSavedGridBack(GameTestHelper helper) {
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, SubgridTestSupport.placeSubgrid(helper, new BlockPos(0, 2, 4)));
        ItemStack save = saved(helper, player);
        BlockPos at = helper.absolutePos(BOX);
        helper.assertTrue(GridSaveItem.pack(helper.getLevel(), at, Direction.UP, save, player), "no box put down");
        helper.getLevel().destroyBlock(at, true);
        helper.startSequence().thenIdle(1).thenExecute(() -> {
            var drops = helper.getLevel().getEntities(EntityType.ITEM, new net.minecraft.world.phys.AABB(at).inflate(2), e -> true);
            helper.assertTrue(drops.stream().map(ItemEntity::getItem).anyMatch(GridSaveItem::isFull), "the grid was lost: " + drops);
        }).thenSucceed();
    }

    /** Two grids: the spot of the second is taken after the box is down, and the box stays closed, holding both. */
    @GameTest(template = "empty_platform")
    public void aBoxThatDoesntFitStaysClosed(GameTestHelper helper) {
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, SubgridTestSupport.placeSubgrid(helper, new BlockPos(0, 2, 4)));
        BlockPos second = FROM.east();
        SubgridBlockEntity a = SubgridTestSupport.placeSubgrid(helper, FROM), b = SubgridTestSupport.placeSubgrid(helper, second);
        SubgridTestSupport.placePiece(a, helper.getLevel(), BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(b, helper.getLevel(), BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        ItemStack save = new ItemStack(Registration.GRID_SAVE.get());
        helper.assertTrue(GridSaveItem.pickUpBox(helper.getLevel(), helper.absolutePos(FROM), helper.absolutePos(second), save, player), "nothing taken");
        BlockPos at = helper.absolutePos(BOX);
        helper.assertTrue(GridSaveItem.pack(helper.getLevel(), at, Direction.UP, save, player), "no box put down");
        for (Direction side : Direction.Plane.HORIZONTAL) helper.setBlock(BOX.relative(side), Blocks.STONE);
        open(helper, player);
        helper.assertBlockPresent(Registration.GRID_BOX.get(), BOX);
        helper.assertTrue(helper.getLevel().getBlockEntity(at) instanceof GridBoxBlockEntity box && GridSaveItem.isFull(box.held()), "the box lost the grids");
        helper.succeed();
    }
}
