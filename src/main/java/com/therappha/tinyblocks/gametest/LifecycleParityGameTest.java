package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A tiny copy that goes through something halfway through a scene, while it is busy: saved and loaded back, taken with a
 * Grid Save and put down, set down by Sable, assembled into a sub-level. Past that tick it must go on exactly as the
 * real copies, which stayed where they were. What was on its way (a scheduled tick, an item in a hopper) is the point.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class LifecycleParityGameTest {

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

    private static BlockState lever() {
        return Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH);
    }

    private static BlockState hopper(Direction to) {
        return Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, to);
    }

    static List<ParityScene> scenes() {
        List<ParityScene> scenes = new ArrayList<>();

        scenes.add(ParityScene.of("moved_while_water_runs", s -> {
            s.fill(0, 0, 3, 15, 0, 5, STONE);
            s.fill(0, 1, 3, 15, 1, 3, STONE).fill(0, 1, 5, 15, 1, 5, STONE).put(0, 1, 4, STONE).put(15, 1, 4, STONE);
            s.raw(2, 1, 4, Blocks.WATER.defaultBlockState()).runFor(60).moveAt(12).provenBy(8, 1, 4, "water");
        }));

        scenes.add(ParityScene.of("moved_while_a_repeater_waits", s -> {
            s.fill(0, 0, 4, 12, 0, 4, STONE);
            s.put(0, 1, 4, lever()).put(1, 1, 4, Blocks.REDSTONE_WIRE.defaultBlockState());
            s.put(2, 1, 4, Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.WEST).setValue(RepeaterBlock.DELAY, 4));
            s.put(3, 1, 4, Blocks.REDSTONE_LAMP.defaultBlockState());
            s.use(5, 0, 1, 4).moveAt(7).runFor(30).provenBy(3, 1, 4, "state.lit=true");
        }));

        scenes.add(ParityScene.of("moved_while_hoppers_carry", s -> {
            s.fill(3, 0, 4, 13, 0, 4, STONE);
            for (int x = 3; x < 12; x++) s.put(x, 1, 4, hopper(Direction.EAST));
            s.put(12, 1, 4, Blocks.CHEST.defaultBlockState()).item(3, 1, 4, 0, Items.APPLE, 6);
            s.moveAt(20).runFor(160).provenBy(12, 1, 4, "applex6");
        }));

        scenes.add(ParityScene.of("moved_with_a_door_open", s -> {
            s.fill(3, 0, 3, 8, 0, 5, STONE);
            BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
            s.raw(4, 1, 4, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)).raw(4, 2, 4, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            s.use(5, 4, 1, 4).moveAt(10).use(20, 4, 2, 4).runFor(30);
        }));

        scenes.add(ParityScene.of("moved_with_full_containers", s -> {
            s.fill(3, 0, 3, 9, 0, 5, STONE);
            s.put(4, 1, 4, Blocks.CHEST.defaultBlockState()).item(4, 1, 4, 3, Items.DIAMOND, 7);
            s.put(5, 1, 4, Blocks.FURNACE.defaultBlockState()).item(5, 1, 4, 0, Items.RAW_IRON, 2).item(5, 1, 4, 1, Items.COAL, 1);
            s.put(6, 1, 4, Blocks.DISPENSER.defaultBlockState()).item(6, 1, 4, 8, Items.ARROW, 16);
            s.moveAt(40).runFor(100);
        }));

        scenes.add(ParityScene.of("moved_while_gears_turn", s -> {
            s.raw(2, 2, 7, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
            BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X);
            s.raw(3, 2, 7, shaft).raw(4, 2, 7, AllBlocks.GEARBOX.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Y));
            s.raw(5, 2, 7, shaft).raw(6, 2, 7, shaft);
            s.settleTicks = 20;
            // The grid is back and turning a while after it moved, as when a chunk loads: it counts from there.
            s.moveAt(10).compareOnlyAtTheEnd().runFor(100).provenBy(6, 2, 7, "working.speed=");
        }));

        scenes.add(ParityScene.of("moved_with_a_lit_redstone_torch_line", s -> {
            s.fill(0, 0, 4, 12, 0, 4, STONE);
            // The lamp first: a plain setBlock doesn't make a block look at its input, and the moved copy would.
            s.put(6, 1, 4, Blocks.REDSTONE_LAMP.defaultBlockState()).put(0, 1, 4, Blocks.REDSTONE_TORCH.defaultBlockState());
            for (int x = 1; x < 6; x++) s.put(x, 1, 4, Blocks.REDSTONE_WIRE.defaultBlockState());
            s.moveAt(15).breakAt(25, 3, 1, 4).runFor(40);
        }));

        return scenes;
    }

    /**
     * Assembling takes a grid out of the world's ticking for a tick: whatever is running comes out one tick behind for
     * good, with nothing lost. What it comes to is compared, not when.
     */
    private static ParityScene atTheEnd(ParityScene scene) {
        return scene.name.equals("moved_while_hoppers_carry") ? scene.compareOnlyAtTheEnd() : scene;
    }

    @GameTestGenerator
    public Collection<TestFunction> lifecycle() {
        List<TestFunction> tests = new ArrayList<>();
        for (ParityScene scene : scenes()) {
            for (ParityRun.Layout layout : List.of(ParityRun.Layout.ONE_16, ParityRun.Layout.FOUR_8_SHIFTED)) {
                for (ParitySpace.Move move : List.of(ParitySpace.Move.RELOAD, ParitySpace.Move.GRID_SAVE, ParitySpace.Move.SABLE_MOVE, ParitySpace.Move.SABLE_ASSEMBLE)) {
                    tests.add(RedstoneParityGameTest.test(move == ParitySpace.Move.SABLE_ASSEMBLE ? atTheEnd(scene) : scene, layout, move));
                }
            }
        }
        return tests;
    }
}
