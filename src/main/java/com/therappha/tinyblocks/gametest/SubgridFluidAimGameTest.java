package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Fluids in a grid as a player meets them: a crosshair goes through water and an empty bucket's stops at it, a block
 * placed into water takes its place, and a tank's client copy draws what it was sent.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridFluidAimGameTest {

    private static final String PLATFORM = "empty_platform";

    private static boolean covers(VoxelShape shape, SubgridBlockEntity be, BlockPos cell) {
        Vec3 center = Vec3.atCenterOf(cell).scale(1.0 / be.getGridSize());
        return shape.toAabbs().stream().anyMatch(box -> box.contains(center));
    }

    @GameTest(template = PLATFORM)
    public void aCrosshairGoesThroughWaterAndAnEmptyBucketsStopsAtIt(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        BlockPos stone = new BlockPos(4, 3, 4);
        BlockPos water = stone.above();
        SubgridTestSupport.placePiece(be, level, stone, Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(be, level, water, Blocks.WATER.defaultBlockState());

        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);
        BlockState grid = level.getBlockState(be.getBlockPos());
        VoxelShape bare = grid.getShape(level, be.getBlockPos(), CollisionContext.of(player));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        VoxelShape withBucket = grid.getShape(level, be.getBlockPos(), CollisionContext.of(player));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        VoxelShape withFullBucket = grid.getShape(level, be.getBlockPos(), CollisionContext.of(player));

        helper.assertTrue(covers(bare, be, stone), "the stone under the water can't be aimed at");
        helper.assertFalse(covers(bare, be, water), "an empty hand's crosshair stops at the water");
        helper.assertTrue(covers(withBucket, be, water), "an empty bucket's crosshair goes through the water source");
        helper.assertFalse(covers(withFullBucket, be, water), "a full bucket's crosshair stops at the water");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public void aSlabPlacedIntoWaterTakesItsPlaceWaterlogged(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        BlockPos stone = new BlockPos(4, 3, 4);
        BlockPos water = stone.above();
        SubgridTestSupport.placePiece(be, level, stone, Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(be, level, water, Blocks.WATER.defaultBlockState());

        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_SLAB));
        // Clicked on the top of the stone, through the water, as the crosshair now does.
        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.faceHit(be, stone, Direction.UP));

        BlockState placed = SubgridTestSupport.stateOf(be, water);
        helper.assertTrue(placed != null && placed.is(Blocks.OAK_SLAB), "the slab didn't take the water's place: " + placed);
        helper.assertTrue(placed.getValue(BlockStateProperties.WATERLOGGED), "the slab placed into water isn't waterlogged");
        helper.succeed();
    }

    /**
     * What PhantomSync sends a client for a tank, loaded as the client loads it: the fluid the tank draws moves to what
     * it holds. Loaded as saved data, as it used to be, Create reads it as a save and only a rebuilt tank showed it.
     */
    @GameTest(template = PLATFORM)
    public void aTanksClientCopyDrawsTheFluidItIsSent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = new BlockPos(4, 4, 4);
        SubgridBlockEntity server = SubgridTestSupport.placeSubgrid(helper, new BlockPos(1, 1, 2));
        SubgridBlockEntity client = SubgridTestSupport.placeSubgrid(helper, new BlockPos(4, 1, 2));
        PlacedPiece serverPiece = SubgridTestSupport.placePiece(server, level, cell, AllBlocks.FLUID_TANK.getDefaultState());
        PlacedPiece clientPiece = SubgridTestSupport.placePiece(client, level, cell, AllBlocks.FLUID_TANK.getDefaultState());

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    if (!(SubgridTestSupport.blockEntityOf(server, serverPiece, level) instanceof FluidTankBlockEntity sent)
                            || !(SubgridTestSupport.blockEntityOf(client, clientPiece, level) instanceof FluidTankBlockEntity drawn)) {
                        throw new IllegalStateException("a tank piece has no tank block entity");
                    }
                    // The client copy as it was: an empty tank, drawn empty.
                    VanillaBlockPiece.applyPhantomData(client, cell, sent.getUpdateTag(level.registryAccess()), true, level);
                    sent.getTankInventory().fill(new FluidStack(Fluids.WATER, sent.getTankInventory().getCapacity() / 2),
                            IFluidHandler.FluidAction.EXECUTE);
                    VanillaBlockPiece.applyPhantomData(client, cell, sent.getUpdateTag(level.registryAccess()), true, level);
                    float target = drawn.getFluidLevel().getChaseTarget();
                    helper.assertTrue(Math.abs(target - 0.5f) < 0.01f, "the client tank draws its fluid at " + target + ", not at half");
                })
                .thenSucceed();
    }
}
