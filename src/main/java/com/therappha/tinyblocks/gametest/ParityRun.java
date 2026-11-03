package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.diff.SpaceDiff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs one scene side by side: in the real world, in a second copy of it elsewhere, and in tiny grids. Each tick the
 * spaces are compared before the scene's steps for that tick run.
 *
 * The other real copies are the control. Vanilla's result can depend on where a build stands (dust updates go through a
 * hash set of positions), and where real copies disagree there is no one right answer, so what differs there is left
 * out of the comparison with the grids and counted. One control wasn't enough: two copies agree by chance often enough
 * that a thousand scenes turned up cases, hence three. What is left against the grids is the grids' doing.
 */
final class ParityRun {

    /** How the tiny copy is cut. The name goes into the test's name. */
    record Layout(String name, int gridSize, BlockPos seam, String template, int spaceWidth) {
        static final Layout ONE_16 = new Layout("g16", 16, BlockPos.ZERO, "parity_16", 16);
        static final Layout FOUR_8 = new Layout("g8", 8, BlockPos.ZERO, "parity_16", 16);
        /** The same space with the seams through other cells. */
        static final Layout FOUR_8_SHIFTED = new Layout("g8s", 8, new BlockPos(3, 0, 5), "parity_16", 16);
        static final Layout FOUR_16 = new Layout("big16", 16, BlockPos.ZERO, "parity_32", 32);
        static final Layout SIXTEEN_8 = new Layout("big8", 8, BlockPos.ZERO, "parity_32", 32);
    }

    private static final int MAX_SHOWN = 8;

    private final GameTestHelper helper;
    private final ParityScene scene;
    private final String testName;
    private final ParitySpace world, tiny;
    private final List<ParitySpace> controls;
    private final List<BlockPos> cells, allCells;
    private final SpaceDiff.Timeline againstTiny = new SpaceDiff.Timeline(), betweenReals = new SpaceDiff.Timeline();
    private final Set<String> undecided = new HashSet<>();
    private final List<String> eventsSeen = new ArrayList<>();
    private int tick;
    private final ParitySpace.Move move;
    private int lastHash, changesSeen;
    private String entitiesSeen = "";
    private boolean built;
    private final int gap;
    /** Off for a run that is only asked whether it still fails. */
    boolean reports = true;

    ParityRun(GameTestHelper helper, ParityScene scene, Layout layout, String testName) {
        this(helper, scene, layout, testName, ParitySpace.Move.STAYS);
    }

    ParityRun(GameTestHelper helper, ParityScene scene, Layout layout, String testName, ParitySpace.Move move) {
        this.helper = helper;
        this.move = move;
        this.tick = -scene.settleTicks;
        this.scene = scene;
        this.testName = testName;
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        gap = layout.spaceWidth() + 1;
        world = new ParitySpace.Real(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)), player);
        controls = List.of(new ParitySpace.Real(helper.getLevel(), helper.absolutePos(new BlockPos(1 + gap, 2, 1)), player),
                new ParitySpace.Real(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1 + gap)), player),
                new ParitySpace.Real(helper.getLevel(), helper.absolutePos(new BlockPos(1 + gap, 2, 1 + gap)), player));
        tiny = new ParitySpace.Tiny(helper.getLevel(), helper.absolutePos(new BlockPos(1 + 2 * gap, 2, 1)),
                layout.gridSize(), layout.seam(), player);
        int[] size = scene.size();
        allCells = SpaceDiff.box(size[0], size[1], size[2]);
        cells = scene.watchOnlyAround ? around(scene) : allCells;
    }

    /** The cells a scene built on, and those next to them: comparing all of a sparse scene's box every tick adds nothing. */
    private static List<BlockPos> around(ParityScene scene) {
        Set<BlockPos> near = new java.util.LinkedHashSet<>();
        for (ParityScene.Placement p : scene.blocks) {
            near.add(p.cell());
            for (Direction direction : Direction.values()) {
                BlockPos next = p.cell().relative(direction);
                if (next.getX() >= 0 && next.getY() >= 0 && next.getZ() >= 0) near.add(next);
            }
        }
        return List.copyOf(near);
    }

    void start() {
        helper.onEachTick(() -> {
            if (!built) {
                // A chunk not ticking yet would hold the real copies still while the grids run: it would look like a difference.
                if (!chunksTicking()) return;
                buildAll();
                built = true;
                return;
            }
            tick();
        });
    }

    private boolean chunksTicking() {
        for (int x = 0; x < 2 * gap + 6; x += 16) {
            for (int z = 0; z < 2 * gap; z += 16) {
                long chunk = net.minecraft.world.level.ChunkPos.asLong(helper.absolutePos(new BlockPos(x, 0, z)));
                if (!helper.getLevel().shouldTickBlocksAt(chunk)) return false;
            }
        }
        return true;
    }

    private List<ParitySpace> all() {
        List<ParitySpace> spaces = new ArrayList<>(controls);
        spaces.add(0, world);
        spaces.add(tiny);
        return spaces;
    }

    void buildAll() {
        for (ParitySpace space : all()) build(space);
    }

    /** Whether the scene has run to its end, and whether a tiny difference was left that the control doesn't explain. */
    boolean finished() {
        return tick > scene.ticks;
    }

    boolean diverged() {
        return !againstTiny.spans().isEmpty();
    }

    private void build(ParitySpace space) {
        for (ParityScene.Placement p : scene.blocks) space.build(p.cell(), p.state(), p.shaped());
        for (ParityScene.Slot s : scene.slots) {
            if (space.blockEntity(s.cell()) instanceof Container container) {
                container.setItem(s.slot(), s.stack().copy());
                container.setChanged();
            }
        }
    }

    void tick() {
        if (tick > scene.ticks) return;
        if (tick >= 0) compare();
        // Its next spot is a row over, past the space it was built in.
        int moveTick = scene.moveAt != null ? scene.moveAt : -scene.settleTicks / 2;
        if (tick == moveTick && move != ParitySpace.Move.STAYS) ((ParitySpace.Tiny) tiny).move(move, new BlockPos(0, 0, gap));
        for (ParityScene.Timed step : scene.steps) {
            if (step.tick() != tick) continue;
            eventsSeen.add("tick " + tick + ": " + step.what());
            for (ParitySpace space : all()) step.step().run(space);
        }
        if (tick == scene.ticks) ((ParitySpace.Tiny) tiny).cleanUp();
        if (tick == scene.ticks && reports) finish();
        tick++;
    }

    private void compare() {
        if (scene.onlyAtTheEnd && tick != scene.ticks) return;
        List<BlockPos> cells = tick == scene.ticks ? allCells : this.cells; // nothing can have hidden elsewhere by the end
        int hash = 0;
        for (BlockPos cell : cells) hash = 31 * hash + SpaceDiff.facts(world, cell).hashCode();
        if (tick > 0 && hash != lastHash) changesSeen++; // a scene where nothing happens proves nothing: the report says so
        lastHash = hash;
        List<SpaceDiff.Difference> reals = new ArrayList<>();
        for (ParitySpace control : controls) reals.addAll(SpaceDiff.compare(world, control, cells));
        List<SpaceDiff.Difference> againstWorld = SpaceDiff.compare(world, tiny, cells);
        if (scene.entitiesAt != null && tick == (scene.entitiesAt < 0 ? scene.ticks : scene.entitiesAt)) {
            int[] size = scene.size();
            for (ParitySpace control : controls) reals.addAll(SpaceDiff.compareTotals(world.entities(size), control.entities(size)));
            againstWorld.addAll(SpaceDiff.compareTotals(world.entities(size), tiny.entities(size)));
            entitiesSeen = world.entities(size).toString();
        }
        betweenReals.record(tick, reals);
        for (SpaceDiff.Difference d : reals) undecided.add(d.cell().toShortString() + d.field());
        List<SpaceDiff.Difference> counted = new ArrayList<>();
        for (SpaceDiff.Difference d : againstWorld) {
            if (!undecided.contains(d.cell().toShortString() + d.field())) counted.add(d);
        }
        againstTiny.record(tick, counted);
    }

    private void finish() {
        List<String> failing = new ArrayList<>();
        for (SpaceDiff.Span span : againstTiny.spans()) failing.add(againstTiny.describe(span, "world", "tiny"));
        for (ParityScene.Proof proof : scene.proofs) {
            String facts = SpaceDiff.facts(world, proof.cell()).toString();
            if (!facts.contains(proof.fact())) failing.add("the world never showed " + proof.fact() + " at " + proof.cell().toShortString() + ", only " + facts);
        }
        List<String> report = new ArrayList<>();
        report.add((failing.isEmpty() ? "PASSED " : "FAILED ") + testName + " (" + scene.ticks + " ticks, "
                + changesSeen + " changes in the world)");
        report.addAll(eventsSeen);
        if (!failing.isEmpty()) report.add("--- divergences: " + failing.size());
        report.addAll(failing);
        if (!failing.isEmpty()) {
            report.add("--- scene");
            for (ParityScene.Placement p : scene.blocks) report.add(p.cell().toShortString() + " " + p.state());
        }
        if (!undecided.isEmpty()) report.add("--- left out of the comparison, the real against real control diverged: " + undecided.size());
        for (SpaceDiff.Span span : betweenReals.spans()) report.add("  " + betweenReals.describe(span, "world", "control"));
        if (!entitiesSeen.isEmpty()) report.add("--- entities in the world: " + entitiesSeen);
        report.add("--- final state in the world");
        for (BlockPos cell : allCells) {
            if (!world.state(cell).isAir()) report.add(cell.toShortString() + " " + world.state(cell));
        }
        write(report);
        if (failing.isEmpty()) {
            helper.succeed();
            return;
        }
        StringBuilder message = new StringBuilder(testName + ": " + failing.size() + " divergences");
        for (int i = 0; i < Math.min(MAX_SHOWN, failing.size()); i++) message.append("\n  ").append(failing.get(i));
        // It ends up on a page of the test's log book, which holds 1024 characters; the whole report is in the file.
        helper.fail(message.length() > 900 ? message.substring(0, 900) : message.toString());
    }

    static void writeReport(String testName, List<String> report) {
        try {
            Path dir = Path.of("space-diff");
            Files.createDirectories(dir);
            Files.write(dir.resolve(testName + ".txt"), report);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void write(List<String> report) {
        writeReport(testName, report);
    }

    static int timeout(ParityScene scene) {
        return scene.ticks + scene.settleTicks + 2000; // most of it only for chunks to start ticking
    }
}
