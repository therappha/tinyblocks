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

    /** Two grids side by side: a box two blocks long. A click on its far end opens it, a break on it gives one Grid Save. */
    @GameTest(template = "empty_platform")
    public void aBoxIsAsBigAsWhatItHolds(GameTestHelper helper) {
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, SubgridTestSupport.placeSubgrid(helper, new BlockPos(0, 2, 4)));
        BlockPos second = FROM.east();
        SubgridBlockEntity a = SubgridTestSupport.placeSubgrid(helper, FROM), b = SubgridTestSupport.placeSubgrid(helper, second);
        SubgridTestSupport.placePiece(a, helper.getLevel(), BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(b, helper.getLevel(), BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        ItemStack save = new ItemStack(Registration.GRID_SAVE.get());
        helper.assertTrue(GridSaveItem.pickUpBox(helper.getLevel(), helper.absolutePos(FROM), helper.absolutePos(second), save, player), "nothing taken");
        BlockPos at = helper.absolutePos(BOX);
        helper.assertTrue(GridSaveItem.pack(helper.getLevel(), at, Direction.UP, save, player), "no box put down");
        int boxes = 0;
        for (BlockPos cell : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(2, 2, 2))) {
            if (helper.getLevel().getBlockState(cell).is(Registration.GRID_BOX.get())) boxes++;
        }
        helper.assertTrue(boxes == 2, "a box of two grids takes " + boxes + " blocks");
        BlockPos far = null;
        for (BlockPos cell : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(2, 2, 2))) {
            if (helper.getLevel().getBlockEntity(cell) instanceof GridBoxBlockEntity box && !box.isMain()) far = cell.immutable();
        }
        helper.assertTrue(far != null, "no second block in the box");
        helper.getLevel().getBlockState(far).useWithoutItem(helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(far), Direction.UP, far, false));
        int grids = 0;
        for (BlockPos cell : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(2, 2, 2))) {
            if (helper.getLevel().getBlockEntity(cell) instanceof SubgridBlockEntity) grids++;
            helper.assertTrue(!helper.getLevel().getBlockState(cell).is(Registration.GRID_BOX.get()), "part of the box stayed");
        }
        helper.assertTrue(grids == 2, "opened into " + grids + " grids");
        helper.succeed();
    }

    /** Broken at any block, one Grid Save comes back, not one per block. */
    @GameTest(template = "empty_platform")
    public void aBigBoxBrokenGivesOneGridSave(GameTestHelper helper) {
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, SubgridTestSupport.placeSubgrid(helper, new BlockPos(0, 2, 4)));
        BlockPos second = FROM.east();
        SubgridBlockEntity a = SubgridTestSupport.placeSubgrid(helper, FROM), b = SubgridTestSupport.placeSubgrid(helper, second);
        SubgridTestSupport.placePiece(a, helper.getLevel(), BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(b, helper.getLevel(), BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        ItemStack save = new ItemStack(Registration.GRID_SAVE.get());
        helper.assertTrue(GridSaveItem.pickUpBox(helper.getLevel(), helper.absolutePos(FROM), helper.absolutePos(second), save, player), "nothing taken");
        BlockPos at = helper.absolutePos(BOX);
        helper.assertTrue(GridSaveItem.pack(helper.getLevel(), at, Direction.UP, save, player), "no box put down");
        BlockPos part = null;
        for (BlockPos cell : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(2, 2, 2))) {
            if (helper.getLevel().getBlockEntity(cell) instanceof GridBoxBlockEntity box && !box.isMain()) part = cell.immutable();
        }
        helper.getLevel().destroyBlock(part, true);
        helper.startSequence().thenIdle(1).thenExecute(() -> {
            var drops = helper.getLevel().getEntities(EntityType.ITEM, new net.minecraft.world.phys.AABB(at).inflate(4), e -> GridSaveItem.isFull(e.getItem()));
            helper.assertTrue(drops.size() == 1, drops.size() + " Grid Saves came back");
        }).thenSucceed();
    }
}
