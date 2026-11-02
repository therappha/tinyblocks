package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The same redstone built in the real world and in tiny grids, cut in several ways, run side by side and compared tick
 * by tick with SpaceDiff. Each scene crosses seams between grids on purpose. A report per test goes to run/space-diff.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public class RedstoneParityGameTest {

    private static final BlockState FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WIRE = Blocks.REDSTONE_WIRE.defaultBlockState();
    private static final BlockState LAMP = Blocks.REDSTONE_LAMP.defaultBlockState();

    private static BlockState lever() {
        return Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH);
    }

    private static BlockState button() {
        return Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR).setValue(ButtonBlock.FACING, Direction.NORTH);
    }

    /** {@code from} is the side its input comes from. */
    private static BlockState repeater(Direction from, int delay) {
        return Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, from).setValue(RepeaterBlock.DELAY, delay);
    }

    private static BlockState comparator(Direction from, ComparatorMode mode) {
        return Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, from).setValue(ComparatorBlock.MODE, mode);
    }

    private static BlockState hopper(Direction to) {
        return Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, to);
    }

    private static BlockState wallTorch(Direction facing) {
        return Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(RedstoneWallTorchBlock.FACING, facing);
    }

    private static BlockState torch() {
        return Blocks.REDSTONE_TORCH.defaultBlockState();
    }

    /** A floor under x0..x1 along z, then a lever, dust, and a lamp. */
    private static void line(ParityScene s, int z, int x0, int x1) {
        s.fill(x0, 0, z, x1, 0, z, FLOOR);
        s.put(x0, 1, z, lever());
        for (int x = x0 + 1; x < x1; x++) s.put(x, 1, z, WIRE);
        s.put(x1, 1, z, LAMP);
    }

    static List<ParityScene> scenes() {
        List<ParityScene> scenes = new ArrayList<>();

        scenes.add(ParityScene.of("wire_line", s -> {
            line(s, 4, 0, 15);
            s.use(5, 0, 1, 4).use(25, 0, 1, 4).runFor(45);
        }));

        scenes.add(ParityScene.of("steps_up_down", s -> {
            s.fill(0, 0, 4, 14, 0, 4, FLOOR);
            s.put(0, 1, 4, lever()).put(1, 1, 4, WIRE);
            for (int i = 1; i <= 5; i++) {
                s.fill(1 + i, 1, 4, 1 + i, i, 4, STONE).put(1 + i, i + 1, 4, WIRE);
            }
            for (int i = 4; i >= 1; i--) {
                s.fill(11 - i, 1, 4, 11 - i, i, 4, STONE).put(11 - i, i + 1, 4, WIRE);
            }
            s.put(11, 1, 4, WIRE).put(12, 1, 4, WIRE).put(13, 1, 4, LAMP);
            s.use(5, 0, 1, 4).use(30, 0, 1, 4).runFor(50);
        }));

        scenes.add(ParityScene.of("steps_cut_by_a_block", s -> {
            s.fill(0, 0, 4, 10, 0, 4, FLOOR);
            s.put(0, 1, 4, lever()).put(1, 1, 4, WIRE);
            s.put(2, 1, 4, STONE).put(2, 2, 4, WIRE).put(3, 1, 4, WIRE).put(4, 1, 4, LAMP);
            s.placeAt(10, 1, 2, 4, STONE).use(15, 0, 1, 4).breakAt(25, 1, 2, 4).use(35, 0, 1, 4).use(45, 0, 1, 4).runFor(60);
        }));

        scenes.add(ParityScene.of("upside_down_stairs", s -> {
            BlockState stairs = Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP)
                    .setValue(StairBlock.FACING, Direction.EAST);
            s.fill(0, 0, 4, 12, 0, 4, FLOOR);
            s.put(0, 1, 4, lever()).put(1, 1, 4, WIRE);
            for (int x = 2; x <= 9; x++) s.put(x, 1, 4, stairs).put(x, 2, 4, WIRE);
            s.put(10, 1, 4, WIRE).put(11, 1, 4, LAMP);
            s.use(5, 0, 1, 4).use(30, 0, 1, 4).runFor(50);
        }));

        scenes.add(ParityScene.of("dust_beside_stairs", s -> {
            BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
            s.fill(0, 0, 3, 12, 0, 3, FLOOR);
            line(s, 4, 0, 12);
            for (int x = 2; x <= 10; x += 2) s.put(x, 1, 3, stairs).put(x, 2, 3, WIRE);
            s.use(5, 0, 1, 4).use(30, 0, 1, 4).runFor(50);
        }));

        for (var block : List.of(Blocks.GLOWSTONE, Blocks.GLASS, Blocks.SEA_LANTERN)) {
            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
            scenes.add(ParityScene.of("tower_" + name, s -> {
                // Climbs block to block, then tries to come back down the other side, which only dust on solid blocks can.
                s.fill(0, 0, 4, 15, 0, 4, FLOOR);
                s.put(0, 1, 4, lever()).put(1, 1, 4, WIRE);
                for (int i = 0; i < 6; i++) s.put(2 + i, 1 + i, 4, block.defaultBlockState()).put(2 + i, 2 + i, 4, WIRE);
                for (int i = 0; i < 5; i++) {
                    s.fill(8 + i, 1, 4, 8 + i, 5 - i, 4, block.defaultBlockState()).put(8 + i, 6 - i, 4, WIRE);
                }
                s.put(13, 1, 4, WIRE).put(14, 1, 4, LAMP);
                s.use(5, 0, 1, 4).use(35, 0, 1, 4).runFor(55);
            }));
        }

        scenes.add(ParityScene.of("wall_torch_under_dust_broken", s -> {
            // The block with dust on it sits right above a torch on the side of the block below.
            s.fill(3, 0, 3, 13, 0, 6, FLOOR);
            s.put(6, 1, 4, STONE).put(6, 1, 5, wallTorch(Direction.SOUTH)).put(6, 2, 5, STONE);
            for (int x = 6; x <= 11; x++) s.put(x, 3, 5, WIRE);
            s.fill(7, 1, 5, 11, 2, 5, STONE).put(12, 3, 5, STONE).put(12, 2, 5, LAMP);
            s.breakAt(10, 6, 1, 5).placeAt(25, 6, 1, 5, wallTorch(Direction.SOUTH)).breakAt(40, 6, 1, 4).runFor(60);
        }));

        scenes.add(ParityScene.of("torch_off_by_signal", s -> {
            s.fill(0, 0, 4, 12, 0, 4, FLOOR);
            s.put(0, 1, 4, lever()).put(1, 1, 4, WIRE).put(2, 1, 4, WIRE).put(3, 1, 4, STONE).put(4, 1, 4, wallTorch(Direction.EAST));
            s.put(4, 2, 4, STONE).put(4, 3, 4, WIRE).put(5, 3, 4, WIRE);
            s.fill(5, 1, 4, 5, 2, 4, STONE).put(6, 1, 4, WIRE).put(6, 2, 4, WIRE);
            s.put(7, 1, 4, WIRE).put(8, 1, 4, LAMP);
            s.use(5, 0, 1, 4).use(25, 0, 1, 4).runFor(45);
        }));

        scenes.add(ParityScene.of("torch_tower", s -> {
            s.fill(5, 0, 4, 9, 0, 4, FLOOR);
            s.put(6, 1, 4, STONE).put(5, 1, 4, lever().setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, Direction.WEST));
            for (int y = 2; y <= 9; y += 2) s.put(6, y, 4, torch()).put(6, y + 1, 4, STONE);
            s.put(7, 9, 4, LAMP);
            s.use(5, 5, 1, 4).use(40, 5, 1, 4).runFor(70);
        }));

        scenes.add(ParityScene.of("torch_burns_out", s -> {
            // A torch whose own output comes back to its block a moment later: too fast, it burns out and later comes back.
            s.fill(3, 0, 3, 8, 0, 7, FLOOR);
            // The lamp and repeater go before the dust feeding them: a plain setBlock doesn't make a block look at its input.
            s.put(5, 1, 4, STONE).put(8, 1, 4, LAMP).put(5, 1, 5, repeater(Direction.SOUTH, 1)).put(6, 1, 4, wallTorch(Direction.EAST));
            s.put(7, 1, 4, WIRE).put(6, 1, 5, WIRE).put(6, 1, 6, WIRE).put(5, 1, 6, WIRE);
            s.runFor(300);
        }));

        scenes.add(ParityScene.of("repeater_delays", s -> {
            s.fill(0, 0, 3, 15, 0, 6, FLOOR);
            for (int delay = 1; delay <= 4; delay++) {
                int z = 2 + delay;
                s.put(0, 1, z, WIRE).put(9, 1, z, repeater(Direction.WEST, delay)).put(10, 1, z, repeater(Direction.WEST, delay)).put(11, 1, z, LAMP);
                for (int x = 1; x < 9; x++) s.put(x, 1, z, WIRE);
            }
            s.put(0, 1, 7, lever());
            s.fill(0, 0, 7, 0, 0, 7, FLOOR);
            s.use(5, 0, 1, 7).use(30, 0, 1, 7).runFor(60);
        }));

        scenes.add(ParityScene.of("repeater_short_pulse", s -> {
            s.fill(0, 0, 4, 12, 0, 4, FLOOR);
            s.put(0, 1, 4, button()).put(1, 1, 4, WIRE);
            s.put(2, 1, 4, repeater(Direction.WEST, 4));
            s.put(3, 1, 4, WIRE).put(4, 1, 4, repeater(Direction.WEST, 1)).put(5, 1, 4, repeater(Direction.WEST, 2));
            s.put(6, 1, 4, repeater(Direction.WEST, 3)).put(7, 1, 4, LAMP);
            s.use(5, 0, 1, 4).runFor(70);
        }));

        scenes.add(ParityScene.of("repeater_locked_from_the_side", s -> {
            s.fill(0, 0, 3, 12, 0, 5, FLOOR);
            s.put(0, 1, 5, lever());
            for (int x = 1; x < 10; x++) s.put(x, 1, 5, x == 4 ? repeater(Direction.WEST, 1) : WIRE);
            s.put(10, 1, 5, LAMP);
            s.put(4, 1, 4, repeater(Direction.NORTH, 1)).put(4, 1, 3, WIRE).put(3, 1, 3, WIRE).put(2, 1, 3, lever());
            s.use(5, 2, 1, 3).use(10, 0, 1, 5).use(20, 2, 1, 3).use(30, 0, 1, 5).use(35, 2, 1, 3).use(45, 2, 1, 3).runFor(60);
        }));

        for (ComparatorMode mode : ComparatorMode.values()) {
            scenes.add(ParityScene.of("comparator_" + mode.getSerializedName(), s -> {
                s.fill(0, 0, 3, 12, 0, 6, FLOOR);
                s.put(0, 1, 4, lever());
                for (int x = 1; x < 7; x++) s.put(x, 1, 4, WIRE);
                s.put(7, 1, 4, comparator(Direction.WEST, mode));
                s.put(8, 1, 4, WIRE).put(9, 1, 4, WIRE).put(10, 1, 4, LAMP);
                s.put(7, 1, 5, WIRE).put(7, 1, 6, WIRE).put(6, 1, 6, lever());
                s.use(5, 0, 1, 4).use(12, 6, 1, 6).use(20, 7, 1, 4).use(28, 6, 1, 6).use(35, 0, 1, 4).runFor(50);
            }));
        }

        scenes.add(ParityScene.of("comparator_reads_hopper", s -> {
            s.fill(5, 0, 4, 12, 0, 4, FLOOR);
            s.put(6, 1, 4, hopper(Direction.UP.getOpposite())).put(5, 1, 4, STONE);
            s.put(7, 1, 4, comparator(Direction.WEST, ComparatorMode.COMPARE));
            for (int x = 8; x < 11; x++) s.put(x, 1, 4, WIRE);
            s.put(11, 1, 4, LAMP);
            s.insert(5, 6, 1, 4, Items.APPLE, 1).insert(15, 6, 1, 4, Items.APPLE, 63).insert(25, 6, 1, 4, Items.BREAD, 64)
                    .take(35, 6, 1, 4, 64).take(45, 6, 1, 4, 64).runFor(60);
        }));

        scenes.add(ParityScene.of("comparator_reads_hopper_as_it_fills", s -> {
            // The item arrives from another hopper, not from a player.
            s.fill(3, 0, 4, 12, 0, 4, FLOOR);
            s.put(5, 1, 4, hopper(Direction.EAST)).put(6, 1, 4, hopper(Direction.DOWN));
            s.put(7, 1, 4, comparator(Direction.WEST, ComparatorMode.COMPARE));
            s.put(8, 1, 4, WIRE).put(9, 1, 4, WIRE).put(10, 1, 4, LAMP);
            s.item(5, 1, 4, 0, Items.APPLE, 64);
            s.runFor(120);
        }));

        scenes.add(ParityScene.of("comparator_reads_chest_through_a_block", s -> {
            s.fill(3, 0, 4, 12, 0, 4, FLOOR);
            s.put(5, 1, 4, Blocks.CHEST.defaultBlockState()).put(6, 1, 4, STONE);
            s.put(7, 1, 4, comparator(Direction.WEST, ComparatorMode.COMPARE));
            s.put(8, 1, 4, WIRE).put(9, 1, 4, LAMP);
            s.insert(5, 5, 1, 4, Items.COBBLESTONE, 64).insert(15, 5, 1, 4, Items.COBBLESTONE, 64).take(25, 5, 1, 4, 64).runFor(40);
        }));

        scenes.add(ParityScene.of("comparator_reads_a_cauldron_and_composter", s -> {
            s.fill(5, 0, 4, 12, 0, 6, FLOOR);
            s.raw(6, 1, 4, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 2));
            s.put(7, 1, 4, comparator(Direction.WEST, ComparatorMode.COMPARE)).put(8, 1, 4, WIRE);
            s.raw(6, 1, 6, Blocks.COMPOSTER.defaultBlockState().setValue(net.minecraft.world.level.block.ComposterBlock.LEVEL, 4));
            s.put(7, 1, 6, comparator(Direction.WEST, ComparatorMode.COMPARE)).put(8, 1, 6, WIRE);
            s.breakAt(10, 6, 1, 4).runFor(30);
        }));

        scenes.add(ParityScene.of("hopper_chain_into_a_chest", s -> {
            s.fill(3, 0, 4, 13, 0, 4, FLOOR);
            for (int x = 3; x < 12; x++) s.put(x, 1, 4, hopper(Direction.EAST));
            s.put(12, 1, 4, Blocks.CHEST.defaultBlockState()).item(3, 1, 4, 0, Items.APPLE, 12);
            s.runFor(240);
        }));

        scenes.add(ParityScene.of("hopper_locked_by_a_lever", s -> {
            s.fill(3, 0, 4, 12, 0, 5, FLOOR);
            for (int x = 3; x < 10; x++) s.put(x, 1, 4, hopper(Direction.EAST));
            s.put(10, 1, 4, Blocks.CHEST.defaultBlockState()).item(3, 1, 4, 0, Items.APPLE, 8);
            s.put(7, 1, 5, lever());
            s.use(30, 7, 1, 5).use(100, 7, 1, 5).runFor(200);
        }));

        scenes.add(ParityScene.of("side_hopper_with_a_hopper_below", s -> {
            s.fill(3, 0, 4, 13, 0, 4, FLOOR);
            s.put(4, 2, 4, hopper(Direction.EAST)).put(5, 2, 4, hopper(Direction.EAST)).put(6, 2, 4, Blocks.CHEST.defaultBlockState());
            s.put(5, 1, 4, hopper(Direction.EAST)).put(6, 1, 4, Blocks.CHEST.defaultBlockState());
            s.item(4, 2, 4, 0, Items.APPLE, 16);
            s.runFor(220);
        }));

        scenes.add(ParityScene.of("piston_and_sticky_piston", s -> {
            s.fill(0, 0, 3, 14, 0, 6, FLOOR);
            BlockState piston = Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST);
            BlockState sticky = Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST);
            s.put(0, 1, 4, lever()).put(1, 1, 4, WIRE).put(2, 1, 4, WIRE).put(3, 1, 4, piston).put(4, 1, 4, STONE).put(5, 1, 4, Blocks.REDSTONE_BLOCK.defaultBlockState());
            s.put(0, 1, 6, lever()).put(1, 1, 6, WIRE).put(2, 1, 6, WIRE).put(3, 1, 6, sticky).put(4, 1, 6, Blocks.REDSTONE_BLOCK.defaultBlockState()).put(6, 1, 6, LAMP);
            s.use(5, 0, 1, 4).use(25, 0, 1, 4).use(10, 0, 1, 6).use(30, 0, 1, 6).runFor(50);
        }));

        scenes.add(ParityScene.of("observer_pulse", s -> {
            s.fill(0, 0, 4, 12, 0, 4, FLOOR);
            s.put(0, 1, 4, lever()).put(1, 1, 4, WIRE).put(2, 1, 4, WIRE);
            // Looks west at the dust, puts its pulse out to the east.
            s.put(3, 1, 4, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.WEST));
            s.put(4, 1, 4, WIRE).put(5, 1, 4, LAMP);
            s.use(5, 0, 1, 4).use(20, 0, 1, 4).runFor(40);
        }));

        scenes.add(ParityScene.of("button_door_trapdoor", s -> {
            s.fill(0, 0, 4, 12, 0, 4, FLOOR);
            s.put(0, 1, 4, button()).put(1, 1, 4, WIRE).put(2, 1, 4, WIRE);
            BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
            s.raw(3, 1, 4, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)).raw(3, 2, 4, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            s.put(4, 1, 4, WIRE).put(5, 1, 4, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH));
            s.use(5, 0, 1, 4).runFor(40);
        }));

        scenes.add(ParityScene.of("dropper_into_hopper", s -> {
            s.fill(0, 0, 4, 12, 0, 4, FLOOR);
            s.put(0, 1, 4, lever()).put(1, 1, 4, WIRE);
            s.put(2, 1, 4, Blocks.DROPPER.defaultBlockState().setValue(net.minecraft.world.level.block.DropperBlock.FACING, Direction.EAST));
            s.put(3, 1, 4, hopper(Direction.EAST)).put(4, 1, 4, Blocks.CHEST.defaultBlockState());
            s.item(2, 1, 4, 0, Items.APPLE, 4);
            s.use(5, 0, 1, 4).use(15, 0, 1, 4).use(25, 0, 1, 4).use(35, 0, 1, 4).runFor(70);
        }));

        scenes.add(ParityScene.of("dust_loses_its_floor", s -> {
            line(s, 4, 0, 12);
            s.use(5, 0, 1, 4).breakAt(15, 6, 0, 4).placeAt(25, 6, 0, 4, FLOOR).placeAt(27, 6, 1, 4, WIRE).runFor(45);
        }));

        scenes.add(ParityScene.of("redstone_block_placed_and_broken", s -> {
            s.fill(0, 0, 4, 12, 0, 4, FLOOR);
            for (int x = 1; x < 9; x++) s.put(x, 1, 4, WIRE);
            s.put(9, 1, 4, LAMP);
            s.placeAt(5, 0, 1, 4, Blocks.REDSTONE_BLOCK.defaultBlockState()).breakAt(25, 0, 1, 4).runFor(45);
        }));

        scenes.add(ParityScene.of("torch_clock_speed", s -> {
            // Torch, dust, a slow repeater back into the torch's block. Counts: see the report's changes; a side ticking
            // too fast or too slow shows as a growing difference.
            s.fill(3, 0, 3, 12, 0, 7, FLOOR);
            s.put(5, 1, 4, STONE).put(8, 1, 4, LAMP).put(5, 1, 5, repeater(Direction.SOUTH, 4)).put(6, 1, 4, wallTorch(Direction.EAST));
            s.put(7, 1, 4, WIRE).put(7, 1, 5, WIRE).put(7, 1, 6, WIRE).put(6, 1, 6, WIRE).put(5, 1, 6, WIRE);
            s.runFor(200);
        }));

        scenes.add(ParityScene.of("observer_next_to_a_corner_with_no_grid", s -> {
            // Found by the random scenes (seed 11), cut down by ParityShrink: dust against a block past a corner of grids.
            BlockState watching = Blocks.OBSERVER.defaultBlockState();
            s.put(11, 1, 9, watching.setValue(ObserverBlock.FACING, Direction.NORTH));
            s.put(11, 0, 10, FLOOR).put(11, 1, 10, WIRE).put(11, 0, 12, FLOOR).put(11, 1, 12, WIRE);
            s.put(12, 1, 12, Blocks.OAK_PLANKS.defaultBlockState());
            s.put(12, 2, 15, watching.setValue(ObserverBlock.FACING, Direction.WEST));
            s.put(13, 1, 15, Blocks.COBBLESTONE.defaultBlockState()).put(13, 2, 15, WIRE);
            s.runFor(40);
        }));

        scenes.add(ParityScene.of("hopper_that_locks_itself_tells_nobody", s -> {
            // Found by the random scenes (seed 951): a hopper locked on arrival, in its onPlace, skips the placement's updates.
            s.put(2, 2, 5, wallTorch(Direction.SOUTH)).put(2, 1, 6, Blocks.COBBLESTONE.defaultBlockState()).put(2, 2, 6, WIRE);
            s.put(2, 2, 7, Blocks.DROPPER.defaultBlockState().setValue(net.minecraft.world.level.block.DropperBlock.FACING, Direction.SOUTH));
            s.put(3, 2, 7, hopper(Direction.SOUTH));
            s.runFor(30);
        }));

        scenes.add(ParityScene.of("comparator_with_no_floor_reading_a_chest", s -> {
            // Found by the random scenes (seed 81): the chest filling reaches the comparator, which then has to pop off.
            for (int z = 7; z < 13; z++) s.put(5, 2, z, hopper(Direction.SOUTH));
            s.put(5, 2, 13, Blocks.CHEST.defaultBlockState()).put(5, 2, 14, comparator(Direction.NORTH, ComparatorMode.COMPARE));
            s.item(5, 2, 7, 0, Items.APPLE, 3);
            s.runFor(120);
        }));

        scenes.add(ParityScene.of("nothing_echoes_off_a_grid_face", s -> {
            // Found by the random scenes (seed 141): a grid telling the block past its face must not hear itself doing so.
            s.put(14, 1, 11, torch());
            for (int z = 12; z < 15; z++) s.put(14, 0, z, FLOOR).put(14, 1, z, WIRE);
            s.put(14, 1, 15, comparator(Direction.NORTH, ComparatorMode.SUBTRACT));
            s.put(6, 1, 14, wallTorch(Direction.EAST)).put(7, 0, 14, FLOOR).put(7, 1, 14, WIRE).put(7, 0, 15, FLOOR).put(7, 1, 15, WIRE);
            s.runFor(40);
        }));

        scenes.add(separator());

        scenes.add(ParityScene.of("sculk_sensor", s -> {
            s.fill(3, 0, 3, 12, 0, 6, FLOOR);
            s.put(4, 1, 4, lever()).put(8, 1, 4, Blocks.SCULK_SENSOR.defaultBlockState());
            s.put(9, 1, 4, WIRE).put(10, 1, 4, LAMP);
            s.put(8, 1, 5, comparator(Direction.NORTH, ComparatorMode.COMPARE)).put(8, 1, 6, WIRE);
            s.use(5, 4, 1, 4).use(40, 4, 1, 4).placeAt(80, 6, 1, 5, STONE).runFor(120);
        }));

        return scenes;
    }

    /** The comparison itself: spaces that differ are said to differ, in words. A diff that sees nothing would pass everything. */
    @net.minecraft.gametest.framework.GameTest(template = "empty_platform", timeoutTicks = 40)
    public void spaceDiffSaysWhatDiffers(net.minecraft.gametest.framework.GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
        helper.setBlock(at.east(), Blocks.REDSTONE_BLOCK);
        var tiny = new ParitySpace.Tiny(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 1)), 8, BlockPos.ZERO,
                helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE));
        tiny.build(BlockPos.ZERO, LAMP, false);
        var real = new com.therappha.tinyblocks.diff.RealSpaceView(helper.getLevel(), helper.absolutePos(at));
        var differences = com.therappha.tinyblocks.diff.SpaceDiff.compare(real, tiny, List.of(BlockPos.ZERO));
        helper.assertTrue(differences.stream().anyMatch(d -> d.field().equals("state.lit") && d.left().equals("true") && d.right().equals("false")),
                "the lit lamp against the dark one was not reported: " + differences);
        helper.succeed();
    }

    /** ~/separator.litematic, an item sorter, with the lava and glass of its overflow left for a chest. */
    private static ParityScene separator() {
        return ParityScene.of("separator", s -> {
            BlockState smooth = Blocks.SMOOTH_STONE.defaultBlockState();
            for (int x = 2; x <= 5; x++) {
                s.raw(x, 0, 2, smooth).raw(x, 1, 1, smooth).raw(x, 1, 3, smooth);
                s.raw(x, 2, 2, smooth).raw(x, 2, 3, smooth).raw(x, 2, 4, smooth);
                s.raw(x, 1, 2, repeater(Direction.NORTH, 1));
                s.raw(x, 1, 4, wallTorch(Direction.SOUTH));
                s.put(x, 2, 1, WIRE).put(x, 3, 2, WIRE).put(x, 3, 3, WIRE);
                s.raw(x, 3, 4, comparator(Direction.SOUTH, ComparatorMode.COMPARE));
                s.raw(x, 2, 5, hopper(Direction.SOUTH).setValue(HopperBlock.ENABLED, false));
                s.raw(x, 3, 5, hopper(Direction.NORTH));
                s.raw(x, 2, 6, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.WEST)
                        .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, ChestType.RIGHT));
                s.raw(x, 2, 7, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.WEST)
                        .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, ChestType.LEFT));
                for (int slot = 1; slot <= 4; slot++) s.item(x, 3, 5, slot, Items.IRON_NUGGET, 1);
            }
            s.item(2, 3, 5, 0, Items.APPLE, 41).item(3, 3, 5, 0, Items.BREAD, 41).item(4, 3, 5, 0, Items.COBBLESTONE, 41).item(5, 3, 5, 0, Items.DIRT, 41);
            for (int x = 1; x <= 9; x++) s.raw(x, 4, 5, hopper(Direction.EAST));
            s.raw(10, 4, 5, hopper(Direction.SOUTH)).raw(10, 4, 6, Blocks.CHEST.defaultBlockState());
            s.raw(1, 5, 5, hopper(Direction.DOWN));
            s.insert(10, 1, 5, 5, Items.APPLE, 8).insert(30, 1, 5, 5, Items.BREAD, 8)
                    .insert(50, 1, 5, 5, Items.COBBLESTONE, 8).insert(70, 1, 5, 5, Items.STICK, 8);
            s.runFor(500);
        });
    }

    static TestFunction test(ParityScene scene, ParityRun.Layout layout) {
        return test(scene, layout, ParitySpace.Move.STAYS);
    }

    static TestFunction test(ParityScene scene, ParityRun.Layout layout, ParitySpace.Move move) {
        String name = "parity_" + scene.name + "_" + layout.name() + (move == ParitySpace.Move.STAYS ? "" : "_" + move.name().toLowerCase());
        return new TestFunction("parity", name, TinyBlocks.MOD_ID + ":" + layout.template(), ParityRun.timeout(scene), 0L, true,
                helper -> new ParityRun(helper, scene, layout, name, move).start());
    }

    @GameTestGenerator
    public Collection<TestFunction> parity() {
        List<TestFunction> tests = new ArrayList<>();
        for (ParityScene scene : scenes()) {
            for (ParityRun.Layout layout : List.of(ParityRun.Layout.ONE_16, ParityRun.Layout.FOUR_8, ParityRun.Layout.FOUR_8_SHIFTED)) {
                tests.add(test(scene, layout));
            }
        }
        return tests;
    }

    /**
     * Random scenes, by seed: a fixed set by default, {@code -PrandomSeeds=n} for a longer hunt. {@code -PshrinkSeed=seed}
     * cuts that seed's scene down to what still fails instead.
     */
    @GameTestGenerator
    public Collection<TestFunction> parityRandom() {
        String shrink = System.getProperty("tinyblocks.shrinkSeed", "");
        if (!shrink.isEmpty()) return List.of(ParityShrink.test(Long.parseLong(shrink)));
        String count = System.getProperty("tinyblocks.randomSeeds", "");
        int seeds = count.isEmpty() ? 60 : Integer.parseInt(count);
        List<ParityRun.Layout> layouts = List.of(ParityRun.Layout.ONE_16, ParityRun.Layout.FOUR_8, ParityRun.Layout.FOUR_8_SHIFTED);
        List<TestFunction> tests = new ArrayList<>();
        for (int seed = 0; seed < seeds; seed++) {
            tests.add(test(RandomRedstoneScene.generate(seed), layouts.get(seed % layouts.size())));
        }
        return tests;
    }

    @GameTestGenerator
    public Collection<TestFunction> parityBig() {
        ParityScene decay = ParityScene.of("wire_decays_across_grids", s -> {
            s.fill(0, 0, 4, 31, 0, 4, FLOOR);
            s.put(0, 1, 4, lever());
            for (int x = 1; x < 31; x++) s.put(x, 1, 4, WIRE);
            s.put(16, 2, 4, LAMP).put(31, 1, 4, LAMP);
            s.use(5, 0, 1, 4).use(30, 0, 1, 4).runFor(50);
        });
        ParityScene crossing = ParityScene.of("many_circuits_in_32", s -> {
            for (int z = 2; z < 30; z += 4) {
                s.fill(0, 0, z, 31, 0, z + 1, FLOOR);
                s.put(0, 1, z, lever());
                for (int x = 1; x < 13; x++) s.put(x, 1, z, WIRE);
                s.put(13, 1, z, repeater(Direction.WEST, 1 + (z / 4) % 4));
                for (int x = 14; x < 26; x++) s.put(x, 1, z, WIRE);
                s.put(26, 1, z, Blocks.STONE.defaultBlockState()).put(27, 1, z, wallTorch(Direction.EAST));
                s.put(28, 1, z, WIRE).put(29, 1, z, LAMP);
                s.use(5 + z, 0, 1, z);
            }
            s.runFor(80);
        });
        return List.of(test(decay, ParityRun.Layout.FOUR_16), test(decay, ParityRun.Layout.SIXTEEN_8),
                test(crossing, ParityRun.Layout.FOUR_16), test(crossing, ParityRun.Layout.SIXTEEN_8));
    }
}
