package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Create's fluids against real ones: pumps, pipes, tanks, spouts and drains. What a tank holds is compared at the end:
 * how much a pipe carries on a given tick has never been the point, what arrived is. Water and lava the pipes put
 * into or take out of the world show up as cells.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class CreateFluidParityGameTest {

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState TANK = AllBlocks.FLUID_TANK.getDefaultState();
    private static final BlockState PIPE = AllBlocks.FLUID_PIPE.getDefaultState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    private static BlockState motor(Direction facing) {
        return AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, facing);
    }

    private static BlockState pump(Direction flow) {
        return AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(DirectionalKineticBlock.FACING, flow);
    }

    private static BlockState hopper(Direction to) {
        return Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, to);
    }

    /**
     * A pump at {@code x},1,4 pushing east, turned as a cog by the one over it, which a motor turns. Pipes on both sides
     * of it, and nothing else: what is at either end is the scene's.
     */
    private static void pumpLine(ParityScene s, int x) {
        s.put(x - 1, 1, 4, PIPE).raw(x, 1, 4, pump(Direction.EAST)).put(x + 1, 1, 4, PIPE);
        s.raw(x, 2, 4, AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
        s.raw(x - 1, 2, 4, motor(Direction.EAST));
        s.settleTicks = 20;
    }

    /**
     * A vault three long along x from {@code x},3,{@code z}, a hopper over its first block holding 16 diamonds, and one
     * under its last block passing what it takes to a chest at {@code x}+2,1,{@code z}.
     */
    private static void vaultThroughHoppers(ParityScene s, int x, int z) {
        BlockState vault = AllBlocks.ITEM_VAULT.getDefaultState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, Axis.X);
        s.raw(x, 3, z, vault).raw(x + 1, 3, z, vault).raw(x + 2, 3, z, vault);
        s.raw(x, 4, z, hopper(Direction.DOWN)).item(x, 4, z, 0, Items.DIAMOND, 16);
        s.raw(x + 2, 2, z, hopper(Direction.DOWN)).raw(x + 2, 1, z, Blocks.CHEST.defaultBlockState());
    }

    static List<ParityScene> scenes() {
        List<ParityScene> scenes = new ArrayList<>();

        scenes.add(ParityScene.of("create_tank_holds_what_is_poured_in", s -> {
            s.fill(2, 0, 3, 7, 0, 5, STONE);
            s.raw(4, 1, 4, TANK).fluid(4, 1, 4, Fluids.WATER, 3000);
            s.raw(5, 1, 4, TANK).fluid(5, 1, 4, Fluids.LAVA, 1500);
            s.runFor(20).provenBy(4, 1, 4, "water=3000").provenBy(5, 1, 4, "lava=1500");
        }));

        scenes.add(ParityScene.of("create_tanks_stacked_make_one", s -> {
            s.fill(2, 0, 3, 7, 0, 5, STONE);
            s.put(4, 1, 4, TANK).put(4, 2, 4, TANK).put(4, 3, 4, TANK).fluid(4, 1, 4, Fluids.WATER, 20000);
            s.runFor(20).provenBy(4, 3, 4, "water=20000");
        }));

        // Across the grids' seam at y=8 in the 1/8 layouts: two grids, one tank.
        scenes.add(ParityScene.of("create_tanks_stacked_across_grids_make_one", s -> {
            s.fill(2, 0, 3, 7, 0, 5, STONE);
            s.put(4, 6, 4, TANK).put(4, 7, 4, TANK).put(4, 8, 4, TANK).put(4, 9, 4, TANK).fluid(4, 6, 4, Fluids.WATER, 20000);
            s.runFor(20).provenBy(4, 9, 4, "water=20000");
        }));

        // The same, a tank at a time, as a player builds it: each one joins what is already there.
        scenes.add(ParityScene.of("create_tanks_placed_one_by_one_across_grids", s -> {
            s.fill(2, 0, 3, 7, 0, 5, STONE);
            s.put(4, 7, 4, TANK).fluid(4, 7, 4, Fluids.WATER, 6000);
            s.placeAt(5, 4, 8, 4, TANK).placeAt(10, 4, 9, 4, TANK);
            s.runFor(30).provenBy(4, 9, 4, "water=6000");
        }));

        // Two wide: a 2x2 tank two high, its halves in two grids.
        scenes.add(ParityScene.of("create_wide_tank_across_grids", s -> {
            s.fill(2, 0, 3, 7, 0, 6, STONE);
            s.fill(4, 7, 4, 5, 8, 5, TANK);
            s.fluid(4, 7, 4, Fluids.WATER, 40000);
            s.runFor(20).provenBy(5, 8, 5, "water=40000");
        }));

        scenes.add(ParityScene.of("create_wide_tank_placed_one_by_one_across_grids", s -> {
            s.fill(2, 0, 3, 7, 0, 6, STONE);
            s.fill(4, 7, 4, 5, 7, 5, TANK);
            s.fluid(4, 7, 4, Fluids.WATER, 20000);
            s.placeAt(5, 4, 8, 4, TANK).placeAt(6, 5, 8, 4, TANK).placeAt(7, 4, 8, 5, TANK).placeAt(8, 5, 8, 5, TANK);
            s.runFor(30).provenBy(5, 8, 5, "water=20000");
        }));

        // Item vaults lying along x, at two places so that one crosses a seam in each 1/8 layout; one built at once,
        // one a block at a time. What goes in at one end is held by the other.
        scenes.add(ParityScene.of("create_vaults_across_grids", s -> {
            s.fill(1, 0, 3, 11, 0, 7, STONE);
            BlockState vault = AllBlocks.ITEM_VAULT.getDefaultState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, Axis.X);
            s.raw(3, 1, 4, vault).raw(4, 1, 4, vault).raw(5, 1, 4, vault);
            s.raw(7, 1, 4, vault).raw(8, 1, 4, vault).raw(9, 1, 4, vault);
            s.raw(6, 1, 6, vault).placeAt(5, 7, 1, 6, vault).placeAt(10, 8, 1, 6, vault);
            s.insert(20, 3, 1, 4, Items.DIAMOND, 10).insert(20, 7, 1, 4, Items.DIAMOND, 20).insert(20, 6, 1, 6, Items.DIAMOND, 30);
            s.runFor(40).provenBy(5, 1, 4, "diamond=10").provenBy(9, 1, 4, "diamond=20").provenBy(8, 1, 6, "diamond=30");
        }));

        // A hopper puts diamonds in at one end of a vault, another takes them out at the other end into a chest: they
        // only get there through one vault. Two of them, one across a seam in each 1/8 layout.
        scenes.add(ParityScene.of("create_vault_hopper_in_hopper_out_across_grids", s -> {
            s.fill(1, 0, 3, 10, 0, 8, STONE);
            vaultThroughHoppers(s, 6, 4);
            vaultThroughHoppers(s, 3, 7);
            s.runFor(200).provenBy(8, 1, 4, "diamondx16").provenBy(5, 1, 7, "diamondx16");
        }));

        // Two wide, built at once (a fill), across the seam at y=8 and, in the shifted layout, the one at x=5. The hopper
        // under it, in the grid below, takes its first diamond a tick after the real one: what reached the chest is
        // compared once the hoppers are done.
        scenes.add(ParityScene.of("create_wide_vault_hopper_in_hopper_out_across_grids", s -> {
            s.fill(1, 0, 3, 8, 0, 7, STONE);
            BlockState vault = AllBlocks.ITEM_VAULT.getDefaultState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, Axis.X);
            s.fill(3, 7, 4, 5, 8, 5, vault);
            s.raw(3, 9, 4, hopper(Direction.DOWN)).item(3, 9, 4, 0, Items.DIAMOND, 16);
            s.raw(5, 6, 5, hopper(Direction.DOWN)).raw(5, 5, 5, Blocks.CHEST.defaultBlockState());
            s.runFor(200).compareOnlyAtTheEnd().provenBy(5, 5, 5, "diamondx16");
        }));

        scenes.add(ParityScene.of("create_pump_moves_water_tank_to_tank", s -> {
            s.fill(1, 0, 3, 9, 0, 5, STONE);
            s.raw(2, 1, 4, TANK).fluid(2, 1, 4, Fluids.WATER, 4000);
            pumpLine(s, 4);
            s.put(6, 1, 4, PIPE).raw(7, 1, 4, TANK);
            s.runFor(300).compareOnlyAtTheEnd().provenBy(7, 1, 4, "water=");
        }));

        scenes.add(ParityScene.of("create_pump_moves_lava_tank_to_tank", s -> {
            s.fill(1, 0, 3, 9, 0, 5, STONE);
            s.raw(2, 1, 4, TANK).fluid(2, 1, 4, Fluids.LAVA, 2000);
            pumpLine(s, 4);
            s.raw(6, 1, 4, TANK);
            s.runFor(300).compareOnlyAtTheEnd().provenBy(6, 1, 4, "lava=");
        }));

        scenes.add(ParityScene.of("create_pump_draws_from_a_pool", s -> {
            s.fill(0, 0, 2, 9, 0, 6, STONE);
            // A pool of sources west of the pipe's open end, walled in.
            s.fill(0, 1, 2, 2, 1, 2, STONE).fill(0, 1, 6, 2, 1, 6, STONE).fill(0, 1, 3, 0, 1, 5, STONE);
            s.fill(1, 1, 3, 1, 1, 5, WATER).raw(2, 1, 3, STONE).raw(2, 1, 5, STONE);
            s.raw(2, 1, 4, WATER);
            pumpLine(s, 4);
            s.raw(6, 1, 4, TANK);
            s.runFor(300).compareOnlyAtTheEnd().provenBy(6, 1, 4, "water=");
        }));

        scenes.add(ParityScene.of("create_pump_pours_out_of_an_open_pipe", s -> {
            s.fill(1, 0, 2, 12, 0, 6, STONE);
            s.fill(1, 1, 2, 12, 1, 2, STONE).fill(1, 1, 6, 12, 1, 6, STONE).fill(12, 1, 3, 12, 1, 5, STONE).fill(1, 1, 3, 1, 1, 5, STONE);
            s.raw(2, 1, 4, TANK).fluid(2, 1, 4, Fluids.WATER, 2000);
            pumpLine(s, 4);
            s.runFor(200).compareOnlyAtTheEnd().provenBy(6, 1, 4, "water");
        }));

        scenes.add(ParityScene.of("create_pipe_cut_and_put_back", s -> {
            s.fill(1, 0, 3, 10, 0, 5, STONE);
            s.raw(2, 1, 4, TANK).fluid(2, 1, 4, Fluids.WATER, 8000);
            pumpLine(s, 4);
            s.put(6, 1, 4, PIPE).put(7, 1, 4, PIPE).raw(8, 1, 4, TANK);
            s.breakAt(40, 6, 1, 4).placeAt(100, 6, 1, 4, PIPE);
            s.runFor(300).compareOnlyAtTheEnd().provenBy(8, 1, 4, "water=");
        }));

        scenes.add(ParityScene.of("create_pump_reversed_by_its_motor", s -> {
            s.fill(1, 0, 3, 9, 0, 5, STONE);
            s.raw(2, 1, 4, TANK);
            // Facing west on the same motor: the pump runs the other way, from the east tank into the west one.
            s.put(3, 1, 4, PIPE).raw(4, 1, 4, pump(Direction.WEST)).put(5, 1, 4, PIPE);
            s.raw(4, 2, 4, AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X)).raw(3, 2, 4, motor(Direction.EAST));
            s.raw(6, 1, 4, TANK).fluid(6, 1, 4, Fluids.WATER, 1000);
            s.settleTicks = 20;
            s.runFor(300).compareOnlyAtTheEnd().provenBy(2, 1, 4, "water=");
        }));

        scenes.add(ParityScene.of("create_spout_fills_a_bottle", s -> {
            s.fill(2, 0, 3, 7, 0, 5, STONE);
            s.raw(4, 1, 4, hopper(Direction.EAST)).item(4, 1, 4, 0, Items.GLASS_BOTTLE, 1);
            s.raw(5, 1, 4, AllBlocks.DEPOT.getDefaultState()).raw(5, 3, 4, AllBlocks.SPOUT.getDefaultState()).fluid(5, 3, 4, Fluids.WATER, 1000);
            s.runFor(200).compareOnlyAtTheEnd().provenBy(5, 1, 4, "potion=1");
        }));

        scenes.add(ParityScene.of("create_drain_empties_a_bucket", s -> {
            s.fill(2, 0, 3, 7, 0, 5, STONE);
            // A drain takes items only from its sides; the empty bucket goes on out of the far one.
            s.raw(4, 1, 4, AllBlocks.ITEM_DRAIN.getDefaultState()).raw(3, 1, 4, hopper(Direction.EAST)).item(3, 1, 4, 0, Items.WATER_BUCKET, 1);
            s.runFor(150).compareOnlyAtTheEnd().compareEntities().provenBy(4, 1, 4, "water=1000");
        }));

        scenes.add(ParityScene.of("create_pump_fills_a_tall_tank", s -> {
            s.fill(1, 0, 3, 9, 0, 5, STONE);
            s.raw(2, 1, 4, TANK).fluid(2, 1, 4, Fluids.WATER, 8000);
            pumpLine(s, 4);
            s.put(6, 1, 4, TANK).put(6, 2, 4, TANK).put(6, 3, 4, TANK);
            s.runFor(300).compareOnlyAtTheEnd().provenBy(6, 3, 4, "water=");
        }));

        scenes.add(ParityScene.of("create_pump_drains_a_tall_tank", s -> {
            s.fill(0, 0, 3, 9, 0, 5, STONE);
            s.put(2, 1, 4, TANK).put(2, 2, 4, TANK).fluid(2, 1, 4, Fluids.WATER, 10000);
            pumpLine(s, 4);
            s.raw(6, 1, 4, TANK);
            s.runFor(300).compareOnlyAtTheEnd().provenBy(6, 1, 4, "water=");
        }));

        scenes.add(ParityScene.of("create_pipe_splits_to_two_tanks", s -> {
            s.fill(1, 0, 1, 10, 0, 7, STONE);
            s.raw(2, 1, 4, TANK).fluid(2, 1, 4, Fluids.WATER, 8000);
            pumpLine(s, 4);
            s.put(6, 1, 4, PIPE).put(6, 1, 3, PIPE).raw(6, 1, 2, TANK).put(7, 1, 4, PIPE).raw(8, 1, 4, TANK);
            s.runFor(300).compareOnlyAtTheEnd().provenBy(6, 1, 2, "water=").provenBy(8, 1, 4, "water=");
        }));

        scenes.add(ParityScene.of("create_pipe_climbs_over_and_down", s -> {
            s.fill(1, 0, 3, 11, 0, 5, STONE);
            s.raw(2, 1, 4, TANK).fluid(2, 1, 4, Fluids.WATER, 4000);
            pumpLine(s, 4);
            s.put(6, 1, 4, PIPE).put(6, 2, 4, PIPE).put(6, 3, 4, PIPE).put(7, 3, 4, PIPE).put(8, 3, 4, PIPE).put(8, 2, 4, PIPE).raw(8, 1, 4, TANK);
            s.runFor(300).compareOnlyAtTheEnd().provenBy(8, 1, 4, "water=");
        }));

        scenes.add(ParityScene.of("create_glass_pipe_carries_it", s -> {
            s.fill(1, 0, 3, 9, 0, 5, STONE);
            s.raw(2, 1, 4, TANK).fluid(2, 1, 4, Fluids.WATER, 4000);
            pumpLine(s, 4);
            s.raw(6, 1, 4, AllBlocks.GLASS_FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.AXIS, Axis.X)).raw(7, 1, 4, TANK);
            s.runFor(300).compareOnlyAtTheEnd().provenBy(7, 1, 4, "water=");
        }));

        scenes.add(ParityScene.of("create_two_pumps_in_a_row", s -> {
            s.fill(1, 0, 3, 11, 0, 5, STONE);
            s.raw(2, 1, 4, TANK).fluid(2, 1, 4, Fluids.WATER, 6000);
            pumpLine(s, 4);
            s.raw(6, 1, 4, pump(Direction.EAST)).raw(6, 2, 4, AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
            s.raw(7, 2, 4, motor(Direction.WEST)).put(7, 1, 4, PIPE).raw(8, 1, 4, TANK);
            s.runFor(300).compareOnlyAtTheEnd().provenBy(8, 1, 4, "water=");
        }));

        scenes.add(ParityScene.of("create_pump_pours_lava_into_a_pool", s -> {
            s.fill(0, 0, 2, 11, 0, 6, STONE);
            s.fill(0, 1, 2, 11, 2, 2, STONE).fill(0, 1, 6, 11, 2, 6, STONE).fill(11, 1, 3, 11, 2, 5, STONE).fill(0, 1, 3, 0, 2, 5, STONE);
            s.raw(1, 1, 4, TANK).fluid(1, 1, 4, Fluids.LAVA, 2000);
            pumpLine(s, 3);
            s.fill(7, 1, 3, 10, 1, 5, WATER);
            s.runFor(200).compareOnlyAtTheEnd();
        }));

        scenes.add(ParityScene.of("create_pump_drains_a_cauldron", s -> {
            s.fill(1, 0, 3, 8, 0, 5, STONE);
            s.raw(2, 1, 4, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
            pumpLine(s, 4);
            s.raw(6, 1, 4, TANK);
            s.runFor(200).compareOnlyAtTheEnd().provenBy(6, 1, 4, "water=");
        }));

        scenes.add(ParityScene.of("create_boiler_runs_a_steam_engine", s -> {
            s.fill(0, 0, 2, 9, 0, 9, STONE);
            s.raw(2, 1, 4, TANK).fluid(2, 1, 4, Fluids.WATER, 8000);
            pumpLine(s, 4);
            s.raw(6, 1, 4, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
            s.put(5, 2, 4, PIPE).raw(6, 2, 4, TANK);
            // The shaft first: an engine set down turns the shaft two blocks out into its powered one.
            s.raw(6, 2, 7, AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
            s.raw(6, 2, 5, AllBlocks.STEAM_ENGINE.getDefaultState().setValue(SteamEngineBlock.FACE, AttachFace.WALL).setValue(SteamEngineBlock.FACING, Direction.SOUTH));
            s.raw(7, 2, 7, AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
            s.runFor(300).compareOnlyAtTheEnd().provenBy(6, 2, 7, "powered_shaft").provenBy(7, 2, 7, "working.speed=");
        }));

        return scenes;
    }

    /** Tanks joined into one, and one on its own, moved with what they hold. */
    static ParityScene fullTanks(ParitySpace.Move move) {
        ParityScene scene = ParityScene.of("moved_with_full_tanks", s -> {
            s.fill(2, 0, 3, 7, 0, 5, STONE);
            s.put(4, 1, 4, TANK).put(4, 2, 4, TANK).fluid(4, 1, 4, Fluids.WATER, 12000);
            s.raw(6, 1, 4, TANK).fluid(6, 1, 4, Fluids.LAVA, 500);
            s.moveAt(10).runFor(40).provenBy(4, 2, 4, "water=12000").provenBy(6, 1, 4, "lava=500");
        });
        // A tank keeps its controller's position: moved to a new virtual origin, it comes apart for a tick, empty on top.
        return move == ParitySpace.Move.RELOAD ? scene : scene.knownBroken("joined tanks come apart for a tick after a grid move");
    }

    /** A running pump, through a reload, a Grid Save and Sable. */
    static List<ParityScene> moved() {
        List<ParityScene> scenes = new ArrayList<>();

        // Moved, a grid comes back a tick or so behind: what the pump moved is compared once it has moved it all.
        scenes.add(ParityScene.of("moved_while_a_pump_runs", s -> {
            s.fill(1, 0, 3, 9, 0, 5, STONE);
            s.raw(2, 1, 4, TANK).fluid(2, 1, 4, Fluids.WATER, 1000);
            pumpLine(s, 4);
            s.raw(6, 1, 4, TANK);
            s.moveAt(15).compareOnlyAtTheEnd().runFor(300).provenBy(6, 1, 4, "water=");
        }));

        return scenes;
    }

    @GameTestGenerator
    public Collection<TestFunction> createFluids() {
        return RedstoneParityGameTest.everyLayout(scenes());
    }

    @GameTestGenerator
    public Collection<TestFunction> createFluidsMoved() {
        List<TestFunction> tests = new ArrayList<>();
        for (ParityRun.Layout layout : List.of(ParityRun.Layout.ONE_16, ParityRun.Layout.FOUR_8_SHIFTED)) {
            for (ParitySpace.Move move : List.of(ParitySpace.Move.RELOAD, ParitySpace.Move.GRID_SAVE, ParitySpace.Move.SABLE_MOVE, ParitySpace.Move.SABLE_ASSEMBLE)) {
                tests.add(RedstoneParityGameTest.test(fullTanks(move), layout, move));
                for (ParityScene scene : moved()) tests.add(RedstoneParityGameTest.test(scene, layout, move));
            }
        }
        return tests;
    }
}
