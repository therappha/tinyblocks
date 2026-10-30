package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * What grids full of pieces cost (issue #64): placing them, a server tick, the shape queries, and how many faces a
 * renderer has to draw. Each scenario logs a "[many-cost]" line; the bounds are loose, the numbers are the point.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridManyPiecesCostGameTest {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static SubgridBlockEntity grid(GameTestHelper helper, BlockPos at, int size) {
        helper.setBlock(at, Registration.subgridBlock(size));
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof SubgridBlockEntity be)) {
            throw new IllegalStateException("no SubgridBlockEntity at " + at);
        }
        return be;
    }

    private static long timeTicks(SubgridBlockEntity be, ServerLevel level, int ticks) {
        long start = System.nanoTime();
        for (int i = 0; i < ticks; i++) {
            be.serverTick(level);
        }
        return (System.nanoTime() - start) / ticks;
    }

    /** Nanoseconds per outline and per collision shape query, asked through the block as vanilla asks. */
    private static long[] timeShapes(SubgridBlockEntity be, ServerLevel level, int queries) {
        BlockPos pos = be.getBlockPos();
        BlockState state = level.getBlockState(pos);
        long start = System.nanoTime();
        int sink = 0;
        for (int i = 0; i < queries; i++) {
            sink += state.getShape(level, pos, CollisionContext.empty()).isEmpty() ? 0 : 1;
        }
        long outline = (System.nanoTime() - start) / queries;
        start = System.nanoTime();
        for (int i = 0; i < queries; i++) {
            sink += state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty() ? 0 : 1;
        }
        long collision = (System.nanoTime() - start) / queries;
        if (sink < 0) LOGGER.info("unreachable");
        return new long[] {outline, collision};
    }

    /** Faces of the pieces' models a renderer draws: every face of every piece, less those it can cull. */
    private static String faces(SubgridBlockEntity be) {
        int all = 6 * be.getPieces().size();
        int drawn = com.therappha.tinyblocks.subgrid.PieceFaceCulling.countDrawnFaces(be);
        return drawn + "/" + all;
    }

    /** A 16-grid half full of stone (2048 pieces) and an 8-grid full of it (512). */
    @GameTest(template = "empty_platform", timeoutTicks = 400)
    public void gridsFullOfStoneAreCheap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SubgridBlockEntity big = grid(helper, new BlockPos(1, 1, 1), 16);
        SubgridBlockEntity small = grid(helper, new BlockPos(3, 1, 3), 8);
        BlockState stone = Blocks.STONE.defaultBlockState();

        long start = System.nanoTime();
        for (int y = 0; y < 8; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++)
                    SubgridTestSupport.placePiece(big, level, new BlockPos(x, y, z), stone);
        long bigPlace = System.nanoTime() - start;
        start = System.nanoTime();
        for (int y = 0; y < 8; y++)
            for (int z = 0; z < 8; z++)
                for (int x = 0; x < 8; x++)
                    SubgridTestSupport.placePiece(small, level, new BlockPos(x, y, z), stone);
        long smallPlace = System.nanoTime() - start;

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    long bigTick = timeTicks(big, level, 100), smallTick = timeTicks(small, level, 100);
                    long[] bigShape = timeShapes(big, level, 1000), smallShape = timeShapes(small, level, 1000);
                    LOGGER.info("[many-cost] stone 16-grid x2048: placed in {} ms, tick {} us, outline {} ns, collision {} ns, faces {}",
                            bigPlace / 1_000_000, bigTick / 1000, bigShape[0], bigShape[1], faces(big));
                    LOGGER.info("[many-cost] stone 8-grid x512: placed in {} ms, tick {} us, outline {} ns, collision {} ns, faces {}",
                            smallPlace / 1_000_000, smallTick / 1000, smallShape[0], smallShape[1], faces(small));
                    helper.assertTrue(big.getPieces().size() == 2048 && small.getPieces().size() == 512, "pieces went missing");
                    helper.assertTrue(bigTick < 5_000_000, "a tick of 2048 stone pieces takes " + bigTick / 1000 + " us");
                })
                .thenSucceed();
    }

    /** A 16-grid: a layer of cogwheels turned by a motor under 6 layers of stone (1793 pieces, 257 of them ticking). */
    @GameTest(template = "empty_platform", timeoutTicks = 400)
    public void cogwheelsUnderStoneAreCheap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SubgridBlockEntity be = grid(helper, new BlockPos(2, 1, 2), 16);
        BlockState cog = AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Y);
        BlockState stone = Blocks.STONE.defaultBlockState();
        List<PlacedPiece> cogs = new ArrayList<>();

        long start = System.nanoTime();
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++)
                cogs.add(SubgridTestSupport.placePiece(be, level, new BlockPos(x, 1, z), cog));
        for (int y = 2; y < 8; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++)
                    SubgridTestSupport.placePiece(be, level, new BlockPos(x, y, z), stone);
        SubgridTestSupport.placePiece(be, level, new BlockPos(0, 0, 0),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        be.notifyUpdate();
        long place = System.nanoTime() - start;

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    long tick = timeTicks(be, level, 50);
                    long[] shape = timeShapes(be, level, 1000);
                    int spinning = 0;
                    for (PlacedPiece piece : cogs) {
                        if (SubgridTestSupport.blockEntityOf(be, piece, level) instanceof KineticBlockEntity k && k.getSpeed() != 0) {
                            spinning++;
                        }
                    }
                    LOGGER.info("[many-cost] cogs+stone 16-grid x{}: placed in {} ms, tick {} us, outline {} ns, collision {} ns, "
                                    + "faces {}, {} of {} cogs spinning",
                            be.getPieces().size(), place / 1_000_000, tick / 1000, shape[0], shape[1], faces(be), spinning, cogs.size());
                    helper.assertTrue(spinning == cogs.size(), "only " + spinning + " of " + cogs.size() + " cogs spin");
                    helper.assertTrue(tick < 100_000_000, "a tick takes " + tick / 1000 + " us");
                })
                .thenSucceed();
    }

    private static BlockState link(boolean receiver) {
        return AllBlocks.REDSTONE_LINK.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP)
                .setValue(RedstoneLinkBlock.RECEIVER, receiver);
    }

    private static void tune(net.minecraft.world.level.block.entity.BlockEntity linkEntity) {
        LinkBehaviour behaviour = BlockEntityBehaviour.get(linkEntity, LinkBehaviour.TYPE);
        if (behaviour == null) {
            throw new IllegalStateException("no link behaviour on " + linkEntity + " / " + (linkEntity == null ? null : linkEntity.getBlockState()));
        }
        behaviour.setFrequency(true, new ItemStack(Items.EMERALD));
        behaviour.setFrequency(false, new ItemStack(Items.EMERALD));
    }

    /**
     * An 8-grid of redstone over a stone floor and under stone (453 pieces): a lever drives a dust line into a lamp,
     * and a tiny link pair. What a lever flip costs, and a steady tick.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 400)
    public void redstoneAmongStoneIsCheap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SubgridBlockEntity be = grid(helper, new BlockPos(2, 1, 2), 8);
        BlockState stone = Blocks.STONE.defaultBlockState();
        long start = System.nanoTime();
        for (int z = 0; z < 8; z++)
            for (int x = 0; x < 8; x++)
                SubgridTestSupport.placePiece(be, level, new BlockPos(x, 0, z), stone);
        for (int y = 3; y < 8; y++)
            for (int z = 0; z < 8; z++)
                for (int x = 0; x < 8; x++)
                    SubgridTestSupport.placePiece(be, level, new BlockPos(x, y, z), stone);
        PlacedPiece lever = SubgridTestSupport.placePiece(be, level, new BlockPos(0, 1, 4), Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.EAST));
        for (int x = 1; x <= 6; x++) {
            SubgridTestSupport.placePiece(be, level, new BlockPos(x, 1, 4), Blocks.REDSTONE_WIRE.defaultBlockState());
        }
        BlockPos lampAt = new BlockPos(7, 1, 4);
        SubgridTestSupport.placePiece(be, level, lampAt, Blocks.REDSTONE_LAMP.defaultBlockState());
        PlacedPiece transmitter = SubgridTestSupport.placePiece(be, level, new BlockPos(1, 1, 1), link(false));
        PlacedPiece receiver = SubgridTestSupport.placePiece(be, level, new BlockPos(6, 1, 1), link(false));
        long place = System.nanoTime() - start;
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        long[] flip = new long[2];

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    // As a player turns a link into a receiver: a sneaking use (SubgridLinkCostGameTest).
                    Player sneaking = helper.makeMockPlayer(GameType.SURVIVAL);
                    sneaking.setShiftKeyDown(true);
                    SubgridTestSupport.useOnPiece(be, level, receiver, sneaking);
                    tune(SubgridTestSupport.blockEntityOf(be, transmitter, level));
                })
                .thenIdle(3)
                .thenExecute(() -> tune(SubgridTestSupport.blockEntityOf(be, receiver, level)))
                .thenIdle(5)
                .thenExecute(() -> {
                    long t = System.nanoTime();
                    SubgridTestSupport.useOnPiece(be, level, lever, player);
                    flip[0] = System.nanoTime() - t;
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(SubgridTestSupport.stateOf(be, lampAt).getValue(RedstoneLampBlock.LIT),
                        "the lamp at the end of the dust line never lit"))
                .thenExecute(() -> {
                    long t = System.nanoTime();
                    SubgridTestSupport.useOnPiece(be, level, lever, player);
                    flip[1] = System.nanoTime() - t;
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    long tick = timeTicks(be, level, 100);
                    long[] shape = timeShapes(be, level, 1000);
                    LOGGER.info("[many-cost] redstone 8-grid x{}: placed in {} ms, lever on {} us, off {} us, tick {} us, "
                                    + "outline {} ns, collision {} ns, faces {}",
                            be.getPieces().size(), place / 1_000_000, flip[0] / 1000, flip[1] / 1000, tick / 1000,
                            shape[0], shape[1], faces(be));
                    helper.assertTrue(tick < 5_000_000, "a tick takes " + tick / 1000 + " us");
                })
                .thenSucceed();
    }

    /** 9 neighbouring 8-grids, each half full of stone (2304 pieces): a tick of all of them, and their shapes. */
    @GameTest(template = "empty_platform", timeoutTicks = 400)
    public void manyNeighbouringGridsAreCheap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        List<SubgridBlockEntity> grids = new ArrayList<>();
        long start = System.nanoTime();
        for (int gx = 0; gx < 3; gx++)
            for (int gz = 0; gz < 3; gz++) {
                SubgridBlockEntity be = grid(helper, new BlockPos(1 + gx, 1, 1 + gz), 8);
                grids.add(be);
                for (int y = 0; y < 4; y++)
                    for (int z = 0; z < 8; z++)
                        for (int x = 0; x < 8; x++)
                            SubgridTestSupport.placePiece(be, level, new BlockPos(x, y, z), stone);
            }
        long place = System.nanoTime() - start;

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    long t = System.nanoTime();
                    for (int i = 0; i < 100; i++) {
                        for (SubgridBlockEntity be : grids) be.serverTick(level);
                    }
                    long tick = (System.nanoTime() - t) / 100;
                    long outline = 0, collision = 0;
                    int drawn = 0, all = 0;
                    for (SubgridBlockEntity be : grids) {
                        long[] shape = timeShapes(be, level, 200);
                        outline += shape[0];
                        collision += shape[1];
                        drawn += com.therappha.tinyblocks.subgrid.PieceFaceCulling.countDrawnFaces(be);
                        all += 6 * be.getPieces().size();
                    }
                    LOGGER.info("[many-cost] 9 stone 8-grids x2304: placed in {} ms, tick of all {} us, outline {} ns, collision {} ns "
                                    + "(per grid), faces {}/{}",
                            place / 1_000_000, tick / 1000, outline / grids.size(), collision / grids.size(), drawn, all);
                    helper.assertTrue(tick < 5_000_000, "a tick of 9 grids takes " + tick / 1000 + " us");
                })
                .thenSucceed();
    }
}
