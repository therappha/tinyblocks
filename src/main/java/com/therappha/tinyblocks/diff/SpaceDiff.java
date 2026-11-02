package com.therappha.tinyblocks.diff;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares two spaces cell by cell and says, in words, what differs: "state" (the block and its properties, redstone
 * level included), "working" (what a comparator puts out or reads, what a container holds) and "visual" (what a
 * client was last told to draw). {@link Timeline} follows the differences tick by tick.
 */
public final class SpaceDiff {

    private SpaceDiff() {}

    public static final String STATE = "state.", WORKING = "working.", VISUAL = "visual.";

    /** {@code left} and {@code right} are what each space has for {@code field} at {@code cell}; "" when it has none. */
    public record Difference(BlockPos cell, String field, String left, String right) {}

    /** Every fact about {@code cell}; nothing for an empty cell. */
    public static Map<String, String> facts(SpaceView space, BlockPos cell) {
        Map<String, String> facts = new LinkedHashMap<>();
        BlockState state = space.state(cell);
        BlockState drawn = space.drawn(cell);
        if (!drawn.isAir()) facts.put(VISUAL + "drawn", drawn.toString());
        if (state.isAir()) return facts;
        facts.put(STATE + "block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
        for (Property<?> property : state.getProperties()) facts.put(STATE + property.getName(), value(state, property));
        if (state.hasAnalogOutputSignal()) facts.put(WORKING + "analog_signal", Integer.toString(space.analogSignal(cell)));
        BlockEntity be = space.blockEntity(cell);
        if (be instanceof ComparatorBlockEntity comparator) {
            facts.put(WORKING + "comparator_output", Integer.toString(comparator.getOutputSignal()));
        }
        if (be instanceof Container container) facts.put(WORKING + "items", items(container));
        if (be != null) com.therappha.tinyblocks.compat.create.CreateFacts.add(be, WORKING, facts);
        return facts;
    }

    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static String items(Container container) {
        StringBuilder text = new StringBuilder();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!text.isEmpty()) text.append(", ");
            text.append(slot).append(':').append(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath())
                    .append('x').append(stack.getCount());
        }
        return text.isEmpty() ? "empty" : text.toString();
    }

    public static List<Difference> compare(SpaceView left, SpaceView right, Iterable<BlockPos> cells) {
        List<Difference> differences = new ArrayList<>();
        for (BlockPos cell : cells) compareCell(cell, facts(left, cell), facts(right, cell), differences);
        return differences;
    }

    /** Every fact of {@code cells}, to compare later or on another thread (a client against its server). */
    public static Map<BlockPos, Map<String, String>> snapshot(SpaceView space, Iterable<BlockPos> cells) {
        Map<BlockPos, Map<String, String>> facts = new LinkedHashMap<>();
        for (BlockPos cell : cells) facts.put(cell, facts(space, cell));
        return facts;
    }

    /** Only the fields starting with {@code prefix}: a client has no inventories to compare, for one. */
    public static List<Difference> compare(Map<BlockPos, Map<String, String>> left, Map<BlockPos, Map<String, String>> right, String prefix) {
        List<Difference> differences = new ArrayList<>();
        for (BlockPos cell : left.keySet()) compareCell(cell, left.get(cell), right.getOrDefault(cell, Map.of()), differences);
        differences.removeIf(d -> !d.field().startsWith(prefix));
        return differences;
    }

    private static void compareCell(BlockPos cell, Map<String, String> a, Map<String, String> b, List<Difference> into) {
        if (a.equals(b)) return;
        Set<String> fields = new TreeSet<>(a.keySet());
        fields.addAll(b.keySet());
        for (String field : fields) {
            String x = a.getOrDefault(field, ""), y = b.getOrDefault(field, "");
            if (!x.equals(y)) into.add(new Difference(cell, field, x, y));
        }
    }

    /** Every cell of the box from (0,0,0) up to, and not including, {@code size}. */
    public static List<BlockPos> box(int width, int height, int depth) {
        List<BlockPos> cells = new ArrayList<>(width * height * depth);
        for (int y = 0; y < height; y++)
            for (int z = 0; z < depth; z++)
                for (int x = 0; x < width; x++) cells.add(new BlockPos(x, y, z));
        return cells;
    }

    public static String describe(Difference d, String leftName, String rightName) {
        return "%s %s: %s %s, %s %s".formatted(cellName(d.cell()), d.field(), leftName, shown(d.left()), rightName, shown(d.right()));
    }

    private static String cellName(BlockPos cell) {
        return "(%d,%d,%d)".formatted(cell.getX(), cell.getY(), cell.getZ());
    }

    private static String shown(String value) {
        return value.isEmpty() ? "nothing" : value;
    }

    /** One difference that held from tick {@code from} to tick {@code to}, with its values on the first of them. */
    public record Span(Difference first, int from, int to) {
        public int length() {
            return to - from + 1;
        }
    }

    /** The differences of consecutive ticks, joined into spans. */
    public static final class Timeline {
        private final Map<String, List<Span>> spans = new LinkedHashMap<>();
        private int lastTick = -1;

        public void record(int tick, List<Difference> differences) {
            lastTick = tick;
            for (Difference d : differences) {
                List<Span> list = spans.computeIfAbsent(d.cell().toShortString() + " " + d.field(), k -> new ArrayList<>());
                Span last = list.isEmpty() ? null : list.get(list.size() - 1);
                if (last != null && last.to() == tick - 1) list.set(list.size() - 1, new Span(last.first(), last.from(), tick));
                else list.add(new Span(d, tick, tick));
            }
        }

        /** All spans, the ones that started first first. */
        public List<Span> spans() {
            List<Span> all = new ArrayList<>();
            spans.values().forEach(all::addAll);
            all.sort(java.util.Comparator.comparingInt(Span::from));
            return all;
        }

        public boolean endsDifferent(Span span) {
            return span.to() == lastTick;
        }

        public String describe(Span span, String leftName, String rightName) {
            String when = endsDifferent(span) ? "from tick %d to the end".formatted(span.from())
                    : span.length() == 1 ? "at tick %d".formatted(span.from())
                    : "from tick %d to %d".formatted(span.from(), span.to());
            return SpaceDiff.describe(span.first(), leftName, rightName) + ", " + when;
        }
    }
}
