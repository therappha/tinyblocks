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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A shaft connector plate (the funnel, in an odd grid) on the east face of a subgrid joins the Create kinetic network of the real block
 * touching that face with the tiny shafts right behind the plate.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridConnectorGameTest {

    private static final String PLATFORM = "empty_platform";

    /** A 1/7 grid: odd, so its connector is the funnel these tests are about (an even grid's is a gear). */
    private static SubgridBlockEntity funnelGrid(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, net.minecraft.world.level.block.Blocks.AIR);
        SubgridBlockEntity be = SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(at), 7);
        if (be == null) throw new IllegalStateException("no grid at " + at);
        return be;
    }

    private static BlockState shaftAlong(Direction.Axis axis) {
        return AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, axis);
    }

    /** The socket of a funnel on the east face, where the tiny shaft it drives goes. */
    private static BlockPos eastSocket(SubgridBlockEntity be) {
        return ShaftConnectorPiece.socket(Direction.EAST, be.getGridSize());
    }

    /** A wrench moves a connector to the next free face, skipping a taken one, with its axis along the new face. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void aWrenchMovesAConnectorToTheNextFreeFace(GameTestHelper helper) {
        SubgridBlockEntity be = funnelGrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        PlacedPiece plate = plateOn(be, level, Direction.EAST);
        // East's next face is down: taken here, so it goes on to up.
        BlockPos taken = ShaftConnectorPiece.anchorOn(Direction.DOWN, be.getGridSize());
        SubgridTestSupport.placePiece(be, level, taken, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, com.simibubi.create.AllItems.WRENCH.asStack());

        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.topHit(be, plate.anchor));

        var plates = be.getPieces().stream().filter(p -> ShaftConnectorPiece.faceOf(p) != null).toList();
        helper.assertTrue(plates.size() == 1, "connectors in the grid: " + plates.size());
        PlacedPiece moved = plates.get(0);
        helper.assertTrue(moved.facing == Direction.UP, "the connector is on " + moved.facing);
        helper.assertTrue(moved.anchor.equals(ShaftConnectorPiece.anchorOn(Direction.UP, be.getGridSize())), "the connector's cells aren't on the face");
        helper.assertTrue(SubgridTestSupport.stateOf(be, moved.anchor).getValue(RotatedPillarKineticBlock.AXIS) == Direction.Axis.Y,
                "the connector's axis isn't its face's");
        helper.succeed();
    }

    /** Without a Minimizer, a block clicked onto a connector's wide end is a normal block outside the grid. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void aBlockClickedOntoAConnectorsWideEndGoesOutside(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2), outside = at.east();
        helper.setBlock(outside, net.minecraft.world.level.block.Blocks.AIR);
        SubgridBlockEntity be = funnelGrid(helper, at);
        ServerLevel level = helper.getLevel();
        plateOn(be, level, Direction.EAST);
        var player = SubgridTestSupport.serverPlayer(helper, be);
        var shafts = AllBlocks.SHAFT.asStack(2);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, shafts);
        BlockPos real = be.getBlockPos();
        var onWideEnd = new net.minecraft.world.phys.BlockHitResult(
                new net.minecraft.world.phys.Vec3(real.getX() + 1, real.getY() + 0.5, real.getZ() + 0.5), Direction.EAST, real, false);

        player.gameMode.useItemOn(player, level, shafts, net.minecraft.world.InteractionHand.MAIN_HAND, onWideEnd);

        helper.assertBlockPresent(AllBlocks.SHAFT.get(), outside);
        helper.assertBlockProperty(outside, RotatedPillarKineticBlock.AXIS, Direction.Axis.X);
        helper.assertTrue(be.getPieces().size() == 1, "the block went into the grid: " + be.getPieces().size() + " pieces");
        helper.succeed();
    }

    private static PlacedPiece plateOn(SubgridBlockEntity be, ServerLevel level, Direction face) {
        if (!ConnectorPlacement.place(be, level, face)) {
            throw new IllegalStateException("could not place a connector plate on " + face + " of " + be.getBlockPos());
        }
        PlacedPiece plate = be.getPieceAt(be.getGridSize() - 1, be.getGridSize() / 2, be.getGridSize() / 2);
        if (plate == null) throw new IllegalStateException("connector plate does not cover the centre of the face");
        return plate;
    }

    /** A real motor touching the east face drives the plate and the tiny shafts behind it. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void realMotorDrivesTheShaftsInsideThroughAPlate(GameTestHelper helper) {
        SubgridBlockEntity be = funnelGrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();

        PlacedPiece plate = plateOn(be, level, Direction.EAST);
        PlacedPiece behind = SubgridTestSupport.placePiece(be, level, eastSocket(be), shaftAlong(Direction.Axis.X));
        PlacedPiece further = SubgridTestSupport.placePiece(be, level, eastSocket(be).west(), shaftAlong(Direction.Axis.X));
        helper.setBlock(new BlockPos(3, 1, 2),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));

        // Past Create's periodic source validation (kineticValidationFrequency, 60 ticks by default), which re-reads
        // each rotation source by position and drops it if that position doesn't resolve to the source.
        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    BlockEntity a = SubgridTestSupport.blockEntityOf(be, plate, level);
                    helper.assertTrue(a instanceof KineticBlockEntity ka && ka.getSpeed() != 0,
                            "plate never received rotation from the real motor: " + a);
                    BlockEntity b = SubgridTestSupport.blockEntityOf(be, behind, level);
                    helper.assertTrue(b instanceof KineticBlockEntity kb && kb.getSpeed() != 0,
                            "rotation stopped at the plate, the shaft behind it is idle: " + b);
                    BlockEntity c = SubgridTestSupport.blockEntityOf(be, further, level);
                    helper.assertTrue(c instanceof KineticBlockEntity kc && kc.getSpeed() != 0,
                            "rotation stopped after the first shaft behind the plate: " + c);
                })
                .thenSucceed();
    }

    /** The other way round: a motor inside, behind the plate, drives a real shaft touching the east face. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void motorInsideDrivesARealShaftThroughAPlate(GameTestHelper helper) {
        SubgridBlockEntity be = funnelGrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();

        plateOn(be, level, Direction.EAST);
        SubgridTestSupport.placePiece(be, level, eastSocket(be),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(3, 1, 2), shaftAlong(Direction.Axis.X));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    BlockEntity real = helper.getBlockEntity(new BlockPos(3, 1, 2));
                    helper.assertTrue(real instanceof KineticBlockEntity k && k.getSpeed() != 0,
                            "real shaft touching the subgrid never received rotation from inside: " + real);
                })
                .thenSucceed();
    }

    /**
     * Breaking the motor inside stops everything it drove, on both sides of the plate, at once — as breaking a
     * real block does in Create — not one shaft per periodic validation.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void breakingTheMotorInsideStopsBothSides(GameTestHelper helper) {
        SubgridBlockEntity be = funnelGrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();

        PlacedPiece plate = plateOn(be, level, Direction.EAST);
        BlockPos motorAnchor = eastSocket(be).west();
        SubgridTestSupport.placePiece(be, level, motorAnchor,
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        PlacedPiece inner = SubgridTestSupport.placePiece(be, level, eastSocket(be), shaftAlong(Direction.Axis.X));
        helper.setBlock(new BlockPos(3, 1, 2), shaftAlong(Direction.Axis.X));
        helper.setBlock(new BlockPos(4, 1, 2), shaftAlong(Direction.Axis.X));

        helper.startSequence()
                .thenIdle(80)
                .thenExecute(() -> {
                    BlockEntity real = helper.getBlockEntity(new BlockPos(4, 1, 2));
                    helper.assertTrue(real instanceof KineticBlockEntity k && k.getSpeed() != 0,
                            "precondition: the real shafts should be rotating before the motor is broken");
                    be.removePieceAt(motorAnchor.getX(), motorAnchor.getY(), motorAnchor.getZ());
                })
                .thenIdle(STOP_TICKS)
                .thenExecute(() -> {
                    String speeds = "inner=" + speedOf(SubgridTestSupport.blockEntityOf(be, inner, level))
                            + " plate=" + speedOf(SubgridTestSupport.blockEntityOf(be, plate, level))
                            + " real1=" + speedOf(helper.getBlockEntity(new BlockPos(3, 1, 2)))
                            + " real2=" + speedOf(helper.getBlockEntity(new BlockPos(4, 1, 2)));
                    helper.assertTrue(speeds.equals("inner=0.0 plate=0.0 real1=0.0 real2=0.0"),
                            "still rotating after the motor was broken: " + speeds);
                })
                .thenSucceed();
    }

    /** Well under Create's periodic validation (60 ticks): what stops here was stopped by the removal itself. */
    private static final int STOP_TICKS = 5;

    private static float speedOf(BlockEntity be) {
        return be instanceof KineticBlockEntity k ? k.getSpeed() : Float.NaN;
    }

    /** Breaking the real motor outside stops the plate and the shaft behind it at once. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void breakingTheRealMotorStopsTheInside(GameTestHelper helper) {
        SubgridBlockEntity be = funnelGrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();

        PlacedPiece plate = plateOn(be, level, Direction.EAST);
        PlacedPiece inner = SubgridTestSupport.placePiece(be, level, eastSocket(be), shaftAlong(Direction.Axis.X));
        helper.setBlock(new BlockPos(3, 1, 2),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));

        helper.startSequence()
                .thenIdle(80)
                .thenExecute(() -> {
                    helper.assertTrue(speedOf(SubgridTestSupport.blockEntityOf(be, inner, level)) != 0,
                            "precondition: the shaft inside should be rotating before the motor is broken");
                    helper.setBlock(new BlockPos(3, 1, 2), net.minecraft.world.level.block.Blocks.AIR);
                })
                .thenIdle(STOP_TICKS)
                .thenExecute(() -> {
                    String speeds = "plate=" + speedOf(SubgridTestSupport.blockEntityOf(be, plate, level))
                            + " inner=" + speedOf(SubgridTestSupport.blockEntityOf(be, inner, level));
                    helper.assertTrue(speeds.equals("plate=0.0 inner=0.0"),
                            "still rotating after the real motor was broken: " + speeds);
                })
                .thenSucceed();
    }

    /** Breaking the plate cuts the connection at once: a real motor outside no longer drives the shaft inside. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void breakingThePlateStopsTheInside(GameTestHelper helper) {
        SubgridBlockEntity be = funnelGrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();

        PlacedPiece plate = plateOn(be, level, Direction.EAST);
        // The socket is not next to the plate's anchor: the plate must let go of it too, not only of its anchor's neighbours.
        PlacedPiece inner = SubgridTestSupport.placePiece(be, level, eastSocket(be), shaftAlong(Direction.Axis.X));
        helper.setBlock(new BlockPos(3, 1, 2),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));

        helper.startSequence()
                .thenIdle(80)
                .thenExecute(() -> {
                    helper.assertTrue(speedOf(SubgridTestSupport.blockEntityOf(be, inner, level)) != 0,
                            "precondition: the shaft inside should be rotating before the plate is broken");
                    be.removePieceAt(plate.anchor.getX(), plate.anchor.getY(), plate.anchor.getZ());
                })
                .thenIdle(STOP_TICKS)
                .thenExecute(() -> {
                    KineticBlockEntity k = (KineticBlockEntity) SubgridTestSupport.blockEntityOf(be, inner, level);
                    helper.assertTrue(k.getSpeed() == 0, "shaft inside still rotating after the plate was broken: "
                            + k.getSpeed() + " source=" + k.source + " plate=" + be.virtualPosOf(plate.anchor));
                })
                .thenSucceed();
    }

    /** Breaking the plate also stops the real shaft a motor inside was driving through it. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void breakingThePlateStopsTheRealSide(GameTestHelper helper) {
        SubgridBlockEntity be = funnelGrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();

        PlacedPiece plate = plateOn(be, level, Direction.EAST);
        SubgridTestSupport.placePiece(be, level, eastSocket(be),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(3, 1, 2), shaftAlong(Direction.Axis.X));

        helper.startSequence()
                .thenIdle(80)
                .thenExecute(() -> {
                    helper.assertTrue(speedOf(helper.getBlockEntity(new BlockPos(3, 1, 2))) != 0,
                            "precondition: the real shaft should be rotating before the plate is broken");
                    be.removePieceAt(plate.anchor.getX(), plate.anchor.getY(), plate.anchor.getZ());
                })
                .thenIdle(STOP_TICKS)
                .thenExecute(() -> helper.assertTrue(speedOf(helper.getBlockEntity(new BlockPos(3, 1, 2))) == 0,
                        "real shaft still rotating after the plate was broken: " + speedOf(helper.getBlockEntity(new BlockPos(3, 1, 2)))))
                .thenSucceed();
    }

    /** Breaking the whole SubgridBlock stops the real shaft the motor inside it was driving. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void breakingTheSubgridStopsTheRealSide(GameTestHelper helper) {
        SubgridBlockEntity be = funnelGrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();

        plateOn(be, level, Direction.EAST);
        SubgridTestSupport.placePiece(be, level, eastSocket(be),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(3, 1, 2), shaftAlong(Direction.Axis.X));

        helper.startSequence()
                .thenIdle(80)
                .thenExecute(() -> {
                    helper.assertTrue(speedOf(helper.getBlockEntity(new BlockPos(3, 1, 2))) != 0,
                            "precondition: the real shaft should be rotating before the subgrid is broken");
                    helper.setBlock(new BlockPos(2, 1, 2), net.minecraft.world.level.block.Blocks.AIR);
                })
                .thenIdle(STOP_TICKS)
                .thenExecute(() -> helper.assertTrue(speedOf(helper.getBlockEntity(new BlockPos(3, 1, 2))) == 0,
                        "real shaft still rotating after the subgrid was broken: " + speedOf(helper.getBlockEntity(new BlockPos(3, 1, 2)))))
                .thenSucceed();
    }

    /**
     * Through a grid and out the other side: a real motor east, plates on the east and west faces joined by a row
     * of tiny shafts, a real shaft west. Every shaft along the line turns at the motor's speed, same direction.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void rotationPassesThroughAGridAtTheSameSpeed(GameTestHelper helper) {
        SubgridBlockEntity be = funnelGrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();

        PlacedPiece east = plateOn(be, level, Direction.EAST);
        if (!ConnectorPlacement.place(be, level, Direction.WEST)) throw new IllegalStateException("west plate not placed");
        BlockPos from = ShaftConnectorPiece.socket(Direction.WEST, be.getGridSize());
        PlacedPiece[] row = new PlacedPiece[eastSocket(be).getX() - from.getX() + 1];
        for (int i = 0; i < row.length; i++) {
            row[i] = SubgridTestSupport.placePiece(be, level, from.east(i), shaftAlong(Direction.Axis.X));
        }
        helper.setBlock(new BlockPos(3, 1, 2),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));
        helper.setBlock(new BlockPos(1, 1, 2), shaftAlong(Direction.Axis.X));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    float expected = speedOf(SubgridTestSupport.blockEntityOf(be, east, level));
                    helper.assertTrue(expected != 0, "east plate not rotating");
                    StringBuilder speeds = new StringBuilder();
                    for (PlacedPiece shaft : row) speeds.append(speedOf(SubgridTestSupport.blockEntityOf(be, shaft, level))).append(' ');
                    float west = speedOf(helper.getBlockEntity(new BlockPos(1, 1, 2)));
                    speeds.append("| real west=").append(west);
                    boolean same = west == expected;
                    for (PlacedPiece shaft : row) same &= speedOf(SubgridTestSupport.blockEntityOf(be, shaft, level)) == expected;
                    helper.assertTrue(same, "expected every shaft at " + expected + ": " + speeds);
                })
                .thenSucceed();
    }

    /** The plate placed last, after the motor outside and the shaft inside exist, still joins them. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void plateBuiltLastStillConnects(GameTestHelper helper) {
        SubgridBlockEntity be = funnelGrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        int last = be.getGridSize() - 1;

        PlacedPiece inner = SubgridTestSupport.placePiece(be, level, eastSocket(be), shaftAlong(Direction.Axis.X));
        helper.setBlock(new BlockPos(3, 1, 2),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> plateOn(be, level, Direction.EAST))
                .thenIdle(120)
                .thenExecute(() -> helper.assertTrue(speedOf(SubgridTestSupport.blockEntityOf(be, inner, level)) != 0,
                        "shaft inside not rotating after the plate was placed last"))
                .thenSucceed();
    }

    /**
     * Only the socket connects: a block put into it the way a click onto the plate does turns, a shaft touching
     * the plate's back anywhere else does not.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void onlyTheSocketConnects(GameTestHelper helper) {
        SubgridBlockEntity be = funnelGrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        int last = be.getGridSize() - 1;

        PlacedPiece plate = plateOn(be, level, Direction.EAST);
        var target = ConnectorPlacement.targetOn(be, plate, AllBlocks.SHAFT.get(), net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()));
        if (target == null || !ConnectorPlacement.placeAt(be, level, target, plate.facing)) {
            throw new IllegalStateException("socket not filled");
        }
        BlockPos socket = ShaftConnectorPiece.socket(Direction.EAST, be.getGridSize());
        PlacedPiece inSocket = be.getPieceAt(socket.getX(), socket.getY(), socket.getZ());
        // Also right behind the plate (its anchor's row), but not the socket.
        PlacedPiece offSocket = SubgridTestSupport.placePiece(be, level, plate.anchor.west(), shaftAlong(Direction.Axis.X));
        helper.setBlock(new BlockPos(3, 1, 2),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(socket.getX() == last - ShaftConnectorPiece.depth(be.getGridSize()), "socket should be right behind the funnel: " + socket);
                    helper.assertTrue(speedOf(SubgridTestSupport.blockEntityOf(be, inSocket, level)) != 0,
                            "the shaft put into the socket is not rotating");
                    helper.assertTrue(speedOf(SubgridTestSupport.blockEntityOf(be, offSocket, level)) == 0,
                            "a shaft behind the plate but outside its socket is rotating: "
                                    + speedOf(SubgridTestSupport.blockEntityOf(be, offSocket, level)));
                })
                .thenSucceed();
    }

    /** Without a plate, a plain tiny shaft at the centre of the face must not connect. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public void plainTinyShaftOnTheFaceDoesNotConnect(GameTestHelper helper) {
        SubgridBlockEntity be = funnelGrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        int last = be.getGridSize() - 1;
        int mid = be.getGridSize() / 2;

        PlacedPiece shaft = SubgridTestSupport.placePiece(be, level, new BlockPos(last, mid, mid), shaftAlong(Direction.Axis.X));
        helper.setBlock(new BlockPos(3, 1, 2),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    BlockEntity a = SubgridTestSupport.blockEntityOf(be, shaft, level);
                    helper.assertTrue(!(a instanceof KineticBlockEntity ka) || ka.getSpeed() == 0,
                            "a plain tiny shaft must not connect to the real motor: " + a);
                })
                .thenSucceed();
    }
}
