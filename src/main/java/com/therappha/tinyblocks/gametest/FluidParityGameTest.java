package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Water and lava in real blocks and in grids, compared tick by tick. Every scene keeps its fluid inside walls: what got
 * past a grid's own edges is SubgridFluidLeakGameTest's question, since the comparison only reads cells.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class FluidParityGameTest {

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();

    /** A floor at y 0 and walls around it up to {@code height}. */
    private static void bath(ParityScene s, int x0, int z0, int x1, int z1, int height) {
        s.fill(x0, 0, z0, x1, 0, z1, STONE);
        for (int y = 1; y <= height; y++) {
            s.fill(x0, y, z0, x1, y, z0, STONE).fill(x0, y, z1, x1, y, z1, STONE);
            s.fill(x0, y, z0 + 1, x0, y, z1 - 1, STONE).fill(x1, y, z0 + 1, x1, y, z1 - 1, STONE);
        }
    }

    static List<ParityScene> scenes() {
        List<ParityScene> scenes = new ArrayList<>();

        scenes.add(ParityScene.of("water_spreads_on_a_floor", s -> {
            bath(s, 0, 0, 15, 15, 1);
            s.raw(7, 1, 7, WATER).runFor(60).provenBy(7, 1, 12, "water");
        }));

        scenes.add(ParityScene.of("water_falls_and_spreads", s -> {
            bath(s, 0, 0, 15, 15, 1);
            s.fill(7, 1, 7, 7, 5, 7, STONE).raw(7, 6, 7, WATER).runFor(100).provenBy(7, 1, 9, "water");
        }));

        scenes.add(ParityScene.of("water_down_the_steps", s -> {
            bath(s, 0, 3, 15, 5, 1);
            for (int x = 1; x < 12; x++) s.fill(x, 1, 4, x, Math.max(1, (12 - x) / 2), 4, STONE);
            for (int y = 2; y <= 8; y++) s.put(0, y, 4, STONE).fill(0, y, 3, 14, y, 3, STONE).fill(0, y, 5, 14, y, 5, STONE);
            s.raw(1, 7, 4, WATER).runFor(120).provenBy(14, 1, 4, "water");
        }));

        scenes.add(ParityScene.of("water_stops_at_walls", s -> {
            bath(s, 0, 2, 15, 6, 2);
            s.fill(8, 1, 3, 8, 2, 5, STONE);
            s.raw(4, 1, 4, WATER).runFor(70).provenBy(7, 1, 4, "water");
        }));

        scenes.add(ParityScene.of("water_goes_for_the_nearest_hole", s -> {
            bath(s, 0, 0, 15, 9, 2);
            s.fill(1, 1, 1, 14, 1, 8, STONE);
            s.breakAt(-8, 11, 1, 4).breakAt(-8, 2, 1, 8);
            s.raw(7, 2, 4, WATER).runFor(80).provenBy(11, 1, 4, "water");
        }));

        scenes.add(ParityScene.of("two_sources_make_a_third", s -> {
            bath(s, 1, 3, 9, 5, 1);
            s.raw(3, 1, 4, WATER).raw(5, 1, 4, WATER).runFor(40).provenBy(4, 1, 4, "state.level=0");
        }));

        scenes.add(ParityScene.of("water_goes_when_its_source_goes", s -> {
            bath(s, 0, 0, 15, 15, 1);
            s.raw(7, 1, 7, WATER).clearAt(30, 7, 1, 7).runFor(100);
        }));

        scenes.add(ParityScene.of("wall_broken_lets_it_through", s -> {
            bath(s, 0, 3, 15, 5, 1);
            s.put(6, 1, 4, STONE).raw(2, 1, 4, WATER).breakAt(30, 6, 1, 4).runFor(90).provenBy(8, 1, 4, "water");
        }));

        scenes.add(ParityScene.of("water_washes_things_away", s -> {
            bath(s, 0, 3, 15, 5, 2);
            s.put(5, 1, 4, Blocks.REDSTONE_WIRE.defaultBlockState()).put(6, 1, 4, Blocks.TORCH.defaultBlockState());
            s.put(7, 1, 4, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH));
            // A flower, not a crop: a crop grows on random ticks, which the real copies and the grid draw differently.
            s.put(8, 1, 4, Blocks.POPPY.defaultBlockState());
            s.raw(2, 1, 4, WATER).runFor(60).compareEntities().provenBy(7, 1, 4, "water");
        }));

        scenes.add(ParityScene.of("waterlogged_slab_lets_it_out", s -> {
            bath(s, 0, 0, 15, 15, 2);
            s.raw(7, 1, 7, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM).setValue(BlockStateProperties.WATERLOGGED, true));
            // A block placed next to it is what makes real water in a block start to run; building it doesn't.
            s.placeAt(5, 7, 2, 7, STONE).runFor(60).provenBy(9, 1, 7, "water");
        }));

        scenes.add(ParityScene.of("sponge_soaks_up_water", s -> {
            bath(s, 0, 0, 15, 15, 1);
            s.put(7, 1, 7, Blocks.SPONGE.defaultBlockState());
            s.placeAt(10, 6, 1, 7, WATER).runFor(30).provenBy(7, 1, 7, "wet_sponge");
        }));

        scenes.add(ParityScene.of("lava_spreads_slowly", s -> {
            bath(s, 0, 0, 15, 15, 1);
            s.raw(7, 1, 7, LAVA).runFor(200).provenBy(9, 1, 7, "lava");
        }));

        scenes.add(ParityScene.of("water_on_a_lava_source_makes_obsidian", s -> {
            bath(s, 0, 3, 15, 5, 1);
            s.raw(3, 1, 4, WATER).raw(8, 1, 4, LAVA).runFor(70).provenBy(8, 1, 4, "obsidian");
        }));

        scenes.add(ParityScene.of("water_on_flowing_lava_makes_cobblestone", s -> {
            bath(s, 0, 2, 15, 6, 1);
            s.fill(1, 1, 4, 6, 1, 4, STONE);
            // The lava gets as far as it can before the water comes round the wall to meet it.
            s.raw(1, 1, 3, LAVA).placeAt(100, 7, 1, 5, WATER).runFor(150).provenBy(4, 1, 3, "cobblestone");
        }));

        scenes.add(ParityScene.of("lava_falling_on_water_makes_stone", s -> {
            bath(s, 3, 3, 7, 7, 1);
            s.fill(4, 1, 4, 6, 1, 6, STONE).breakAt(-8, 5, 1, 5);
            s.placeAt(-6, 5, 1, 5, WATER);
            s.raw(5, 4, 5, LAVA).runFor(80).provenBy(5, 1, 5, "state.block=stone");
        }));

        return scenes;
    }

    @GameTestGenerator
    public Collection<TestFunction> fluids() {
        return RedstoneParityGameTest.everyLayout(scenes());
    }
}
