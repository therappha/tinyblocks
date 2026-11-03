package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.GridSight;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The rules for when a grid skips its frame work. What's drawn can't be tested here: a GameTest has no GPU. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class GridSightGameTest {

    private static final double RENDER_DISTANCE = 192;

    @GameTest(template = "empty_platform")
    public void aGridIsFarOnlyPastTheRenderDistance(GameTestHelper helper) {
        GridSight sight = new GridSight();
        sight.update(RENDER_DISTANCE, RENDER_DISTANCE, true);
        helper.assertFalse(sight.far(), "far at the render distance itself");
        sight.update(RENDER_DISTANCE + 1, RENDER_DISTANCE, true);
        helper.assertTrue(sight.far(), "not far past the render distance");
        helper.succeed();
    }

    @GameTest(template = "empty_platform")
    public void aFarGridNeedsTheMarginToBeNearAgain(GameTestHelper helper) {
        GridSight sight = new GridSight();
        sight.update(RENDER_DISTANCE + 1, RENDER_DISTANCE, true);
        sight.update(RENDER_DISTANCE - 1, RENDER_DISTANCE, true);
        helper.assertTrue(sight.far(), "near again without the margin: it would flicker at the edge");
        sight.update(RENDER_DISTANCE - GridSight.NEAR_AGAIN_MARGIN, RENDER_DISTANCE, true);
        helper.assertFalse(sight.far(), "still far inside the margin");
        helper.succeed();
    }

    @GameTest(template = "empty_platform")
    public void aGridIsOffScreenOnlyAfterSomeFrames(GameTestHelper helper) {
        GridSight sight = new GridSight();
        for (int frame = 1; frame < GridSight.OFF_AFTER_FRAMES; frame++) sight.update(1, RENDER_DISTANCE, false);
        helper.assertFalse(sight.away(), "off screen too soon: turning fast would flicker it");
        sight.update(1, RENDER_DISTANCE, false);
        helper.assertTrue(sight.away(), "not off screen after the delay");
        sight.update(1, RENDER_DISTANCE, true);
        helper.assertFalse(sight.away(), "still away back on screen");
        helper.succeed();
    }

    @GameTest(template = "empty_platform")
    public void perFrameDrawingStopsAtVanillasDistance(GameTestHelper helper) {
        helper.assertTrue(GridSight.drawnPerFrame(GridSight.PER_FRAME_DISTANCE), "not drawn at 64 blocks");
        helper.assertFalse(GridSight.drawnPerFrame(GridSight.PER_FRAME_DISTANCE + 1), "drawn past 64 blocks");
        helper.succeed();
    }
}
