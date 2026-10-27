package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.compat.create.ConnectorPlacement;
import com.therappha.tinyblocks.compat.create.ShaftConnectorPiece;
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
 * A shaft connector meets whatever is across its face: a real block (SubgridConnectorGameTest), or another
 * SubgridBlock — of the same size cell to cell; of another size only that grid's own connector facing back.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridConnectorNeighbourGameTest {

    private static final String PLATFORM = "empty_platform";

    private static BlockState shaftX() {
        return AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X);
    }

    private static BlockState motorFacing(Direction facing) {
        return AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, facing);
    }

    private static SubgridBlockEntity grid(GameTestHelper helper, BlockPos at, int size) {
        helper.setBlock(at, Blocks.AIR);
        SubgridBlockEntity be = SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(at), size);
        if (be == null) throw new IllegalStateException("no " + size + "-grid at " + at);
        return be;
    }

    private static float speed(GameTestHelper helper, SubgridBlockEntity be, PlacedPiece piece) {
        BlockEntity phantom = SubgridTestSupport.blockEntityOf(be, piece, helper.getLevel());
        return phantom instanceof KineticBlockEntity k ? k.getSpeed() : Float.NaN;
    }

    /**
     * A motor behind a connector on the east face of grid A drives the shaft in grid B east of it: in the cell the
     * connector meets for the same size, behind B's own connector facing back for another size.
     */
    private static void motorInADrivesB(GameTestHelper helper, int sizeA, int sizeB) {
        SubgridBlockEntity a = grid(helper, new BlockPos(2, 2, 2), sizeA);
        SubgridBlockEntity b = grid(helper, new BlockPos(3, 2, 2), sizeB);
        if (!ConnectorPlacement.place(a, helper.getLevel(), Direction.EAST)) throw new IllegalStateException("no connector");
        SubgridTestSupport.placePiece(a, helper.getLevel(), ShaftConnectorPiece.socket(Direction.EAST, sizeA), motorFacing(Direction.EAST));
        BlockPos shaftCell = ShaftConnectorPiece.partnerCell(Direction.EAST, sizeA, sizeB);
        if (sizeA != sizeB) {
            if (!ConnectorPlacement.place(b, helper.getLevel(), Direction.WEST)) throw new IllegalStateException("no connector on B");
            shaftCell = ShaftConnectorPiece.socket(Direction.WEST, sizeB);
        }
        PlacedPiece shaft = SubgridTestSupport.placePiece(b, helper.getLevel(), shaftCell, shaftX());

        helper.startSequence()
                .thenIdle(120) // past Create's periodic source validation
                .thenExecute(() -> helper.assertTrue(speed(helper, b, shaft) == 16,
                        "1/" + sizeA + " -> 1/" + sizeB + ": the shaft across turns at " + speed(helper, b, shaft) + ", not 16"))
                .thenSucceed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void aConnectorDrivesTheSameSizeGridNextToIt(GameTestHelper helper) {
        motorInADrivesB(helper, 7, 7);
    }

    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void aConnectorDrivesASmallerGridNextToIt(GameTestHelper helper) {
        motorInADrivesB(helper, 9, 5);
    }

    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void aConnectorDrivesALargerGridNextToIt(GameTestHelper helper) {
        motorInADrivesB(helper, 5, 9);
    }

    /** Across sizes a plain piece does not join a connector: a motor in grid B, at the cell A's connector meets, drives nothing. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void anotherSizeJoinsOnlyThroughItsOwnConnector(GameTestHelper helper) {
        SubgridBlockEntity a = grid(helper, new BlockPos(2, 2, 2), 9);
        SubgridBlockEntity b = grid(helper, new BlockPos(3, 2, 2), 5);
        if (!ConnectorPlacement.place(a, helper.getLevel(), Direction.EAST)) throw new IllegalStateException("no connector");
        PlacedPiece shaft = SubgridTestSupport.placePiece(a, helper.getLevel(), ShaftConnectorPiece.socket(Direction.EAST, 9), shaftX());
        SubgridTestSupport.placePiece(b, helper.getLevel(), ShaftConnectorPiece.partnerCell(Direction.EAST, 9, 5), motorFacing(Direction.WEST));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> helper.assertTrue(speed(helper, a, shaft) == 0,
                        "a plain piece of another size drove the connector: " + speed(helper, a, shaft)))
                .thenSucceed();
    }

    /** A 1/7 funnel facing a 1/8 gear: the motor behind the funnel turns a small cogwheel on the gear's side. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void aFunnelFacingAGearJoins(GameTestHelper helper) {
        SubgridBlockEntity a = grid(helper, new BlockPos(2, 2, 2), 7);
        SubgridBlockEntity b = grid(helper, new BlockPos(3, 2, 2), 8);
        if (!ConnectorPlacement.place(a, helper.getLevel(), Direction.EAST)) throw new IllegalStateException("no funnel");
        if (!ConnectorPlacement.place(b, helper.getLevel(), Direction.WEST)) throw new IllegalStateException("no gear");
        SubgridTestSupport.placePiece(a, helper.getLevel(), ShaftConnectorPiece.socket(Direction.EAST, 7), motorFacing(Direction.EAST));
        PlacedPiece cog = SubgridTestSupport.placePiece(b, helper.getLevel(),
                ShaftConnectorPiece.meshCells(Direction.WEST, 8).get(0),
                AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> helper.assertTrue(Math.abs(speed(helper, b, cog)) == 32,
                        "the cogwheel on the gear's side turns at " + speed(helper, b, cog) + ", not twice the motor's 16"))
                .thenSucceed();
    }

    /** Two connectors facing each other, on grids of different sizes, join. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void twoConnectorsFacingEachOtherJoin(GameTestHelper helper) {
        SubgridBlockEntity a = grid(helper, new BlockPos(2, 2, 2), 9);
        SubgridBlockEntity b = grid(helper, new BlockPos(3, 2, 2), 5);
        if (!ConnectorPlacement.place(a, helper.getLevel(), Direction.EAST)) throw new IllegalStateException("no connector on A");
        if (!ConnectorPlacement.place(b, helper.getLevel(), Direction.WEST)) throw new IllegalStateException("no connector on B");
        SubgridTestSupport.placePiece(a, helper.getLevel(), ShaftConnectorPiece.socket(Direction.EAST, 9), motorFacing(Direction.EAST));
        PlacedPiece shaft = SubgridTestSupport.placePiece(b, helper.getLevel(), ShaftConnectorPiece.socket(Direction.WEST, 5), shaftX());

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> helper.assertTrue(speed(helper, b, shaft) == 16,
                        "the shaft behind B's connector turns at " + speed(helper, b, shaft) + ", not 16"))
                .thenSucceed();
    }

    /** Grid B taken away and a real shaft put there: the connector drives the real shaft, as a plain connector does. */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public void theConnectorGoesBackToARealBlockWhenTheGridIsGone(GameTestHelper helper) {
        SubgridBlockEntity a = grid(helper, new BlockPos(2, 2, 2), 9);
        grid(helper, new BlockPos(3, 2, 2), 5);
        if (!ConnectorPlacement.place(a, helper.getLevel(), Direction.EAST)) throw new IllegalStateException("no connector");
        SubgridTestSupport.placePiece(a, helper.getLevel(), ShaftConnectorPiece.socket(Direction.EAST, 9), motorFacing(Direction.EAST));

        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.setBlock(new BlockPos(3, 2, 2), Blocks.AIR);
                    helper.setBlock(new BlockPos(3, 2, 2), shaftX());
                })
                .thenIdle(120)
                .thenExecute(() -> helper.assertTrue(helper.getBlockEntity(new BlockPos(3, 2, 2)) instanceof KineticBlockEntity k
                                && k.getSpeed() != 0, "the real shaft that replaced the grid is still"))
                .thenSucceed();
    }
}
