package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A grid-wide layer of meshed tiny cogwheels driven by one motor: what it costs to start, to tick and to sync. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridGearFieldCostGameTest {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    @GameTest(template = "empty_platform", timeoutTicks = 400)
    public void aFieldOfTinyCogwheelsIsCheap(GameTestHelper helper) {
        gearField(helper, 8);
    }

    @GameTest(template = "empty_platform", timeoutTicks = 400)
    public void aGridWideFieldOfSixteenthCogwheelsIsCheap(GameTestHelper helper) {
        gearField(helper, 16);
    }

    private static void gearField(GameTestHelper helper, int gridSize) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, Registration.subgridBlock(gridSize));
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof SubgridBlockEntity be)) {
            throw new IllegalStateException("no SubgridBlockEntity at " + at);
        }
        ServerLevel level = helper.getLevel();
        int size = be.getGridSize();
        BlockState cog = AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Y);
        PlacedPiece[][] cogs = new PlacedPiece[size][size];

        long placeStart = System.nanoTime();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                cogs[x][z] = SubgridTestSupport.placePiece(be, level, new BlockPos(x, 1, z), cog);
            }
        }
        long placeNanos = System.nanoTime() - placeStart;

        long motorStart = System.nanoTime();
        SubgridTestSupport.placePiece(be, level, new BlockPos(0, 0, 0),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        be.notifyUpdate();
        long motorNanos = System.nanoTime() - motorStart;

        int[] flushes = new int[2];
        long[] tickNanos = new long[1];
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> flushes[0] = be.phantomDataFlushes())
                .thenIdle(100)
                .thenExecute(() -> {
                    flushes[1] = be.phantomDataFlushes();
                    long start = System.nanoTime();
                    for (int i = 0; i < 100; i++) {
                        be.serverTick(level);
                    }
                    tickNanos[0] = (System.nanoTime() - start) / 100;
                })
                .thenExecute(() -> {
                    int spinning = 0;
                    for (PlacedPiece[] row : cogs) {
                        for (PlacedPiece piece : row) {
                            if (SubgridTestSupport.blockEntityOf(be, piece, level) instanceof KineticBlockEntity k && k.getSpeed() != 0) {
                                spinning++;
                            }
                        }
                    }
                    LOGGER.info("[gear-cost] {} cogs placed in {} ms; the motor started them in {} ms; {} spinning; "
                                    + "one grid tick takes {} us; {} data flushes in 100 steady ticks",
                            size * size, placeNanos / 1_000_000, motorNanos / 1_000_000, spinning,
                            tickNanos[0] / 1000, flushes[1] - flushes[0]);
                    helper.assertTrue(spinning == size * size, "only " + spinning + " of " + size * size + " cogs spin");
                    helper.assertTrue(motorNanos < 500_000_000L, "the motor took " + motorNanos / 1_000_000 + " ms to start the cogs");
                    // ~13 us a cog once the fake level's reflection is cached; well over 100 us before.
                    helper.assertTrue(tickNanos[0] < 40_000L * size * size,
                            "one grid tick takes " + tickNanos[0] / 1000 + " us for " + size * size + " cogs");
                })
                .thenSucceed();
    }
}
