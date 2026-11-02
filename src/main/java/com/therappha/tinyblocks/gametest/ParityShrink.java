package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a failing random scene down to the smallest one that still fails ({@code -PshrinkSeed=seed}, best with
 * {@code -Psprint=true}): tries the scene without a run of its blocks or steps, keeps the cut when the scene still fails,
 * and halves the run until single items are left. The result, in run/space-diff, is the scene to write down as a fixed one.
 */
final class ParityShrink {

    private static final int TIMEOUT = 2_000_000;
    private static final int PAUSE_TICKS = 30;
    private final GameTestHelper helper;
    private final ParityRun.Layout layout;
    private final String testName;
    private ParityScene best;
    private List<Object> items;
    private int chunk, position, attempts;
    private ParityScene trying;
    private ParityRun run;
    private boolean finishing;
    private int pause;

    private ParityShrink(GameTestHelper helper, ParityScene scene, ParityRun.Layout layout, String testName) {
        this.helper = helper;
        this.layout = layout;
        this.testName = testName;
        this.best = scene;
    }

    static TestFunction test(long seed) {
        List<ParityRun.Layout> layouts = List.of(ParityRun.Layout.ONE_16, ParityRun.Layout.FOUR_8, ParityRun.Layout.FOUR_8_SHIFTED);
        ParityRun.Layout layout = layouts.get((int) (seed % layouts.size()));
        ParityScene scene = RandomRedstoneScene.generate(seed);
        String name = "shrink_" + seed;
        return new TestFunction("parity", name, TinyBlocks.MOD_ID + ":" + layout.template(), TIMEOUT, 0L, true,
                helper -> new ParityShrink(helper, scene, layout, name).start());
    }

    private void start() {
        reset();
        begin(best);
        helper.onEachTick(this::tick);
    }

    private void reset() {
        items = new ArrayList<>();
        items.addAll(best.blocks);
        items.addAll(best.slots);
        items.addAll(best.steps);
        chunk = Math.max(1, items.size() / 2);
        position = 0;
    }

    private void begin(ParityScene scene) {
        clear();
        trying = scene;
        run = new ParityRun(helper, scene, layout, testName);
        run.reports = false;
        pause = PAUSE_TICKS;
    }

    /** Empties all three spaces, without the updates: nothing of the last attempt may reach the next. */
    private void clear() {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 2, 1), new BlockPos(38, 15, 35))) {
            BlockPos at = helper.absolutePos(pos);
            if (!helper.getLevel().getBlockState(at).isAir()) helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), flags);
        }
    }

    private ParityScene without(int from, int to) {
        ParityScene scene = new ParityScene(best.name);
        scene.ticks = best.ticks;
        scene.watchOnlyAround = true;
        for (int i = 0; i < items.size(); i++) {
            if (i >= from && i < to) continue;
            Object item = items.get(i);
            if (item instanceof ParityScene.Placement p) scene.blocks.add(p);
            else if (item instanceof ParityScene.Slot s) scene.slots.add(s);
            else if (item instanceof ParityScene.Timed t) scene.steps.add(t);
        }
        return scene;
    }

    private void tick() {
        if (pause > 0) {
            // Ticks still queued for what was cleared run out first, or they would land on the next attempt's blocks.
            if (--pause == 0) run.buildAll();
            return;
        }
        run.tick();
        if (!run.finished() || finishing) return;
        attempts++;
        boolean first = attempts == 1;
        if (first && !run.diverged()) {
            helper.fail(testName + ": this seed doesn't fail in this layout");
            return;
        }
        if (run.diverged() && !first) {
            best = trying; // the cut stays; the same place now holds what came after it
            int keep = position;
            int size = chunk;
            reset();
            chunk = Math.min(size, Math.max(1, items.size()));
            position = keep;
        } else if (!first) {
            position += chunk;
        }
        if (position >= items.size()) {
            if (chunk == 1) {
                finish();
                return;
            }
            chunk = Math.max(1, chunk / 2);
            position = 0;
        }
        begin(without(position, Math.min(items.size(), position + chunk)));
    }

    private void finish() {
        List<String> report = new ArrayList<>();
        report.add("REDUZIDA " + testName + " em " + layout.name() + ", " + attempts + " tentativas, " + best.ticks + " ticks");
        best.blocks.forEach(p -> report.add(p.cell().toShortString() + " " + p.state()));
        best.slots.forEach(s -> report.add("item " + s.cell().toShortString() + " slot " + s.slot() + " " + s.stack()));
        best.steps.forEach(t -> report.add("tick " + t.tick() + ": " + t.what()));
        ParityRun.writeReport(testName + "_cena", report);
        // Once more with its report on: it says what the smallest scene still gets wrong, and fails the test with it.
        clear();
        trying = best;
        run = new ParityRun(helper, best, layout, testName);
        pause = PAUSE_TICKS;
        finishing = true;
    }
}
