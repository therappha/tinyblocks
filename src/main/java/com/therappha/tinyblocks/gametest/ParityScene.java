package com.therappha.tinyblocks.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Something to build and do, written once in cells and run the same way in every space being compared. Nothing here
 * says what should happen: the real world's run is the answer.
 */
final class ParityScene {

    /** Done in a space at a tick of the scene: the world's and the grid's versions of the same action. */
    interface Step {
        void run(ParitySpace space);
    }

    record Placement(BlockPos cell, BlockState state, boolean shaped) {}
    record Slot(BlockPos cell, int slot, ItemStack stack) {}
    record Timed(int tick, String what, Step step) {}
    record Proof(BlockPos cell, String fact) {}
    /**
     * A cell whose block, in the space under test, is a grid of {@code gridSize} standing in for it: connectors on its
     * two faces along {@code axis} joined inside by tiny parts (ParitySpace.Bridged). {@code downstream} is the face
     * rotation leaves by, the one whose connector the cell shows.
     */
    record StandIn(BlockPos cell, int gridSize, Direction.Axis axis, Direction downstream, boolean jog) {}

    /** Where the spaces stand: by the test, a few million blocks out; next to the world origin; or across a seam of the virtual frame. */
    enum Place {
        BY_THE_TEST, NEAR_ORIGIN, FRAME_SEAM
    }

    final String name;
    final List<Placement> blocks = new ArrayList<>();
    final List<Slot> slots = new ArrayList<>();
    final List<Timed> steps = new ArrayList<>();
    final List<Proof> proofs = new ArrayList<>();
    final List<StandIn> standIns = new ArrayList<>();
    /** Compared only at the end, these fields everywhere and every field of these cells: what may get there on another tick. */
    final Set<String> endOnlyFields = new HashSet<>();
    final Set<BlockPos> endOnlyCells = new HashSet<>();
    Place place = Place.BY_THE_TEST;
    int ticks = 60;
    /** How long it runs before the comparison starts, built and, when a grid is moved, moved halfway through this. */
    int settleTicks = 12;
    /** Compared tick by tick only around what was built; the whole box still at the end. */
    boolean watchOnlyAround = false;
    /** Compared only at the end: for what gets to the same place by another way (a falling block is an entity in the world, not in a grid). */
    boolean onlyAtTheEnd = false;
    /** Set when this scene shows a bug with an issue of its own: its tests stay, as not required, until that is fixed. */
    @javax.annotation.Nullable String knownBroken = null;
    /** The tick at which the entities around each space are compared; none when null. */
    @javax.annotation.Nullable Integer entitiesAt = null;
    /** The tick at which a grid that moves does so; halfway through the settling when null. */
    @javax.annotation.Nullable Integer moveAt = null;

    ParityScene(String name) {
        this.name = name;
    }

    static ParityScene of(String name, Consumer<ParityScene> build) {
        ParityScene scene = new ParityScene(name);
        build.accept(scene);
        return scene;
    }

    /** Placed as it is, with its properties exactly as given (both halves of a door, a double chest, a schematic). */
    ParityScene raw(int x, int y, int z, BlockState state) {
        blocks.add(new Placement(new BlockPos(x, y, z), state, false));
        return this;
    }

    /** Placed shaped against what is around it, as a player's placement would (dust connects). */
    ParityScene put(int x, int y, int z, BlockState state) {
        blocks.add(new Placement(new BlockPos(x, y, z), state, true));
        return this;
    }

    ParityScene fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int y = y0; y <= y1; y++)
            for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++) put(x, y, z, state);
        return this;
    }

    ParityScene item(int x, int y, int z, int slot, Item item, int count) {
        slots.add(new Slot(new BlockPos(x, y, z), slot, new ItemStack(item, count)));
        return this;
    }

    ParityScene use(int tick, int x, int y, int z) {
        BlockPos cell = new BlockPos(x, y, z);
        steps.add(new Timed(tick, "use " + cell.toShortString(), space -> space.use(cell)));
        return this;
    }

    ParityScene breakAt(int tick, int x, int y, int z) {
        BlockPos cell = new BlockPos(x, y, z);
        steps.add(new Timed(tick, "break " + cell.toShortString(), space -> space.breakCell(cell)));
        return this;
    }

    /** Takes what is there away and leaves nothing: breaking water, in the world, leaves the water. */
    ParityScene clearAt(int tick, int x, int y, int z) {
        BlockPos cell = new BlockPos(x, y, z);
        steps.add(new Timed(tick, "clear " + cell.toShortString(), space -> space.clear(cell)));
        return this;
    }

    ParityScene placeAt(int tick, int x, int y, int z, BlockState state) {
        BlockPos cell = new BlockPos(x, y, z);
        steps.add(new Timed(tick, "place " + state + " at " + cell.toShortString(), space -> space.place(cell, state)));
        return this;
    }

    ParityScene insert(int tick, int x, int y, int z, Item item, int count) {
        BlockPos cell = new BlockPos(x, y, z);
        steps.add(new Timed(tick, "put " + count + " " + item + " at " + cell.toShortString(),
                space -> space.insert(cell, new ItemStack(item, count))));
        return this;
    }

    ParityScene take(int tick, int x, int y, int z, int count) {
        BlockPos cell = new BlockPos(x, y, z);
        steps.add(new Timed(tick, "take " + count + " from " + cell.toShortString(), space -> space.take(cell, count)));
        return this;
    }

    /** A player's turn of a hand crank, which keeps it going for a few ticks. */
    ParityScene crank(int tick, int x, int y, int z) {
        BlockPos cell = new BlockPos(x, y, z);
        steps.add(new Timed(tick, "crank " + cell.toShortString(), space -> space.crank(cell)));
        return this;
    }

    ParityScene runFor(int ticks) {
        this.ticks = ticks;
        return this;
    }

    /**
     * Not what should happen, only that the real run did something worth comparing: a scene whose press never pressed
     * would pass with a press that doesn't work. {@code fact} is looked for in what SpaceDiff knows of the cell at the end.
     */
    ParityScene provenBy(int x, int y, int z, String fact) {
        proofs.add(new Proof(new BlockPos(x, y, z), fact));
        return this;
    }

    ParityScene knownBroken(String why) {
        knownBroken = why;
        return this;
    }

    ParityScene compareOnlyAtTheEnd() {
        onlyAtTheEnd = true;
        return this;
    }

    /** Also what is left lying around at the end: dropped items, by what they hold, and any other entity. */
    ParityScene compareEntities() {
        return compareEntitiesAt(-1);
    }

    ParityScene compareEntitiesAt(int tick) {
        entitiesAt = tick;
        return this;
    }

    ParityScene endOnlyField(String prefix) {
        endOnlyFields.add(prefix);
        return this;
    }

    ParityScene endOnlyCell(BlockPos cell) {
        endOnlyCells.add(cell);
        return this;
    }

    ParityScene placed(Place place) {
        this.place = place;
        return this;
    }

    /** The same scene without what is said to the setter: everything but what was built and done in it. */
    ParityScene settingsFrom(ParityScene other) {
        ticks = other.ticks;
        settleTicks = other.settleTicks;
        watchOnlyAround = other.watchOnlyAround;
        onlyAtTheEnd = other.onlyAtTheEnd;
        knownBroken = other.knownBroken;
        entitiesAt = other.entitiesAt;
        moveAt = other.moveAt;
        place = other.place;
        standIns.addAll(other.standIns);
        endOnlyFields.addAll(other.endOnlyFields);
        endOnlyCells.addAll(other.endOnlyCells);
        return this;
    }

    ParityScene moveAt(int tick) {
        moveAt = tick;
        return this;
    }

    /** The box holding every cell the scene touches, and one more on the far sides for what spills out of it. */
    int[] size() {
        int w = 0, h = 0, d = 0;
        for (Placement p : blocks) {
            w = Math.max(w, p.cell().getX() + 2);
            h = Math.max(h, p.cell().getY() + 2);
            d = Math.max(d, p.cell().getZ() + 2);
        }
        return new int[] {w, h, d};
    }
}
