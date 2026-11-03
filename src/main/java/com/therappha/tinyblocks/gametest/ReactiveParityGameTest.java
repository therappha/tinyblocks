package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Blocks that move or react, in real blocks and in grids. A falling block is an entity in the world and goes cell by
 * cell in a grid, so those scenes are compared where it comes to rest, not on the way.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReactiveParityGameTest {

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();

    private static BlockState lever() {
        return Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH);
    }

    private static void door(ParityScene s, int x, int z, BlockState door) {
        BlockState facing = door.setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        s.raw(x, 1, z, facing.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)).raw(x, 2, z, facing.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    static List<ParityScene> scenes() {
        List<ParityScene> scenes = new ArrayList<>();

        scenes.add(ParityScene.of("sand_and_gravel_fall_to_the_floor", s -> {
            s.fill(0, 0, 3, 12, 0, 5, STONE);
            s.put(4, 9, 4, SAND).put(8, 6, 4, GRAVEL);
            s.runFor(60).compareOnlyAtTheEnd().provenBy(4, 1, 4, "sand").provenBy(8, 1, 4, "gravel");
        }));

        scenes.add(ParityScene.of("sand_tower_falls_when_its_support_goes", s -> {
            s.fill(0, 0, 3, 12, 0, 5, STONE);
            s.put(4, 1, 4, STONE);
            s.fill(4, 2, 4, 4, 6, 4, STONE);
            s.breakAt(-8, 4, 2, 4).breakAt(-8, 4, 3, 4).breakAt(-8, 4, 4, 4).breakAt(-8, 4, 5, 4).breakAt(-8, 4, 6, 4);
            s.placeAt(-6, 4, 2, 4, SAND).placeAt(-6, 4, 3, 4, GRAVEL).placeAt(-6, 4, 4, 4, SAND);
            s.breakAt(10, 4, 1, 4).runFor(50).compareOnlyAtTheEnd().provenBy(4, 3, 4, "sand");
        }));

        scenes.add(ParityScene.of("sand_falling_on_a_torch_breaks_up", s -> {
            s.fill(0, 0, 3, 12, 0, 5, STONE);
            s.put(4, 1, 4, Blocks.TORCH.defaultBlockState()).put(4, 6, 4, SAND);
            s.runFor(60).compareOnlyAtTheEnd().compareEntities();
        }));

        scenes.add(ParityScene.of("sand_sinks_through_water", s -> {
            s.fill(3, 0, 3, 5, 0, 5, STONE);
            s.fill(3, 1, 3, 5, 4, 3, STONE).fill(3, 1, 5, 5, 4, 5, STONE).fill(3, 1, 4, 3, 4, 4, STONE).fill(5, 1, 4, 5, 4, 4, STONE);
            for (int y = 1; y <= 3; y++) s.raw(4, y, 4, Blocks.WATER.defaultBlockState());
            s.placeAt(5, 4, 7, 4, SAND).runFor(60).compareOnlyAtTheEnd().provenBy(4, 1, 4, "sand");
        }));

        scenes.add(ParityScene.of("concrete_powder_lands_in_water", s -> {
            s.fill(3, 0, 3, 5, 0, 5, STONE);
            s.fill(3, 1, 3, 5, 3, 3, STONE).fill(3, 1, 5, 5, 3, 5, STONE).fill(3, 1, 4, 3, 3, 4, STONE).fill(5, 1, 4, 5, 3, 4, STONE);
            s.raw(4, 1, 4, Blocks.WATER.defaultBlockState());
            s.placeAt(5, 4, 7, 4, Blocks.WHITE_CONCRETE_POWDER.defaultBlockState()).runFor(60).compareOnlyAtTheEnd().provenBy(4, 1, 4, "white_concrete");
        }));

        scenes.add(ParityScene.of("door_used_by_either_half", s -> {
            s.fill(3, 0, 3, 8, 0, 5, STONE);
            door(s, 4, 4, Blocks.OAK_DOOR.defaultBlockState());
            s.use(5, 4, 1, 4).use(15, 4, 2, 4).use(25, 4, 2, 4).use(35, 4, 1, 4).runFor(45);
        }));

        scenes.add(ParityScene.of("door_goes_when_its_floor_goes", s -> {
            s.fill(3, 0, 3, 8, 0, 5, STONE);
            door(s, 4, 4, Blocks.OAK_DOOR.defaultBlockState());
            s.breakAt(10, 4, 0, 4).runFor(30).compareEntities();
        }));

        scenes.add(ParityScene.of("door_goes_whole_when_a_half_goes", s -> {
            s.fill(3, 0, 3, 8, 0, 5, STONE);
            door(s, 4, 4, Blocks.OAK_DOOR.defaultBlockState());
            door(s, 6, 4, Blocks.OAK_DOOR.defaultBlockState());
            s.breakAt(10, 4, 2, 4).breakAt(20, 6, 1, 4).runFor(35).compareEntities();
        }));

        scenes.add(ParityScene.of("iron_door_on_a_lever", s -> {
            s.fill(3, 0, 3, 8, 0, 5, STONE);
            door(s, 4, 4, Blocks.IRON_DOOR.defaultBlockState());
            s.put(5, 1, 4, lever()).use(5, 4, 1, 4).use(10, 5, 1, 4).use(25, 5, 1, 4).runFor(40);
        }));

        scenes.add(ParityScene.of("trapdoor_used_and_on_a_lever", s -> {
            s.fill(3, 0, 3, 8, 0, 5, STONE);
            s.put(4, 1, 4, Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH));
            s.put(6, 1, 4, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH)).put(7, 1, 4, lever());
            s.put(4, 1, 5, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.SOUTH));
            s.use(5, 4, 1, 4).use(10, 6, 1, 4).use(15, 7, 1, 4).use(25, 4, 1, 5).use(35, 7, 1, 4).use(45, 4, 1, 4).use(50, 4, 1, 5).runFor(60);
        }));

        scenes.add(ParityScene.of("fence_gate_used", s -> {
            s.fill(3, 0, 3, 8, 0, 5, STONE);
            s.put(4, 1, 4, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH));
            s.use(5, 4, 1, 4).use(15, 4, 1, 4).runFor(25);
        }));

        scenes.add(ParityScene.of("note_block_tuned", s -> {
            s.fill(3, 0, 3, 8, 0, 5, STONE);
            s.put(4, 1, 4, Blocks.NOTE_BLOCK.defaultBlockState());
            s.use(3, 4, 1, 4).use(6, 4, 1, 4).use(9, 4, 1, 4).runFor(15).provenBy(4, 1, 4, "state.note=3");
        }));

        scenes.add(ParityScene.of("note_block_hears_what_it_stands_on", s -> {
            s.fill(3, 0, 3, 8, 0, 5, STONE);
            s.put(4, 1, 4, Blocks.NOTE_BLOCK.defaultBlockState());
            s.breakAt(5, 4, 0, 4).placeAt(10, 4, 0, 4, Blocks.OAK_PLANKS.defaultBlockState());
            s.breakAt(15, 4, 0, 4).placeAt(20, 4, 0, 4, Blocks.GOLD_BLOCK.defaultBlockState()).runFor(30).provenBy(4, 1, 4, "state.instrument=bell");
        }));

        scenes.add(ParityScene.of("note_block_on_a_lever", s -> {
            s.fill(3, 0, 3, 8, 0, 5, STONE);
            s.put(4, 1, 4, Blocks.NOTE_BLOCK.defaultBlockState()).put(5, 1, 4, lever());
            s.use(5, 5, 1, 4).use(15, 5, 1, 4).runFor(25);
        }));

        scenes.add(ParityScene.of("leaves_count_their_way_to_a_log", s -> {
            s.fill(5, 0, 5, 5, 3, 5, Blocks.OAK_LOG.defaultBlockState());
            // Persistent, so they count and never fall off: that part is random, and SubgridReactiveGameTest's.
            BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            for (int x = 6; x <= 13; x++) s.raw(x, 3, 5, leaves);
            for (int z = 6; z <= 13; z++) s.raw(5, 3, z, leaves);
            // A block placed by a leaf sets its count going; building leaves doesn't, as with water in a block.
            s.placeAt(2, 6, 4, 5, STONE).placeAt(2, 5, 4, 6, STONE);
            s.breakAt(20, 5, 3, 5).breakAt(22, 5, 2, 5).runFor(50).provenBy(13, 3, 5, "state.distance=7");
        }));

        return scenes;
    }

    @GameTestGenerator
    public Collection<TestFunction> reactive() {
        return RedstoneParityGameTest.everyLayout(scenes());
    }
}
