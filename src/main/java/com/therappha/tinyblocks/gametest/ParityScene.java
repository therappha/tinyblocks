package com.therappha.tinyblocks.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
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

    final String name;
    final List<Placement> blocks = new ArrayList<>();
    final List<Slot> slots = new ArrayList<>();
    final List<Timed> steps = new ArrayList<>();
    int ticks = 60;
    /** How long it runs before the comparison starts, built and, when a grid is moved, moved halfway through this. */
    int settleTicks = 12;
    /** Compared tick by tick only around what was built; the whole box still at the end. */
    boolean watchOnlyAround = false;

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

    ParityScene runFor(int ticks) {
        this.ticks = ticks;
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
