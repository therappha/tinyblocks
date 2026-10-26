package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.compat.create.ConnectorPlacement;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In an even grid the shaft connector is a gear (ShaftConnectorPiece#meshCells): it takes a real shaft outside and
 * meshes inside with tiny cogwheels — small ones on its sides, large ones on its diagonals — but not with a shaft.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridGearConnectorGameTest {

    private static final String PLATFORM = "empty_platform";

    private static BlockState alongX(com.tterrag.registrate.util.entry.BlockEntry<?> block) {
        return block.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X);
    }

    /** An 8-grid with a gear connector on its east face, and a real motor against that face turning it. */
    private static PlacedPiece[] gearDrivenByARealMotor(GameTestHelper helper, SubgridBlockEntity[] grid) {
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        SubgridBlockEntity be = SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), 8);
        if (be == null || !ConnectorPlacement.place(be, helper.getLevel(), Direction.EAST)) throw new IllegalStateException("no gear");
        helper.setBlock(new BlockPos(3, 2, 2), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));
        grid[0] = be;
        return new PlacedPiece[] {be.getPieceAt(7, 3, 3)};
    }

    private static float speed(GameTestHelper helper, SubgridBlockEntity be, PlacedPiece piece) {
        BlockEntity phantom = SubgridTestSupport.blockEntityOf(be, piece, helper.getLevel());
        return phantom instanceof KineticBlockEntity k ? k.getSpeed() : Float.NaN;
    }

    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void aSmallCogwheelOnTheGearsSideTurnsTwiceAsFastTheOtherWay(GameTestHelper helper) {
        SubgridBlockEntity[] grid = new SubgridBlockEntity[1];
        PlacedPiece gear = gearDrivenByARealMotor(helper, grid)[0];
        PlacedPiece cog = SubgridTestSupport.placePiece(grid[0], helper.getLevel(), new BlockPos(7, 2, 3), alongX(AllBlocks.COGWHEEL));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    float g = speed(helper, grid[0], gear), c = speed(helper, grid[0], cog);
                    helper.assertTrue(g != 0 && c == -2 * g, "gear " + g + ", small cogwheel on its side " + c + " (not -2x)");
                })
                .thenSucceed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void aLargeCogwheelOnTheGearsDiagonalTurnsAsFastTheOtherWay(GameTestHelper helper) {
        SubgridBlockEntity[] grid = new SubgridBlockEntity[1];
        PlacedPiece gear = gearDrivenByARealMotor(helper, grid)[0];
        PlacedPiece cog = SubgridTestSupport.placePiece(grid[0], helper.getLevel(), new BlockPos(7, 2, 2), alongX(AllBlocks.LARGE_COGWHEEL));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    float g = speed(helper, grid[0], gear), c = speed(helper, grid[0], cog);
                    helper.assertTrue(g != 0 && c == -g, "gear " + g + ", large cogwheel on its diagonal " + c + " (not -1x)");
                })
                .thenSucceed();
    }

    /** A tiny shaft right behind the gear is not driven: the gear only meshes with cogwheels. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void theGearTakesNoShaft(GameTestHelper helper) {
        SubgridBlockEntity[] grid = new SubgridBlockEntity[1];
        PlacedPiece gear = gearDrivenByARealMotor(helper, grid)[0];
        PlacedPiece shaft = SubgridTestSupport.placePiece(grid[0], helper.getLevel(), new BlockPos(6, 3, 3), alongX(AllBlocks.SHAFT));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(speed(helper, grid[0], gear) != 0, "the gear itself is still");
                    helper.assertTrue(speed(helper, grid[0], shaft) == 0, "a shaft against the gear turns: " + speed(helper, grid[0], shaft));
                })
                .thenSucceed();
    }

    /** The other way: a tiny motor turns a small cogwheel on the gear's side, the gear, and the real shaft outside. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void aTinyMotorTurnsTheRealShaftThroughTheGear(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        SubgridBlockEntity be = SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), 8);
        if (be == null || !ConnectorPlacement.place(be, helper.getLevel(), Direction.EAST)) throw new IllegalStateException("no gear");
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(7, 2, 3), alongX(AllBlocks.COGWHEEL));
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(6, 2, 3),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(3, 2, 2), alongX(AllBlocks.SHAFT));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> helper.assertTrue(helper.getBlockEntity(new BlockPos(3, 2, 2)) instanceof KineticBlockEntity k
                        && k.getSpeed() != 0, "the real shaft outside is still"))
                .thenSucceed();
    }
}
