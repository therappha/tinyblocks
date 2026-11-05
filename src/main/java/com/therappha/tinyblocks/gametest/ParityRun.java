package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.diff.SpaceDiff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;

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
        /** Real blocks, with grids standing in for some of them (ParityScene.StandIn, ParitySpace.Bridged): a whole space wide, so the bigger template. */
        static final Layout BRIDGED = new Layout("bridged", 16, BlockPos.ZERO, "parity_32", 16);
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
    private int lastHash, changesSeen, overstressedTicks, beltTicks;
    private String entitiesSeen = "";
    private boolean built;
    private final int gap;
    private final BlockPos base;
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
        base = base(helper, scene.place, gap);
        world = new ParitySpace.Real(helper.getLevel(), base, player);
        controls = List.of(new ParitySpace.Real(helper.getLevel(), base.offset(gap, 0, 0), player),
                new ParitySpace.Real(helper.getLevel(), base.offset(0, 0, gap), player),
                new ParitySpace.Real(helper.getLevel(), base.offset(gap, 0, gap), player));
        BlockPos tinyAt = base.offset(2 * gap, 0, 0);
        tiny = scene.standIns.isEmpty()
                ? new ParitySpace.Tiny(helper.getLevel(), tinyAt, layout.gridSize(), layout.seam(), player)
                : new ParitySpace.Bridged(helper.getLevel(), tinyAt, player, scene.standIns);
        int[] size = scene.size();
        allCells = SpaceDiff.box(size[0], size[1], size[2]);
        cells = scene.watchOnlyAround ? around(scene) : allCells;
        if (scene.place != ParityScene.Place.BY_THE_TEST) forEachChunk(chunk -> helper.getLevel().setChunkForced(chunk.x, chunk.z, true));
    }

    /** Spaces away from the test each get ground of their own: tests run side by side. */
    private static final java.util.concurrent.atomic.AtomicInteger AWAY = new java.util.concurrent.atomic.AtomicInteger();
    /** A seam of the virtual frame's horizontal window (SubgridBlockEntity#virtualOrigin), millions of blocks out. */
    private static final int FRAME_SEAM_X = 2 * SubgridBlockEntity.HORIZONTAL_WINDOW + SubgridBlockEntity.HORIZONTAL_WINDOW / 2;

    /** Where the real copy's cell (0,0,0) is; the others are a row over from it, the tiny one two. */
    private static BlockPos base(GameTestHelper helper, ParityScene.Place place, int gap) {
        int slot = place == ParityScene.Place.BY_THE_TEST ? 0 : AWAY.getAndIncrement();
        return switch (place) {
            case BY_THE_TEST -> helper.absolutePos(new BlockPos(1, 2, 1));
            case NEAR_ORIGIN -> new BlockPos(64 + 128 * (slot % 16), 100, 64 + 128 * (slot / 16));
            // The tiny copy's first column of grids is the last one before the seam.
            case FRAME_SEAM -> new BlockPos(FRAME_SEAM_X - 1 - 2 * gap, 100, 4_000_000 + 128 * slot);
        };
    }

    /** The chunks every space of this run stands in. */
    private void forEachChunk(java.util.function.Consumer<net.minecraft.world.level.ChunkPos> action) {
        int[] size = scene.size();
        // A tiny copy stands in a few blocks; one with grids standing in for blocks is as wide as a real one.
        int tinyWidth = scene.standIns.isEmpty() ? 5 : size[0];
        BlockPos far = base.offset(2 * gap + tinyWidth, 0, gap + size[2]);
        for (int x = base.getX() >> 4; x <= far.getX() >> 4; x++) {
            for (int z = base.getZ() >> 4; z <= far.getZ() >> 4; z++) action.accept(new net.minecraft.world.level.ChunkPos(x, z));
        }
    }

    /** Ground away from the test isn't cleared by the test framework: what a run left there goes, and the chunks are let go once done. */
    private void clearAway(boolean letGo) {
        if (scene.place == ParityScene.Place.BY_THE_TEST) return;
        int[] size = scene.size();
        var level = helper.getLevel();
        BlockPos far = base.offset(2 * gap + size[0], size[1], gap + size[2]);
        int flags = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS;
        for (BlockPos at : BlockPos.betweenClosed(base.below(), far)) {
            // Ground to stand on while it runs, as by the test: a sub-level assembled in the air falls.
            BlockState wanted = at.getY() < base.getY() && !letGo ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                    : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            if (level.getBlockState(at) != wanted) level.setBlock(at, wanted, flags);
        }
        var box = new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(base), Vec3.atLowerCornerOf(far)).inflate(4);
        for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box, e -> !(e instanceof Player))) entity.discard();
        if (letGo) forEachChunk(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
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
                clearAway(false); // ground away from the test keeps what an earlier run of the server left there
                buildAll();
                built = true;
                return;
            }
            tick();
        });
    }

    private boolean chunksTicking() {
        boolean[] ticking = {true};
        forEachChunk(chunk -> ticking[0] &= helper.getLevel().shouldTickBlocksAt(chunk.toLong()));
        return ticking[0];
    }

    private List<ParitySpace> all() {
        List<ParitySpace> spaces = new ArrayList<>(controls);
        spaces.add(0, world);
        spaces.add(tiny);
        return spaces;
    }

    void buildAll() {
        if (scene.entitiesAt != null) {
            int[] size = scene.size();
            for (ParitySpace space : all()) ParityEntities.watch(helper.getLevel(), space, size);
        }
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
        for (ParityScene.Tank t : scene.tanks) {
            BlockEntity be = space.blockEntity(t.cell());
            if (be == null || be.getLevel() == null) continue;
            IFluidHandler handler = be.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, null);
            if (handler != null) handler.fill(t.stack().copy(), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    void tick() {
        if (tick > scene.ticks) return;
        if (tick >= 0) compare();
        // Its next spot is a row over, past the space it was built in.
        int moveTick = scene.moveAt != null ? scene.moveAt : -scene.settleTicks / 2;
        if (tick == moveTick && move != ParitySpace.Move.STAYS) {
            ((ParitySpace.Tiny) tiny).move(move, new BlockPos(0, 0, gap));
            // A chunk reload changes what a real block entity holds as well: the real copies go through it with the grid.
            if (move == ParitySpace.Move.RELOAD) {
                for (ParitySpace space : all()) if (space instanceof ParitySpace.Real real) real.reload(scene.size());
            }
        }
        for (ParityScene.Timed step : scene.steps) {
            if (step.tick() != tick) continue;
            eventsSeen.add("tick " + tick + ": " + step.what());
            for (ParitySpace space : all()) step.step().run(space);
        }
        if (tick == scene.ticks) {
            if (tiny instanceof ParitySpace.Tiny moved) moved.cleanUp();
            for (ParitySpace space : all()) ParityEntities.stopWatching(space);
        }
        try {
            if (tick == scene.ticks && reports) finish();
        } finally {
            if (tick == scene.ticks) clearAway(true);
            tick++;
        }
    }

    private void compare() {
        if (scene.onlyAtTheEnd && tick != scene.ticks) return;
        List<BlockPos> cells = tick == scene.ticks ? allCells : this.cells; // nothing can have hidden elsewhere by the end
        int hash = 0;
        boolean overstressed = false, onBelts = false;
        for (BlockPos cell : cells) {
            var facts = SpaceDiff.facts(world, cell);
            hash = 31 * hash + facts.hashCode();
            overstressed |= "true".equals(facts.get(SpaceDiff.WORKING + "overstressed"));
            onBelts |= facts.containsKey(SpaceDiff.WORKING + "on_belt") && !"{}".equals(facts.get(SpaceDiff.WORKING + "on_belt"));
        }
        // What a random scene got up to, for its report: one that never did what it was built for proves little.
        if (overstressed) overstressedTicks++;
        if (onBelts) beltTicks++;
        if (tick > 0 && hash != lastHash) changesSeen++; // a scene where nothing happens proves nothing: the report says so
        lastHash = hash;
        List<SpaceDiff.Difference> reals = new ArrayList<>();
        for (ParitySpace control : controls) reals.addAll(SpaceDiff.compare(world, control, cells));
        List<SpaceDiff.Difference> againstWorld = SpaceDiff.compare(world, tiny, cells);
        if (scene.entitiesAt != null && tick == (scene.entitiesAt < 0 ? scene.ticks : scene.entitiesAt)) {
            for (ParitySpace control : controls) reals.addAll(SpaceDiff.compareTotals(world.entities(), control.entities()));
            againstWorld.addAll(SpaceDiff.compareTotals(world.entities(), tiny.entities()));
            entitiesSeen = world.entities().toString();
        }
        if (tick != scene.ticks && !(scene.endOnlyFields.isEmpty() && scene.endOnlyCells.isEmpty())) {
            reals.removeIf(this::comparedAtTheEnd);
            againstWorld.removeIf(this::comparedAtTheEnd);
        }
        betweenReals.record(tick, reals);
        for (SpaceDiff.Difference d : reals) undecided.add(d.cell().toShortString() + d.field());
        List<SpaceDiff.Difference> counted = new ArrayList<>();
        for (SpaceDiff.Difference d : againstWorld) {
            if (!undecided.contains(d.cell().toShortString() + d.field())) counted.add(d);
        }
        againstTiny.record(tick, counted);
    }

    private boolean comparedAtTheEnd(SpaceDiff.Difference d) {
        if (scene.endOnlyCells.contains(d.cell())) return true;
        for (String prefix : scene.endOnlyFields) if (d.field().startsWith(prefix)) return true;
        return false;
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
                + changesSeen + " changes in the world" + (overstressedTicks > 0 ? ", overstressed for " + overstressedTicks + " ticks" : "")
                + (beltTicks > 0 ? ", items on belts for " + beltTicks + " ticks" : "") + ")");
        for (ParityScene.StandIn standIn : scene.standIns) report.add("grid standing in: " + standIn);
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
