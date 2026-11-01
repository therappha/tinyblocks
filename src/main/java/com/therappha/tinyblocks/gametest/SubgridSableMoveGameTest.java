package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * A grid moved by Sable (SubLevelAssemblyHelper#moveBlocks, as a car is assembled or set down snapped to a quarter
 * turn): its pieces are turned with it, its cogwheel turns with its motor where it now is (the phantoms left behind
 * are in no kinetic network), and its link answers a linked controller, also once broken and placed again.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridSableMoveGameTest {

    private static final BlockPos MOTOR = new BlockPos(4, 3, 5), COG = new BlockPos(5, 3, 5);
    private static final BlockPos LINK_WALL = new BlockPos(6, 2, 0), LINK = new BlockPos(6, 2, 1);

    @GameTest(template = "empty_platform", timeoutTicks = 300)
    public void aGridSetDownTurnedKeepsWorking(GameTestHelper helper) {
        // Not an item other link tests use: they run alongside, in range.
        moved(helper, Rotation.CLOCKWISE_90, Items.EMERALD);
    }

    @GameTest(template = "empty_platform", timeoutTicks = 300)
    public void aGridMovedUnturnedKeepsWorking(GameTestHelper helper) {
        moved(helper, Rotation.NONE, Items.LAPIS_LAZULI);
    }

    /**
     * Two grids, one on the other, set down turned together: two carpets side by side in the top one, each resting on
     * a stone in the one below, stay. Sable moves the top grid first: turned, its carpets tell each other they moved,
     * and each looks down, which finds its stone only once the grid below is turned too.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void stackedGridsSetDownTurnedKeepWhatRestsAcross(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SubgridBlockEntity bottom = SubgridTestSupport.placeSubgrid(helper, new BlockPos(1, 1, 1));
        SubgridBlockEntity top = SubgridTestSupport.placeSubgrid(helper, new BlockPos(1, 2, 1));
        int below = bottom.getGridSize() - 1;
        List<BlockPos> carpets = List.of(new BlockPos(1, 0, 2), new BlockPos(2, 0, 2));
        for (BlockPos carpet : carpets) {
            SubgridTestSupport.placePiece(bottom, level, carpet.atY(below), Blocks.STONE.defaultBlockState());
            SubgridTestSupport.placePiece(top, level, carpet, Blocks.WHITE_CARPET.defaultBlockState());
        }
        Rotation rotation = Rotation.CLOCKWISE_90;
        BlockPos from = bottom.getBlockPos(), to = helper.absolutePos(new BlockPos(4, 1, 4));

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    for (BlockPos carpet : carpets) {
                        helper.assertTrue(Blocks.WHITE_CARPET.defaultBlockState().equals(SubgridTestSupport.stateOf(top, carpet)),
                                "the carpet at " + carpet + " doesn't rest on the grid below before the move");
                    }
                })
                .thenExecute(() -> SubLevelAssemblyHelper.moveBlocks(level,
                        new SubLevelAssemblyHelper.AssemblyTransform(from, to, 4 - rotation.ordinal(), rotation, level),
                        List.of(from.above(), from)))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(level.getBlockEntity(to) instanceof SubgridBlockEntity
                            && level.getBlockEntity(to.above()) instanceof SubgridBlockEntity, "no grids where they were moved to");
                    SubgridBlockEntity movedBottom = (SubgridBlockEntity) level.getBlockEntity(to);
                    SubgridBlockEntity movedTop = (SubgridBlockEntity) level.getBlockEntity(to.above());
                    for (BlockPos carpet : carpets) {
                        BlockPos stoneCell = bottom.transformCell(carpet.atY(below), rotation, Mirror.NONE);
                        BlockPos carpetCell = top.transformCell(carpet, rotation, Mirror.NONE);
                        helper.assertTrue(Blocks.STONE.defaultBlockState().equals(SubgridTestSupport.stateOf(movedBottom, stoneCell)),
                                "stone at " + stoneCell + ": " + SubgridTestSupport.stateOf(movedBottom, stoneCell));
                        helper.assertTrue(Blocks.WHITE_CARPET.defaultBlockState().equals(SubgridTestSupport.stateOf(movedTop, carpetCell)),
                                "carpet at " + carpetCell + ": " + SubgridTestSupport.stateOf(movedTop, carpetCell));
                    }
                })
                .thenSucceed();
    }

    /**
     * Assembled into a real sub-level, as a car is: the motor still drives the cog there, the link still answers, also
     * once placed again, and both still work after reopening the world.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 400)
    public void aGridAssembledIntoASubLevelKeepsWorking(GameTestHelper helper) {
        SubgridBlockEntity source = built(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, source);
        Frequency frequency = Frequency.of(new ItemStack(Items.GLOW_INK_SAC));
        SubgridBlockEntity[] grid = {source};
        Object[] subLevel = new Object[1];

        GameTestSequence sequence = helper.startSequence();
        placeReceiver(helper, sequence, grid, LINK, Direction.SOUTH, Items.GLOW_INK_SAC);
        sequence.thenIdle(10)
                .thenExecute(() -> helper.assertTrue(spinning(source, level, COG), "the cog doesn't turn before assembling"))
                .thenExecute(() -> {
                    BlockPos at = source.getBlockPos();
                    subLevel[0] = assemble(level, at);
                    BlockPos plotPos = (BlockPos) call(call(subLevel[0], "getPlot"), "getCenterBlock");
                    helper.assertTrue(level.getBlockEntity(plotPos) instanceof SubgridBlockEntity, "no grid in the sub-level at " + plotPos);
                    grid[0] = (SubgridBlockEntity) level.getBlockEntity(plotPos);
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(spinning(grid[0], level, COG), "the cog doesn't turn in the sub-level"));
        expectAnswer(helper, sequence, grid, LINK, player, frequency, "the assembled link");
        sequence.thenExecute(() -> grid[0].removePieceAt(LINK.getX(), LINK.getY(), LINK.getZ())).thenIdle(2);
        placeReceiver(helper, sequence, grid, LINK, Direction.SOUTH, Items.GLOW_INK_SAC);
        expectAnswer(helper, sequence, grid, LINK, player, frequency, "the link placed again in the sub-level");
        sequence.thenExecute(() -> {
            // Leave the world and join again.
            BlockPos pos = grid[0].getBlockPos();
            CompoundTag saved = grid[0].saveWithFullMetadata(level.registryAccess());
            grid[0].onChunkUnloaded();
            level.removeBlockEntity(pos);
            level.setBlockEntity(BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess()));
            grid[0] = (SubgridBlockEntity) level.getBlockEntity(pos);
        }).thenIdle(10)
                .thenExecute(() -> helper.assertTrue(spinning(grid[0], level, COG), "the cog doesn't turn after reopening the world"))
                .thenExecute(() -> helper.assertTrue(grid[0].getPieces().size() == 4,
                        "the grid holds " + grid[0].getPieces().size() + " pieces after reopening the world, not 4"));
        expectAnswer(helper, sequence, grid, LINK, player, frequency, "the link after reopening the world");
        sequence.thenExecute(() -> remove(level, subLevel[0])).thenSucceed();
    }

    // Sable's companion library (its bounding boxes, the interfaces of a sub-level's plot) is inside Sable's own jar,
    // not on the compile classpath: what needs it is reached by reflection.

    /** SubLevelAssemblyHelper#assembleBlocks of the one block at {@code at}: the new sub-level. */
    private static Object assemble(ServerLevel level, BlockPos at) {
        try {
            ClassLoader loader = SubLevelAssemblyHelper.class.getClassLoader();
            Object bounds = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3i", true, loader)
                    .getConstructor(BlockPos.class, BlockPos.class).newInstance(at, at);
            return SubLevelAssemblyHelper.class.getMethod("assembleBlocks", ServerLevel.class, BlockPos.class, Iterable.class,
                            Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3ic", true, loader))
                    .invoke(null, level, at, List.of(at), bounds);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void remove(ServerLevel level, Object subLevel) {
        SubLevelContainer.getContainer(level).removeSubLevel((SubLevel) subLevel, SubLevelRemovalReason.REMOVED);
    }

    private static Object call(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A grid with a motor driving a cog and a stone to hang a link on. */
    private static SubgridBlockEntity built(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        SubgridTestSupport.placePiece(be, level, COG,
                AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        SubgridTestSupport.placePiece(be, level, MOTOR,
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        SubgridTestSupport.placePiece(be, level, LINK_WALL, Blocks.STONE.defaultBlockState());
        return be;
    }

    private static void moved(GameTestHelper helper, Rotation rotation, Item frequencyItem) {
        SubgridBlockEntity source = built(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, source);
        Frequency frequency = Frequency.of(new ItemStack(frequencyItem));
        BlockPos motorCell = source.transformCell(MOTOR, rotation, Mirror.NONE);
        BlockPos cogCell = source.transformCell(COG, rotation, Mirror.NONE);
        BlockPos linkCell = source.transformCell(LINK, rotation, Mirror.NONE);
        BlockPos from = source.getBlockPos(), to = helper.absolutePos(new BlockPos(4, 1, 4));
        SubgridBlockEntity[] grid = {source};
        List<KineticBlockEntity> gone = new ArrayList<>();

        GameTestSequence sequence = helper.startSequence();
        placeReceiver(helper, sequence, grid, LINK, Direction.SOUTH, frequencyItem);
        sequence.thenIdle(10)
                .thenExecute(() -> helper.assertTrue(spinning(source, level, COG), "the cog doesn't turn before the move"))
                .thenExecute(() -> {
                    for (PlacedPiece p : source.getPieces()) {
                        if (SubgridTestSupport.blockEntityOf(source, p, level) instanceof KineticBlockEntity k) gone.add(k);
                    }
                    // What Simulated's SimAssemblyHelper#disassembleSubLevel gives Sable for a rotation.
                    int angle = rotation == Rotation.NONE ? 0 : 4 - rotation.ordinal();
                    SubLevelAssemblyHelper.moveBlocks(level,
                            new SubLevelAssemblyHelper.AssemblyTransform(from, to, angle, rotation, level), List.of(from));
                    helper.assertTrue(level.getBlockState(from).isAir(), "the grid is still where it was");
                    helper.assertTrue(level.getBlockEntity(to) instanceof SubgridBlockEntity, "no grid where it was moved to");
                    grid[0] = (SubgridBlockEntity) level.getBlockEntity(to);
                    BlockState motor = SubgridTestSupport.stateOf(grid[0], motorCell);
                    helper.assertTrue(motor != null && AllBlocks.CREATIVE_MOTOR.has(motor)
                            && motor.getValue(DirectionalKineticBlock.FACING) == rotation.rotate(Direction.EAST),
                            "motor at " + motorCell + ": " + motor);
                    BlockState cog = SubgridTestSupport.stateOf(grid[0], cogCell);
                    helper.assertTrue(cog != null && AllBlocks.COGWHEEL.has(cog)
                            && cog.getValue(RotatedPillarKineticBlock.AXIS) == rotation.rotate(Direction.EAST).getAxis(),
                            "cog at " + cogCell + ": " + cog);
                    BlockState link = SubgridTestSupport.stateOf(grid[0], linkCell);
                    helper.assertTrue(link != null && AllBlocks.REDSTONE_LINK.has(link)
                            && link.getValue(DirectionalBlock.FACING) == rotation.rotate(Direction.SOUTH),
                            "link at " + linkCell + ": " + link);
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(spinning(grid[0], level, cogCell), "the cog doesn't turn after the move"))
                .thenExecute(() -> {
                    for (KineticBlockEntity k : gone) {
                        helper.assertTrue(!SubgridTransformGameTest.inANetwork(level, k),
                                "a phantom left where the grid was is still in a kinetic network: " + k);
                    }
                });
        expectAnswer(helper, sequence, grid, linkCell, player, frequency, "the moved link");
        sequence.thenExecute(() -> grid[0].removePieceAt(linkCell.getX(), linkCell.getY(), linkCell.getZ()))
                .thenIdle(2);
        placeReceiver(helper, sequence, grid, linkCell, rotation.rotate(Direction.SOUTH), frequencyItem);
        expectAnswer(helper, sequence, grid, linkCell, player, frequency, "the link placed again after the move");
        sequence.thenSucceed();
    }

    /** Placed as a transmitter, made a receiver with a sneaking click as a player does, then tuned. */
    private static void placeReceiver(GameTestHelper helper, GameTestSequence sequence, SubgridBlockEntity[] grid,
                                      BlockPos cell, Direction facing, Item frequencyItem) {
        ServerLevel level = helper.getLevel();
        PlacedPiece[] link = new PlacedPiece[1];
        sequence.thenExecute(() -> link[0] = SubgridTestSupport.placePiece(grid[0], level, cell,
                        AllBlocks.REDSTONE_LINK.getDefaultState().setValue(DirectionalBlock.FACING, facing)))
                .thenIdle(3)
                .thenExecute(() -> {
                    Player sneaking = helper.makeMockPlayer(GameType.SURVIVAL);
                    sneaking.setShiftKeyDown(true);
                    SubgridTestSupport.useOnPiece(grid[0], level, link[0], sneaking);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    LinkBehaviour behaviour = BlockEntityBehaviour.get(
                            VanillaBlockPiece.boundPhantom(link[0], grid[0], level), LinkBehaviour.TYPE);
                    behaviour.setFrequency(true, new ItemStack(frequencyItem));
                    behaviour.setFrequency(false, new ItemStack(frequencyItem));
                });
    }

    /** The controller's button held: the receiver turns on. Let go: it turns off. */
    private static void expectAnswer(GameTestHelper helper, GameTestSequence sequence, SubgridBlockEntity[] grid,
                                     BlockPos cell, ServerPlayer player, Frequency frequency, String which) {
        sequence.thenExecute(() -> press(helper, player, frequency, true))
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(receiverOn(grid[0], cell), which + " stays off with the button held"))
                .thenExecute(() -> press(helper, player, frequency, false))
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(!receiverOn(grid[0], cell), which + " stays on with the button let go"));
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
