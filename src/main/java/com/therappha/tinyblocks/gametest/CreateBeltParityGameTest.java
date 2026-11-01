package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Create belts against real ones, tick by tick: in a grid left where it was built, and in one moved after it was running
 * (a Grid Save, Sable setting it down, Sable assembling it into a sub-level), which is where a belt stopped turning. Grids
 * of size 16 hold the whole belt; size 8 runs it across seams.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class CreateBeltParityGameTest {

    private static final int FROM = 2, TO = 11, Z = 4;

    private static BlockState belt(BeltPart part) {
        return AllBlocks.BELT.getDefaultState().setValue(BeltBlock.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BeltBlock.SLOPE, BeltSlope.HORIZONTAL).setValue(BeltBlock.PART, part);
    }

    /** A motor into the belt's first pulley, and a shaft out of its last, so turning has to go the whole way along. */
    private static ParityScene scene() {
        return ParityScene.of("belt", s -> {
            for (int x = FROM; x <= TO; x++) {
                s.raw(x, 1, Z, belt(x == FROM ? BeltPart.START : x == TO ? BeltPart.END : BeltPart.MIDDLE));
            }
            s.raw(FROM, 1, Z - 1, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
            s.raw(TO, 1, Z + 1, AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z));
            s.settleTicks = 80;
            s.runFor(60);
        });
    }

    /**
     * The same move close to the world origin, where a phantom's virtual position falls in chunks that are loaded: the
     * belt was seen to survive a Grid Save here and not 12 million blocks away. Every test above lands far out.
     */
    @net.minecraft.gametest.framework.GameTest(template = "empty_platform", timeoutTicks = 200)
    public void parityBeltMovedNearTheWorldOrigin(net.minecraft.gametest.framework.GameTestHelper helper) {
        var level = helper.getLevel();
        net.minecraft.core.BlockPos from = new net.minecraft.core.BlockPos(1, 100, 1), to = new net.minecraft.core.BlockPos(1, 100, 6);
        level.setChunkForced(0, 0, true);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        for (var at : List.of(from, to)) level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        var be = com.therappha.tinyblocks.subgrid.SubgridBlockEntity.createAt(level, from, 16);
        if (be == null) throw new IllegalStateException("no grid");
        for (var p : scene().blocks) SubgridTestSupport.placePiece(be, level, p.cell(), p.state());
        helper.startSequence().thenIdle(40).thenExecute(() -> {
            var save = new net.minecraft.world.item.ItemStack(com.therappha.tinyblocks.setup.Registration.GRID_SAVE.get());
            helper.assertTrue(com.therappha.tinyblocks.items.GridSaveItem.pickUp(level, from, save, player), "nothing taken");
            helper.assertTrue(com.therappha.tinyblocks.items.GridSaveItem.place(level, to, Direction.UP, save, player), "nothing placed");
        }).thenIdle(40).thenExecute(() -> {
            var grid = (com.therappha.tinyblocks.subgrid.SubgridBlockEntity) level.getBlockEntity(to);
            var speeds = new ArrayList<String>();
            boolean still = false;
            for (int x = FROM; x <= TO; x++) {
                var piece = grid.getPieceAt(x, 1, Z);
                float speed = SubgridTestSupport.blockEntityOf(grid, piece, level) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k ? k.getSpeed() : 0;
                speeds.add(x + "=" + speed);
                still |= speed == 0;
            }
            level.setBlock(to, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setChunkForced(0, 0, false);
            helper.assertTrue(!still, "belt moved near the origin: " + speeds);
        }).thenSucceed();
    }

    private static int belts(com.therappha.tinyblocks.subgrid.SubgridBlockEntity grid) {
        int count = 0;
        for (var piece : grid.getPieces()) {
            if (piece.runtimeState instanceof BlockState state && AllBlocks.BELT.has(state)) count++;
        }
        return count;
    }

    /** A belt across two grids, one of them taken away: as a belt with a segment broken in the world, none of it stays. */
    @net.minecraft.gametest.framework.GameTest(template = "empty_platform", timeoutTicks = 200)
    public void parityBeltCutByTakingOneGridBreaks(net.minecraft.gametest.framework.GameTestHelper helper) {
        var level = helper.getLevel();
        net.minecraft.core.BlockPos westAt = new net.minecraft.core.BlockPos(1, 2, 1), eastAt = westAt.east(), to = new net.minecraft.core.BlockPos(3, 2, 3);
        helper.setBlock(westAt, net.minecraft.world.level.block.Blocks.AIR);
        helper.setBlock(eastAt, net.minecraft.world.level.block.Blocks.AIR);
        var west = com.therappha.tinyblocks.subgrid.SubgridBlockEntity.createAt(level, helper.absolutePos(westAt), 8);
        var east = com.therappha.tinyblocks.subgrid.SubgridBlockEntity.createAt(level, helper.absolutePos(eastAt), 8);
        int first = 4, last = 11;
        for (int x = first; x <= last; x++) {
            var cell = new net.minecraft.core.BlockPos(x % 8, 1, 4);
            SubgridTestSupport.placePiece(x < 8 ? west : east, level, cell, belt(x == first ? BeltPart.START : x == last ? BeltPart.END : BeltPart.MIDDLE));
        }
        var player = SubgridTestSupport.serverPlayer(helper, west);
        var save = new net.minecraft.world.item.ItemStack(com.therappha.tinyblocks.setup.Registration.GRID_SAVE.get());
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            helper.assertTrue(belts(west) == 4 && belts(east) == 4, "the belt wasn't built: " + belts(west) + " and " + belts(east));
            helper.assertTrue(com.therappha.tinyblocks.items.GridSaveItem.pickUp(level, helper.absolutePos(eastAt), save, player), "nothing taken");
        }).thenIdle(20).thenExecute(() -> {
            helper.assertTrue(belts(west) == 0, belts(west) + " segments of the cut belt stayed in the grid left behind");
            helper.assertTrue(com.therappha.tinyblocks.items.GridSaveItem.place(level, helper.absolutePos(to), Direction.UP, save, player), "nothing placed");
        }).thenIdle(20).thenExecute(() -> {
            var moved = level.getBlockEntity(helper.absolutePos(to)) instanceof com.therappha.tinyblocks.subgrid.SubgridBlockEntity grid ? grid : null;
            helper.assertTrue(moved == null || belts(moved) == 0, (moved == null ? 0 : belts(moved)) + " segments of the cut belt stayed in the grid placed");
        }).thenSucceed();
    }

    @GameTestGenerator
    public Collection<TestFunction> belts() {
        List<TestFunction> tests = new ArrayList<>();
        for (ParityRun.Layout layout : List.of(ParityRun.Layout.ONE_16, ParityRun.Layout.FOUR_8, ParityRun.Layout.FOUR_8_SHIFTED)) {
            for (ParitySpace.Move move : ParitySpace.Move.values()) {
                tests.add(RedstoneParityGameTest.test(scene(), layout, move));
            }
        }
        return tests;
    }
}
