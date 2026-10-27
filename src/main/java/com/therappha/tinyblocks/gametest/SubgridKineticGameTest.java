package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Create's rotation propagation inside one subgrid: a creative motor driving a chain of shafts.
 * Create finds neighbours with level.getBlockEntity(pos.relative(face)), so this only works if every
 * phantom block entity sits at its own consistent virtual position (SubgridBlockEntity#virtualOrigin).
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridKineticGameTest {

    private static final String PLATFORM = "empty_platform";

    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public void motorDrivesAShaftChain(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();

        BlockPos motorAnchor = new BlockPos(2, 4, 4);
        PlacedPiece motor = SubgridTestSupport.placePiece(be, level, motorAnchor,
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        PlacedPiece[] shafts = new PlacedPiece[3];
        for (int i = 0; i < shafts.length; i++) {
            shafts[i] = SubgridTestSupport.placePiece(be, level, motorAnchor.east(i + 1),
                    AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        }

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    BlockEntity motorBE = SubgridTestSupport.blockEntityOf(be, motor, level);
                    helper.assertTrue(motorBE instanceof KineticBlockEntity k && k.getSpeed() != 0,
                            "motor itself is not generating speed: " + motorBE);
                    for (int i = 0; i < shafts.length; i++) {
                        BlockEntity shaftBE = SubgridTestSupport.blockEntityOf(be, shafts[i], level);
                        helper.assertTrue(shaftBE instanceof KineticBlockEntity k && k.getSpeed() != 0,
                                "shaft " + i + " never received rotation: " + shaftBE);
                    }
                })
                .thenSucceed();
    }

    /** Two side-by-side small cogwheels mesh: the driven one must spin the opposite way at the same speed. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public void meshedCogwheelsCounterRotate(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();

        BlockPos motorAnchor = new BlockPos(2, 4, 4);
        SubgridTestSupport.placePiece(be, level, motorAnchor,
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        BlockState cog = AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X);
        PlacedPiece driver = SubgridTestSupport.placePiece(be, level, motorAnchor.east(), cog);
        PlacedPiece driven = SubgridTestSupport.placePiece(be, level, motorAnchor.east().south(), cog);

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    BlockEntity a = SubgridTestSupport.blockEntityOf(be, driver, level);
                    BlockEntity b = SubgridTestSupport.blockEntityOf(be, driven, level);
                    helper.assertTrue(a instanceof KineticBlockEntity ka && ka.getSpeed() != 0,
                            "driving cogwheel is not rotating: " + a);
                    helper.assertTrue(b instanceof KineticBlockEntity kb && kb.getSpeed() != 0,
                            "meshed cogwheel never received rotation: " + b);
                    float sa = ((KineticBlockEntity) a).getSpeed();
                    float sb = ((KineticBlockEntity) b).getSpeed();
                    helper.assertTrue(sa == -sb, "meshed cogwheels should counter-rotate, got " + sa + " and " + sb);
                })
                .thenSucceed();
    }

    /** A shaft chain that crosses the edge of one subgrid into the next one east of it. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public void rotationCrossesIntoTheNeighbouringSubgrid(GameTestHelper helper) {
        SubgridBlockEntity west = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        SubgridBlockEntity east = SubgridTestSupport.placeSubgrid(helper, new BlockPos(3, 1, 2));
        ServerLevel level = helper.getLevel();
        int last = west.getGridSize() - 1;

        BlockPos motorAnchor = new BlockPos(last - 1, 4, 4);
        SubgridTestSupport.placePiece(west, level, motorAnchor,
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X);
        PlacedPiece edge = SubgridTestSupport.placePiece(west, level, new BlockPos(last, 4, 4), shaft);
        PlacedPiece first = SubgridTestSupport.placePiece(east, level, new BlockPos(0, 4, 4), shaft);
        PlacedPiece second = SubgridTestSupport.placePiece(east, level, new BlockPos(1, 4, 4), shaft);

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    BlockEntity a = SubgridTestSupport.blockEntityOf(west, edge, level);
                    helper.assertTrue(a instanceof KineticBlockEntity ka && ka.getSpeed() != 0,
                            "shaft at the edge of the first subgrid is not rotating: " + a);
                    BlockEntity b = SubgridTestSupport.blockEntityOf(east, first, level);
                    helper.assertTrue(b instanceof KineticBlockEntity kb && kb.getSpeed() != 0,
                            "rotation did not cross into the neighbouring subgrid: " + b);
                    BlockEntity c = SubgridTestSupport.blockEntityOf(east, second, level);
                    helper.assertTrue(c instanceof KineticBlockEntity kc && kc.getSpeed() != 0,
                            "rotation stopped after the first shaft of the neighbouring subgrid: " + c);
                })
                .thenSucceed();
    }

    /** Same chain as above, but the motor is placed LAST, after the shafts on both sides of the edge exist. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public void motorPlacedLastDrivesShaftsAcrossTheEdge(GameTestHelper helper) {
        SubgridBlockEntity west = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        SubgridBlockEntity east = SubgridTestSupport.placeSubgrid(helper, new BlockPos(3, 1, 2));
        ServerLevel level = helper.getLevel();
        int last = west.getGridSize() - 1;

        BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X);
        PlacedPiece edge = SubgridTestSupport.placePiece(west, level, new BlockPos(last, 4, 4), shaft);
        PlacedPiece first = SubgridTestSupport.placePiece(east, level, new BlockPos(0, 4, 4), shaft);
        PlacedPiece second = SubgridTestSupport.placePiece(east, level, new BlockPos(1, 4, 4), shaft);
        SubgridTestSupport.placePiece(west, level, new BlockPos(last - 1, 4, 4),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    BlockEntity a = SubgridTestSupport.blockEntityOf(west, edge, level);
                    helper.assertTrue(a instanceof KineticBlockEntity ka && ka.getSpeed() != 0,
                            "shaft at the edge of the motor's subgrid is not rotating: " + a);
                    BlockEntity b = SubgridTestSupport.blockEntityOf(east, first, level);
                    helper.assertTrue(b instanceof KineticBlockEntity kb && kb.getSpeed() != 0,
                            "rotation did not reach the neighbouring subgrid's first shaft: " + b);
                    BlockEntity c = SubgridTestSupport.blockEntityOf(east, second, level);
                    helper.assertTrue(c instanceof KineticBlockEntity kc && kc.getSpeed() != 0,
                            "rotation did not reach the neighbouring subgrid's second shaft: " + c);
                })
                .thenSucceed();
    }

    /** A row of meshed cogwheels running across the edge into a neighbouring subgrid, motor placed last. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public void cogwheelRowCrossesTheEdgeWhenTheMotorIsPlacedLast(GameTestHelper helper) {
        SubgridBlockEntity west = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        SubgridBlockEntity east = SubgridTestSupport.placeSubgrid(helper, new BlockPos(3, 1, 2));
        ServerLevel level = helper.getLevel();
        int last = west.getGridSize() - 1;

        BlockState cog = AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Y);
        PlacedPiece[] westCogs = new PlacedPiece[4];
        for (int i = 0; i < westCogs.length; i++) {
            westCogs[i] = SubgridTestSupport.placePiece(west, level, new BlockPos(last - 3 + i, 4, 4), cog);
        }
        PlacedPiece[] eastCogs = new PlacedPiece[2];
        for (int i = 0; i < eastCogs.length; i++) {
            eastCogs[i] = SubgridTestSupport.placePiece(east, level, new BlockPos(i, 4, 4), cog);
        }
        int flushesBefore = east.phantomDataFlushes();
        SubgridTestSupport.placePiece(west, level, new BlockPos(last - 3, 3, 4),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        west.notifyUpdate(); // what the real placement flow ends with

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(east.phantomDataFlushes() > flushesBefore,
                            "the neighbouring subgrid's clients were never sent its new rotation data");
                    for (int i = 0; i < westCogs.length; i++) {
                        BlockEntity b = SubgridTestSupport.blockEntityOf(west, westCogs[i], level);
                        helper.assertTrue(b instanceof KineticBlockEntity k && k.getSpeed() != 0,
                                "west cogwheel " + i + " is not rotating: " + b);
                    }
                    for (int i = 0; i < eastCogs.length; i++) {
                        BlockEntity b = SubgridTestSupport.blockEntityOf(east, eastCogs[i], level);
                        helper.assertTrue(b instanceof KineticBlockEntity k && k.getSpeed() != 0,
                                "east cogwheel " + i + " is not rotating: " + b);
                    }
                })
                .thenSucceed();
    }

    /** Breaking the motor must stop the shafts it was driving (server-side speed back to 0). */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void removingTheMotorStopsTheShafts(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();

        BlockPos motorAnchor = new BlockPos(2, 4, 4);
        SubgridTestSupport.placePiece(be, level, motorAnchor,
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        PlacedPiece[] shafts = new PlacedPiece[3];
        for (int i = 0; i < shafts.length; i++) {
            shafts[i] = SubgridTestSupport.placePiece(be, level, motorAnchor.east(i + 1),
                    AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        }

        int[] flushesBeforeRemoval = new int[1];
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    BlockEntity first = SubgridTestSupport.blockEntityOf(be, shafts[0], level);
                    helper.assertTrue(first instanceof KineticBlockEntity k && k.getSpeed() != 0,
                            "precondition: shaft should be rotating before the motor is removed");
                    flushesBeforeRemoval[0] = be.phantomDataFlushes();
                    be.removePieceAt(motorAnchor.getX(), motorAnchor.getY(), motorAnchor.getZ());
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertTrue(be.phantomDataFlushes() > flushesBeforeRemoval[0],
                            "clients were never sent the shafts' new (stopped) state after the motor was removed");
                    for (int i = 0; i < shafts.length; i++) {
                        BlockEntity b = SubgridTestSupport.blockEntityOf(be, shafts[i], level);
                        helper.assertTrue(b instanceof KineticBlockEntity k && k.getSpeed() == 0,
                                "shaft " + i + " is still rotating after the motor was removed: "
                                        + ((KineticBlockEntity) b).getSpeed());
                    }
                })
                .thenSucceed();
    }

    /**
     * A grid near the world origin: its phantoms' virtual positions (world position times the grid size) fall in
     * a loaded chunk, above the world's top. Create's sendData marked them changed in the real chunk cache, which
     * threw every tick there and cut rotation off part way (SyncedBlockEntityMixin).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public void aGridNearTheWorldOriginStillTurns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(1, 100, 1);
        level.setChunkForced(0, 0, true);
        level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        SubgridBlockEntity be = SubgridBlockEntity.createAt(level, at, 7);
        if (be == null) throw new IllegalStateException("no grid at " + at);
        PlacedPiece motor = SubgridTestSupport.placePiece(be, level, new BlockPos(2, 3, 3),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        PlacedPiece shaft = SubgridTestSupport.placePiece(be, level, new BlockPos(3, 3, 3),
                AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    BlockEntity motorBE = SubgridTestSupport.blockEntityOf(be, motor, level);
                    BlockEntity shaftBE = SubgridTestSupport.blockEntityOf(be, shaft, level);
                    RuntimeException synced = null;
                    try {
                        ((KineticBlockEntity) shaftBE).sendData();
                    } catch (RuntimeException e) {
                        synced = e;
                    }
                    level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    level.setChunkForced(0, 0, false);
                    helper.assertTrue(synced == null, "the shaft's sendData threw: " + synced);
                    helper.assertTrue(motorBE instanceof KineticBlockEntity k && k.getSpeed() != 0, "motor still: " + motorBE);
                    helper.assertTrue(shaftBE instanceof KineticBlockEntity k && k.getSpeed() != 0, "shaft still: " + shaftBE);
                })
                .thenSucceed();
    }
}
