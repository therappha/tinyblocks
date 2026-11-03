package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.gametest.RandomCreateScene.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Random Create networks (RandomCreateScene) against real ones, by seed. Each seed has its own way of being cut, moved
 * and placed, so a run of seeds goes through all of them: grids of 16, of 8, of 8 with the seams elsewhere; left where
 * they are, reloaded, taken by a Grid Save, set down by Sable, assembled into a sub-level; by the test, millions of
 * blocks out, or next to the world origin. A fixed set by default, {@code -PrandomSeeds=n} for a longer hunt of each
 * kind, {@code -PshrinkSeed=kind:seed} (machines, overstress, connectors) to cut a failing one down.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class CreateRandomParityGameTest {

    private static final int MACHINE_SEEDS = 24, OVERSTRESS_SEEDS = 8, CONNECTOR_SEEDS = 12;
    private static final List<ParityRun.Layout> LAYOUTS = List.of(ParityRun.Layout.ONE_16, ParityRun.Layout.FOUR_8, ParityRun.Layout.FOUR_8_SHIFTED);

    static ParityScene scene(Kind kind, long seed) {
        ParityScene scene = RandomCreateScene.generate(seed, kind);
        boolean nearOrigin = kind == Kind.CONNECTORS ? seed % 2 == 1 : (seed / 15) % 2 == 1;
        return scene.placed(nearOrigin ? ParityScene.Place.NEAR_ORIGIN : ParityScene.Place.BY_THE_TEST);
    }

    static ParityRun.Layout layout(Kind kind, long seed) {
        return kind == Kind.CONNECTORS ? ParityRun.Layout.BRIDGED : LAYOUTS.get((int) (seed % LAYOUTS.size()));
    }

    static ParitySpace.Move move(Kind kind, long seed) {
        if (kind == Kind.CONNECTORS) return ParitySpace.Move.STAYS;
        return ParitySpace.Move.values()[(int) ((seed / LAYOUTS.size()) % ParitySpace.Move.values().length)];
    }

    /**
     * A moved grid gets a while to settle again before the comparison starts, as a chunk that loads does. A hand crank
     * isn't turned until it has moved: one moved while turning loses its speed and only joins its network again once it
     * has stopped, as Create does with one a contraption moves (it saves no speed for a moved block, and a crank still in
     * use never asks again), which no real copy that stayed can show.
     */
    private static ParityScene settled(ParityScene scene, ParitySpace.Move move) {
        if (move == ParitySpace.Move.STAYS) return scene;
        scene.settleTicks = Math.max(scene.settleTicks, 40);
        int moved = -scene.settleTicks / 2;
        scene.steps.removeIf(step -> step.what().startsWith("crank") && step.tick() <= moved);
        return scene;
    }

    private static List<TestFunction> seeds(Kind kind, int byDefault) {
        String shrink = System.getProperty("tinyblocks.shrinkSeed", "");
        String prefix = kind.name().toLowerCase() + ":";
        if (shrink.startsWith(prefix)) {
            long seed = Long.parseLong(shrink.substring(prefix.length()));
            ParitySpace.Move move = move(kind, seed);
            return List.of(ParityShrink.test("shrink_" + kind.name().toLowerCase() + "_" + seed, settled(scene(kind, seed), move), layout(kind, seed), move));
        }
        if (!shrink.isEmpty()) return List.of();
        String count = System.getProperty("tinyblocks.randomSeeds", "");
        int seeds = count.isEmpty() ? byDefault : Integer.parseInt(count);
        List<TestFunction> tests = new ArrayList<>();
        for (int seed = 0; seed < seeds; seed++) {
            ParitySpace.Move move = move(kind, seed);
            tests.add(RedstoneParityGameTest.test(settled(scene(kind, seed), move), layout(kind, seed), move));
        }
        return tests;
    }

    @GameTestGenerator
    public Collection<TestFunction> createRandomMachines() {
        return seeds(Kind.MACHINES, MACHINE_SEEDS);
    }

    @GameTestGenerator
    public Collection<TestFunction> createRandomOverstress() {
        return seeds(Kind.OVERSTRESS, OVERSTRESS_SEEDS);
    }

    @GameTestGenerator
    public Collection<TestFunction> createRandomConnectors() {
        return seeds(Kind.CONNECTORS, CONNECTOR_SEEDS);
    }

    /**
     * Found by the random scenes (connectors seed 8): a real gearshift or clutch turned through a grid's connector, then
     * switched by its lever. Create took the side its rotation came in by from the difference between its position and
     * the connector's, which is in the grid's frame, so which side it took depended on where the test stood: all four
     * ways along the ground, so that some of them are always wrong without the fix. A funnel (1/7) and a gear (1/8)
     * stand in for the shaft before it.
     */
    @GameTestGenerator
    public Collection<TestFunction> createSwitchedThroughAConnector() {
        List<TestFunction> tests = new ArrayList<>();
        for (var block : List.of(AllBlocks.GEARSHIFT, AllBlocks.CLUTCH)) {
            for (Direction way : Direction.Plane.HORIZONTAL) {
                int size = way.getAxis() == Axis.X ? 7 : 8;
                String name = "create_" + block.getId().getPath() + "_after_a_1_" + size + "_grid_" + way.getName();
                ParityScene scene = ParityScene.of(name, s -> {
                    BlockPos centre = new BlockPos(7, 2, 7);
                    BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, way.getAxis());
                    BlockPos motor = centre.relative(way, -3), switched = centre.relative(way, -1), last = centre.relative(way, 3);
                    s.raw(motor.getX(), 2, motor.getZ(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, way));
                    for (int i = -2; i <= 3; i++) {
                        BlockPos at = centre.relative(way, i);
                        s.raw(at.getX(), 2, at.getZ(), i == -1 ? block.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, way.getAxis()) : shaft);
                    }
                    s.raw(switched.getX(), 3, switched.getZ(), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
                    s.standIns.add(new ParityScene.StandIn(centre.relative(way, -2), size, way.getAxis(), way, false));
                    s.settleTicks = 20;
                    s.use(10, switched.getX(), 3, switched.getZ()).use(30, switched.getX(), 3, switched.getZ()).runFor(50)
                            .provenBy(last.getX(), 2, last.getZ(), "working.speed=");
                });
                tests.add(RedstoneParityGameTest.test(scene, ParityRun.Layout.BRIDGED));
            }
        }
        return tests;
    }

    /**
     * A line of shafts and a belt across grids, far out on a seam of the virtual frame's window
     * (SubgridBlockEntity#virtualOrigin), and next to the world origin: grids that meet across that seam are no longer
     * neighbours in the frame.
     */
    @GameTestGenerator
    public Collection<TestFunction> createAcrossTheFrame() {
        List<TestFunction> tests = new ArrayList<>();
        for (ParityScene.Place place : List.of(ParityScene.Place.FRAME_SEAM, ParityScene.Place.NEAR_ORIGIN)) {
            ParityScene scene = ParityScene.of("create_shafts_" + place.name().toLowerCase(), s -> {
                BlockState shaft = AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Axis.X);
                s.raw(1, 2, 7, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
                for (int x = 2; x <= 14; x++) s.raw(x, 2, 7, shaft);
                s.settleTicks = 20;
                s.breakAt(10, 3, 2, 7).placeAt(25, 3, 2, 7, shaft).runFor(45).provenBy(14, 2, 7, "working.speed=16.0");
            }).placed(place);
            for (ParityRun.Layout layout : LAYOUTS) {
                // A single 1/16 grid has no seam to cross.
                boolean crosses = place == ParityScene.Place.FRAME_SEAM && layout != ParityRun.Layout.ONE_16;
                tests.add(RedstoneParityGameTest.test(crosses ? copyBroken(scene, "#112: grids across the seam aren't neighbours in the frame") : scene, layout));
            }
        }
        return tests;
    }

    private static ParityScene copyBroken(ParityScene scene, String why) {
        ParityScene copy = new ParityScene(scene.name).settingsFrom(scene).knownBroken(why);
        copy.blocks.addAll(scene.blocks);
        copy.steps.addAll(scene.steps);
        copy.proofs.addAll(scene.proofs);
        return copy;
    }
}
