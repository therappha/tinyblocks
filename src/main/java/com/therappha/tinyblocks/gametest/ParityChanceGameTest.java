package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * No parity scene builds anything that changes by chance: a block with random ticks (copper weathering, crops, grass,
 * ice, kelp), or fire and lava when fire ticks, which draw from the level's random. The real copies and the grid draw differently, so such a
 * scene passes or fails by luck. A scene that needs one says how it stays out of luck's way here.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class ParityChanceGameTest {

    static List<ParityScene> all() {
        List<ParityScene> scenes = new ArrayList<>(RedstoneParityGameTest.scenes());
        for (int seed = 0; seed < 60; seed++) scenes.add(RandomRedstoneScene.generate(seed));
        scenes.addAll(CreateBasicsParityGameTest.gears());
        scenes.addAll(CreateBasicsParityGameTest.machines());
        scenes.addAll(LifecycleParityGameTest.scenes());
        scenes.addAll(ItemParityGameTest.scenes());
        scenes.addAll(ReactiveParityGameTest.scenes());
        scenes.addAll(FluidParityGameTest.scenes());
        for (RandomCreateScene.Kind kind : RandomCreateScene.Kind.values()) {
            for (int seed = 0; seed < 60; seed++) scenes.add(RandomCreateScene.generate(seed, kind));
        }
        return scenes;
    }

    /** Fire's ticks and lava's random ones do nothing but schedule the next while doFireTick is off, as on the test server. */
    static boolean byChance(BlockState state, boolean fireTicks) {
        boolean fire = state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA);
        return fire ? fireTicks : state.isRandomlyTicking();
    }

    @GameTest(template = "empty_platform", timeoutTicks = 20)
    public void parityScenesBuildNothingThatChangesByChance(GameTestHelper helper) {
        boolean fireTicks = helper.getLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK);
        Set<String> found = new TreeSet<>();
        for (ParityScene scene : all()) {
            List<BlockState> states = new ArrayList<>(scene.placedLater);
            for (ParityScene.Placement p : scene.blocks) states.add(p.state());
            for (BlockState state : states) {
                if (!byChance(state, fireTicks)) continue;
                found.add(scene.name + ": " + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
            }
        }
        helper.assertTrue(found.isEmpty(), "scenes that change by chance: " + found);
        helper.succeed();
    }
}
