package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A block placed against a piece takes its facing from the clicked face and the spot hit, as it does full size. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridFacingPlacementGameTest {

    private static final BlockPos STONE = new BlockPos(4, 4, 4);

    /**
     * A player looking steeply down, as one does at tiny blocks: vanilla's own fallback order (the player's look) puts
     * the ground first, so only the clicked face can make a torch or lever go on the wall.
     */
    private static Player holding(GameTestHelper helper, ItemStack stack) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setXRot(80);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    /** On the east face of {@code cell}, at height {@code y} (0 bottom, 1 top) within it. */
    private static BlockHitResult eastFaceAt(SubgridBlockEntity be, BlockPos cell, double y) {
        Vec3 point = new Vec3(cell.getX() + 1, cell.getY() + y, cell.getZ() + 0.5).scale(1.0 / be.getGridSize())
                .add(Vec3.atLowerCornerOf(be.getBlockPos()));
        return new BlockHitResult(point, Direction.EAST, be.getBlockPos(), false);
    }

    /** A stone piece with another under the cell east of it, so a standing torch there would survive too. */
    private static SubgridBlockEntity stoneWithFloorBeside(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        SubgridTestSupport.placePiece(be, helper.getLevel(), STONE, Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(be, helper.getLevel(), STONE.east().below(), Blocks.STONE.defaultBlockState());
        return be;
    }

    @GameTest(template = "empty_platform")
    public void aTorchOnASideGoesOnTheWall(GameTestHelper helper) {
        SubgridBlockEntity be = stoneWithFloorBeside(helper);
        SubgridTestSupport.rightClick(be, holding(helper, new ItemStack(Items.TORCH)), eastFaceAt(be, STONE, 0.5));

        BlockState torch = SubgridTestSupport.stateOf(be, STONE.east());
        helper.assertTrue(torch != null && torch.is(Blocks.WALL_TORCH)
                        && torch.getValue(WallTorchBlock.FACING) == Direction.EAST,
                "no wall torch facing east on the stone's side: " + torch);
        helper.succeed();
    }

    @GameTest(template = "empty_platform")
    public void aLeverOnASideGoesOnTheWall(GameTestHelper helper) {
        SubgridBlockEntity be = stoneWithFloorBeside(helper);
        SubgridTestSupport.rightClick(be, holding(helper, new ItemStack(Items.LEVER)), eastFaceAt(be, STONE, 0.5));

        BlockState lever = SubgridTestSupport.stateOf(be, STONE.east());
        helper.assertTrue(lever != null && lever.is(Blocks.LEVER)
                        && lever.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.WALL
                        && lever.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST,
                "no lever facing east on the stone's side: " + lever);
        helper.succeed();
    }

    @GameTest(template = "empty_platform")
    public void aSlabOnTheUpperHalfOfASideGoesOnTop(GameTestHelper helper) {
        SubgridBlockEntity be = stoneWithFloorBeside(helper);
        SubgridTestSupport.rightClick(be, holding(helper, new ItemStack(Items.STONE_SLAB)), eastFaceAt(be, STONE, 0.75));

        BlockState slab = SubgridTestSupport.stateOf(be, STONE.east());
        helper.assertTrue(slab != null && slab.is(Blocks.STONE_SLAB) && slab.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP,
                "no top slab beside the stone: " + slab);
        helper.succeed();
    }
}
