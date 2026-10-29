package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.TorquePropagator;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.compat.create.ConnectorPlacement;
import com.therappha.tinyblocks.compat.create.ShaftConnectorPiece;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeLevel;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A grid turned or mirrored in place (SubgridBlockEntity#transform): each piece is in its turned cell with its turned
 * state, a wall torch and a wall link still hang on their turned walls, a cogwheel still turns with its motor (and the
 * phantoms left behind are in no kinetic network), the link still answers a linked controller, and a shaft connector
 * joins what is on its new face.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridTransformGameTest {

    private static final BlockPos TORCH_WALL = new BlockPos(1, 1, 0), TORCH = new BlockPos(1, 1, 1);
    private static final BlockPos STAIR = new BlockPos(3, 0, 3);
    private static final BlockPos MOTOR = new BlockPos(4, 3, 5), COG = new BlockPos(5, 3, 5), FAR_COG = new BlockPos(6, 3, 5);
    private static final BlockPos LINK_WALL = new BlockPos(6, 2, 0), LINK = new BlockPos(6, 2, 1);

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void aGridTurnedClockwiseKeepsWorking(GameTestHelper helper) {
        // Not the diamond or echo shard other link tests use: they run alongside, in range.
        transformed(helper, Rotation.CLOCKWISE_90, Mirror.NONE, Items.AMETHYST_SHARD, new Expected(
                new BlockPos(6, 1, 1), Direction.WEST,
                new BlockPos(4, 0, 3), Direction.SOUTH,
                new BlockPos(2, 3, 4), Direction.SOUTH, new BlockPos(2, 3, 5), Direction.Axis.Z,
                new BlockPos(6, 2, 6), Direction.WEST));
    }

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void aGridMirroredKeepsWorking(GameTestHelper helper) {
        transformed(helper, Rotation.NONE, Mirror.LEFT_RIGHT, Items.PRISMARINE_SHARD, new Expected(
                new BlockPos(1, 1, 6), Direction.NORTH,
                new BlockPos(3, 0, 4), Direction.EAST,
                new BlockPos(4, 3, 2), Direction.EAST, new BlockPos(5, 3, 2), Direction.Axis.X,
                new BlockPos(6, 2, 6), Direction.NORTH));
    }

    /** Mirrored, then turned: the order a vanilla structure does them in. */
    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void aGridMirroredAndTurnedKeepsWorking(GameTestHelper helper) {
        transformed(helper, Rotation.CLOCKWISE_90, Mirror.FRONT_BACK, Items.QUARTZ, new Expected(
                new BlockPos(6, 1, 6), Direction.WEST,
                new BlockPos(4, 0, 4), Direction.NORTH,
                new BlockPos(2, 3, 3), Direction.NORTH, new BlockPos(2, 3, 2), Direction.Axis.Z,
                new BlockPos(6, 2, 1), Direction.WEST));
    }

    /**
     * A shaft connector on the east face of an odd grid, turned clockwise, is on the south face with the shaft behind
     * it, and a real motor there drives that shaft.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 300)
    public void aTurnedConnectorJoinsWhatIsOnItsNewFace(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
        SubgridBlockEntity be = SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), 7);
        ServerLevel level = helper.getLevel();
        if (be == null || !ConnectorPlacement.place(be, level, Direction.EAST)) throw new IllegalStateException("no connector");
        SubgridTestSupport.placePiece(be, level, ShaftConnectorPiece.socket(Direction.EAST, 7),
                AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        helper.setBlock(new BlockPos(2, 1, 3),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.NORTH));
        BlockPos socket = ShaftConnectorPiece.socket(Direction.SOUTH, 7), anchor = ShaftConnectorPiece.anchorOn(Direction.SOUTH, 7);

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(be.transform(Rotation.CLOCKWISE_90, Mirror.NONE), "the grid was not turned"))
                .thenExecute(() -> {
                    PlacedPiece plate = be.getPieceAt(anchor.getX(), anchor.getY(), anchor.getZ());
                    helper.assertTrue(plate != null && ShaftConnectorPiece.faceOf(plate) == Direction.SOUTH
                            && plate.anchor.equals(anchor), "no connector on the south face: " + plate);
                    BlockState shaft = SubgridTestSupport.stateOf(be, socket);
                    helper.assertTrue(shaft != null && AllBlocks.SHAFT.has(shaft)
                            && shaft.getValue(RotatedPillarKineticBlock.AXIS) == Direction.Axis.Z, "shaft at " + socket + ": " + shaft);
                })
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(spinning(be, level, socket), "the real motor on the south face doesn't drive the shaft"))
                .thenSucceed();
    }

    /** Where each piece should end up, and which way it should face there. */
    private record Expected(BlockPos torch, Direction torchFacing, BlockPos stair, Direction stairFacing,
                            BlockPos motor, Direction motorFacing, BlockPos cog, Direction.Axis cogAxis,
                            BlockPos link, Direction linkFacing) {}

    private static void transformed(GameTestHelper helper, Rotation rotation, Mirror mirror, Item frequencyItem, Expected expected) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        SubgridTestSupport.placePiece(be, level, TORCH_WALL, stone);
        SubgridTestSupport.placePiece(be, level, TORCH, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        SubgridTestSupport.placePiece(be, level, STAIR, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        // The far cog first, the motor last: the order in which a phantom gone first could be taken back into a network
        // by the removal of the ones after it, were they still in the grid as it went.
        SubgridTestSupport.placePiece(be, level, FAR_COG,
                AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        SubgridTestSupport.placePiece(be, level, COG,
                AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        SubgridTestSupport.placePiece(be, level, MOTOR,
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        SubgridTestSupport.placePiece(be, level, LINK_WALL, stone);
        PlacedPiece link = SubgridTestSupport.placePiece(be, level, LINK, AllBlocks.REDSTONE_LINK.getDefaultState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH));
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);
        Frequency frequency = Frequency.of(new ItemStack(frequencyItem));
        List<KineticBlockEntity> gone = new ArrayList<>();

        GameTestSequence sequence = helper.startSequence()
                // Placed a transmitter, then made a receiver with a sneaking click, as a player does: a new link only
                // gets its behaviour when its mode changes after its first tick.
                .thenIdle(3)
                .thenExecute(() -> {
                    Player sneaking = helper.makeMockPlayer(GameType.SURVIVAL);
                    sneaking.setShiftKeyDown(true);
                    SubgridTestSupport.useOnPiece(be, level, link, sneaking);
                })
                .thenWaitUntil(() -> helper.assertTrue(linkOf(be, level, link) != null, "the link has no link behaviour yet"))
                .thenExecute(() -> {
                    LinkBehaviour behaviour = linkOf(be, level, link);
                    behaviour.setFrequency(true, new ItemStack(frequencyItem));
                    behaviour.setFrequency(false, new ItemStack(frequencyItem));
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(spinning(be, level, COG), "the cog doesn't turn before the grid is transformed"))
                .thenExecute(() -> {
                    for (PlacedPiece p : be.getPieces()) {
                        if (SubgridTestSupport.blockEntityOf(be, p, level) instanceof KineticBlockEntity k) gone.add(k);
                    }
                    helper.assertTrue(gone.size() == 3, "not 3 kinetic phantoms before the transform: " + gone.size());
                    helper.assertTrue(be.transform(rotation, mirror), "the grid was not transformed");
                })
                .thenExecute(() -> {
                    FakeLevel space = VanillaBlockPiece.buildFakeSpace(be, level);
                    BlockState torch = SubgridTestSupport.stateOf(be, expected.torch());
                    helper.assertTrue(torch != null && torch.is(Blocks.WALL_TORCH)
                            && torch.getValue(WallTorchBlock.FACING) == expected.torchFacing(), "torch at " + expected.torch() + ": " + torch);
                    helper.assertTrue(torch.canSurvive(space, space.world(expected.torch())), "the torch lost its wall");
                    BlockState stair = SubgridTestSupport.stateOf(be, expected.stair());
                    helper.assertTrue(stair != null && stair.is(Blocks.OAK_STAIRS)
                            && stair.getValue(StairBlock.FACING) == expected.stairFacing(), "stair at " + expected.stair() + ": " + stair);
                    BlockState motor = SubgridTestSupport.stateOf(be, expected.motor());
                    helper.assertTrue(motor != null && AllBlocks.CREATIVE_MOTOR.has(motor)
                            && motor.getValue(DirectionalKineticBlock.FACING) == expected.motorFacing(), "motor at " + expected.motor() + ": " + motor);
                    BlockState cog = SubgridTestSupport.stateOf(be, expected.cog());
                    helper.assertTrue(cog != null && AllBlocks.COGWHEEL.has(cog)
                            && cog.getValue(RotatedPillarKineticBlock.AXIS) == expected.cogAxis(), "cog at " + expected.cog() + ": " + cog);
                    BlockState turnedLink = SubgridTestSupport.stateOf(be, expected.link());
                    helper.assertTrue(turnedLink != null && AllBlocks.REDSTONE_LINK.has(turnedLink)
                            && turnedLink.getValue(DirectionalBlock.FACING) == expected.linkFacing(), "link at " + expected.link() + ": " + turnedLink);
                    helper.assertTrue(turnedLink.canSurvive(space, space.world(expected.link())), "the link lost its wall");
                    helper.assertTrue(be.getPieces().size() == 8, "the grid holds " + be.getPieces().size() + " pieces, not 8");
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(spinning(be, level, expected.cog()), "the cog doesn't turn after the grid is transformed"))
                .thenExecute(() -> {
                    for (KineticBlockEntity k : gone) {
                        helper.assertTrue(!inANetwork(level, k), "a phantom gone with the transform is still in a kinetic network: " + k);
                    }
                });
        sequence.thenExecute(() -> press(helper, player, frequency, true))
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(receiverOn(be, expected.link()), "the link stays off with the button held"))
                .thenExecute(() -> press(helper, player, frequency, false))
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(!receiverOn(be, expected.link()), "the link stays on with the button let go"))
                .thenSucceed();
    }

    /** Whether Create still counts {@code k} in any of the level's kinetic networks (TorquePropagator#networks). */
    private static boolean inANetwork(ServerLevel level, KineticBlockEntity k) {
        try {
            Field field = TorquePropagator.class.getDeclaredField("networks");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<LevelAccessor, Map<Long, KineticNetwork>> networks = (Map<LevelAccessor, Map<Long, KineticNetwork>>) field.get(null);
            Map<Long, KineticNetwork> here = networks.get(level);
            return here != null && here.values().stream().anyMatch(n -> n.members.containsKey(k) || n.sources.containsKey(k));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LinkBehaviour linkOf(SubgridBlockEntity be, ServerLevel level, PlacedPiece link) {
        return BlockEntityBehaviour.get(VanillaBlockPiece.boundPhantom(link, be, level), LinkBehaviour.TYPE);
    }

    private static boolean spinning(SubgridBlockEntity be, ServerLevel level, BlockPos cell) {
        PlacedPiece piece = be.getPieceAt(cell.getX(), cell.getY(), cell.getZ());
        return piece != null && SubgridTestSupport.blockEntityOf(be, piece, level) instanceof KineticBlockEntity k && k.getSpeed() != 0;
    }

    private static void press(GameTestHelper helper, ServerPlayer player, Frequency frequency, boolean pressed) {
        LinkedControllerServerHandler.receivePressed(helper.getLevel(), player.blockPosition(), player.getUUID(),
                List.of(Couple.create(frequency, frequency)), pressed);
    }

    private static boolean receiverOn(SubgridBlockEntity be, BlockPos cell) {
        BlockState state = SubgridTestSupport.stateOf(be, cell);
        return state != null && state.getValue(RedstoneLinkBlock.POWERED);
    }
}
