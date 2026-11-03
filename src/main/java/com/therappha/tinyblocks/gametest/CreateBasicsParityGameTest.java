package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Create's parts not covered elsewhere, against real ones. Gears go by speed, compared tick by tick. Machines are
 * compared at the end: when an item goes by has never been the same tick twice, even between real copies, and what
 * came of it is the point. Parts that assemble a contraption stay out of grids: ContraptionInGridGameTest.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class CreateBasicsParityGameTest {

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

    private static BlockState motor(Direction facing) {
        return AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, facing);
    }

    private static BlockState shaft(Axis axis) {
        return AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, axis);
    }

    private static BlockState axial(BlockState block, Axis axis) {
        return block.setValue(RotatedPillarKineticBlock.AXIS, axis);
    }

    private static BlockState hopper(Direction to) {
        return Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, to);
    }

    private static BlockState lever() {
        return Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH);
    }

    /** A motor, a shaft, {@code part}, and three more shafts, along x at (.,2,7). */
    private static void line(ParityScene s, BlockState part) {
        s.raw(2, 2, 7, motor(Direction.EAST)).raw(3, 2, 7, shaft(Axis.X)).raw(4, 2, 7, part);
        for (int x = 5; x <= 8; x++) s.raw(x, 2, 7, shaft(Axis.X));
        s.settleTicks = 20;
    }

    static List<ParityScene> gears() {
        List<ParityScene> scenes = new ArrayList<>();

        scenes.add(ParityScene.of("create_gearbox_turns_four_ways", s -> {
            s.raw(2, 2, 7, motor(Direction.EAST)).raw(3, 2, 7, shaft(Axis.X));
            s.raw(4, 2, 7, axial(AllBlocks.GEARBOX.getDefaultState(), Axis.Y));
            s.raw(5, 2, 7, shaft(Axis.X)).raw(4, 2, 8, shaft(Axis.Z)).raw(4, 2, 9, shaft(Axis.Z)).raw(4, 2, 6, shaft(Axis.Z));
            s.settleTicks = 20;
            s.runFor(20).provenBy(4, 2, 9, "working.speed=").provenBy(5, 2, 7, "working.speed=-");
        }));

        scenes.add(ParityScene.of("create_clutch_on_a_lever", s -> {
            line(s, axial(AllBlocks.CLUTCH.getDefaultState(), Axis.X));
            s.put(4, 3, 7, lever()).use(10, 4, 3, 7).use(30, 4, 3, 7).runFor(50).provenBy(8, 2, 7, "working.speed=16.0");
        }));

        scenes.add(ParityScene.of("create_gearshift_on_a_lever", s -> {
            line(s, axial(AllBlocks.GEARSHIFT.getDefaultState(), Axis.X));
            s.put(4, 3, 7, lever()).use(10, 4, 3, 7).runFor(30).provenBy(8, 2, 7, "working.speed=-16.0");
        }));

        scenes.add(ParityScene.of("create_encased_shafts", s -> {
            line(s, axial(AllBlocks.ANDESITE_ENCASED_SHAFT.getDefaultState(), Axis.X));
            s.raw(9, 2, 7, axial(AllBlocks.BRASS_ENCASED_SHAFT.getDefaultState(), Axis.X)).raw(10, 2, 7, shaft(Axis.X));
            s.runFor(20).provenBy(10, 2, 7, "working.speed=16.0");
        }));

        scenes.add(ParityScene.of("create_cut_and_put_back", s -> {
            line(s, shaft(Axis.X));
            s.breakAt(10, 4, 2, 7).placeAt(30, 4, 2, 7, shaft(Axis.X)).runFor(50).provenBy(8, 2, 7, "working.speed=16.0");
        }));

        return scenes;
    }

    static List<ParityScene> machines() {
        List<ParityScene> scenes = new ArrayList<>();

        scenes.add(ParityScene.of("create_depot_passes_items_on", s -> {
            s.fill(3, 0, 4, 9, 0, 4, STONE);
            s.raw(4, 1, 4, hopper(Direction.EAST)).raw(5, 1, 4, AllBlocks.DEPOT.getDefaultState()).item(4, 1, 4, 0, Items.APPLE, 1);
            s.runFor(40).compareOnlyAtTheEnd().provenBy(5, 1, 4, "apple=1");
        }));

        scenes.add(ParityScene.of("create_press_presses_on_a_depot", s -> {
            s.fill(3, 0, 4, 9, 0, 4, STONE);
            s.raw(5, 1, 4, AllBlocks.DEPOT.getDefaultState()).raw(4, 1, 4, hopper(Direction.EAST)).item(4, 1, 4, 0, Items.IRON_INGOT, 2);
            s.raw(5, 3, 4, AllBlocks.MECHANICAL_PRESS.getDefaultState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
            s.raw(4, 3, 4, motor(Direction.EAST));
            s.settleTicks = 20;
            s.runFor(300).compareOnlyAtTheEnd().provenBy(5, 1, 4, "iron_sheet=1");
        }));

        scenes.add(ParityScene.of("create_mixer_turns_over_a_basin", s -> {
            s.fill(3, 0, 4, 9, 0, 4, STONE);
            s.raw(5, 1, 4, AllBlocks.BASIN.getDefaultState()).raw(4, 1, 4, hopper(Direction.EAST));
            s.item(4, 1, 4, 0, Items.ANDESITE, 1).item(4, 1, 4, 1, Items.IRON_NUGGET, 1);
            // A mixer takes no shaft: it is turned as a cog, by the one next to it.
            s.raw(5, 3, 4, AllBlocks.MECHANICAL_MIXER.getDefaultState()).raw(6, 3, 4, axial(AllBlocks.COGWHEEL.getDefaultState(), Axis.Y));
            s.raw(6, 4, 4, motor(Direction.DOWN));
            s.settleTicks = 20;
            // Even the real copies never mixed in the test world, so what is proven is a mixer turning over a full basin.
            s.runFor(100).compareOnlyAtTheEnd().provenBy(5, 1, 4, "andesite=1, iron_nugget=1").provenBy(5, 3, 4, "working.speed=16.0");
        }));

        scenes.add(ParityScene.of("create_saw_cuts_what_it_is_handed", s -> {
            s.fill(2, 0, 2, 9, 0, 6, STONE);
            s.raw(5, 1, 4, AllBlocks.MECHANICAL_SAW.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP)
                    .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
            s.raw(4, 1, 4, motor(Direction.EAST)).raw(5, 2, 4, hopper(Direction.DOWN)).item(5, 2, 4, 0, Items.OAK_LOG, 1);
            s.settleTicks = 20;
            s.runFor(300).compareOnlyAtTheEnd().compareEntities();
        }));

        scenes.add(ParityScene.of("create_saw_cuts_a_log_down", s -> {
            s.fill(2, 0, 2, 9, 0, 6, STONE);
            s.raw(4, 1, 4, motor(Direction.EAST));
            s.raw(5, 1, 4, AllBlocks.MECHANICAL_SAW.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
            s.raw(6, 1, 4, Blocks.OAK_LOG.defaultBlockState());
            s.settleTicks = 20;
            s.runFor(300).compareOnlyAtTheEnd().compareEntities();
        }));

        scenes.add(ParityScene.of("create_drill_breaks_a_block", s -> {
            s.fill(2, 0, 2, 9, 0, 6, STONE);
            s.raw(4, 1, 4, motor(Direction.EAST));
            s.raw(5, 1, 4, AllBlocks.MECHANICAL_DRILL.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
            s.raw(6, 1, 4, Blocks.DIRT.defaultBlockState());
            s.settleTicks = 20;
            s.runFor(300).compareOnlyAtTheEnd().compareEntities();
        }));

        scenes.add(ParityScene.of("create_deployer_places_what_it_holds", s -> {
            s.fill(2, 0, 2, 9, 0, 6, STONE);
            s.raw(4, 1, 4, STONE).raw(4, 2, 4, hopper(Direction.EAST)).item(4, 2, 4, 0, Items.OAK_PLANKS, 2);
            s.raw(5, 2, 4, AllBlocks.DEPLOYER.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST)
                    .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
            s.raw(5, 3, 4, motor(Direction.DOWN)).raw(7, 1, 4, STONE);
            s.settleTicks = 20;
            s.runFor(300).compareOnlyAtTheEnd().provenBy(7, 2, 4, "oak_planks");
        }));

        scenes.add(ParityScene.of("create_funnel_empties_a_chest", s -> {
            s.fill(2, 0, 2, 9, 0, 6, STONE);
            s.raw(4, 1, 4, Blocks.CHEST.defaultBlockState()).item(4, 1, 4, 0, Items.APPLE, 2);
            s.raw(5, 1, 4, AllBlocks.ANDESITE_FUNNEL.getDefaultState().setValue(FunnelBlock.FACING, Direction.EAST).setValue(FunnelBlock.EXTRACTING, true));
            s.runFor(120).compareOnlyAtTheEnd().compareEntities().knownBroken("#109: the funnel doesn't see the item it put out in its way");
        }));

        scenes.add(ParityScene.of("create_chute_carries_items_down", s -> {
            s.fill(3, 0, 4, 8, 0, 4, STONE);
            s.raw(4, 1, 4, Blocks.CHEST.defaultBlockState());
            s.put(4, 2, 4, AllBlocks.CHUTE.getDefaultState()).put(4, 3, 4, AllBlocks.CHUTE.getDefaultState()).put(4, 4, 4, AllBlocks.CHUTE.getDefaultState());
            s.raw(4, 5, 4, hopper(Direction.DOWN)).item(4, 5, 4, 0, Items.APPLE, 3);
            s.runFor(120).compareOnlyAtTheEnd().provenBy(4, 1, 4, "applex3");
        }));

        scenes.add(ParityScene.of("create_chute_drops_into_the_air", s -> {
            s.fill(3, 0, 4, 8, 0, 4, STONE);
            s.put(4, 3, 4, AllBlocks.CHUTE.getDefaultState()).raw(4, 4, 4, hopper(Direction.DOWN)).item(4, 4, 4, 0, Items.APPLE, 2);
            s.runFor(80).compareOnlyAtTheEnd().compareEntities();
        }));

        return scenes;
    }

    @GameTestGenerator
    public Collection<TestFunction> createBasics() {
        List<ParityScene> scenes = new ArrayList<>(gears());
        scenes.addAll(machines());
        return RedstoneParityGameTest.everyLayout(scenes);
    }
}
