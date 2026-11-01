package com.therappha.tinyblocks.subgrid;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Scheduled ticks and block events of every grid of a level, run as the level runs its own: all that are due, in trigger
 * order, then priority, then the order they were asked for, whichever grid they belong to, and before any block entity
 * ticks. Each grid ran its own queue from its block entity tick before: a circuit crossing grids ran in the order the
 * grids happen to tick, and a hopper saw a lock a tick late. Grid order must not change what a circuit does.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID)
public final class GridTickScheduler {

    private GridTickScheduler() {}

    private static final class Queues {
        final Set<SubgridBlockEntity> withTicks = new LinkedHashSet<>();
        final Set<BlockEvent> events = new LinkedHashSet<>();
    }

    /** {@code pos} is the block's vanilla-visible position, in {@code grid}'s virtual frame. */
    public record BlockEvent(SubgridBlockEntity grid, BlockPos pos, Block block, int id, int param) {}

    private record Due(SubgridBlockEntity grid, SubgridBlockEntity.ScheduledPieceTick tick) {}

    private static final Map<Level, Queues> LEVELS = new WeakHashMap<>();

    private static Queues of(Level level) {
        return LEVELS.computeIfAbsent(level, l -> new Queues());
    }

    private static long nextOrder = 0;
    @javax.annotation.Nullable private static Level running;

    /** The game time for a tick asked for now. Queues run ahead of the level's own tick, in the tick about to start. */
    public static long gameTime(Level level) {
        return level == running ? level.getGameTime() + 1 : level.getGameTime();
    }

    static long nextOrder() {
        return nextOrder++;
    }

    static void wake(SubgridBlockEntity grid) {
        if (grid.getLevel() instanceof ServerLevel level) of(level).withTicks.add(grid);
    }

    public static void blockEvent(ServerLevel level, BlockEvent event) {
        of(level).events.add(event);
    }

    /** Before the level ticks, which is when its game time moves on to the tick being run. */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.tickRateManager().runsNormally()) return;
        Queues queues = LEVELS.get(level);
        if (queues == null) return;
        long now = level.getGameTime() + 1;
        running = level;
        try {
            run(level, queues, now);
        } finally {
            running = null;
        }
    }

    private static void run(ServerLevel level, Queues queues, long now) {
        List<Due> due = new ArrayList<>();
        for (var it = queues.withTicks.iterator(); it.hasNext(); ) {
            SubgridBlockEntity grid = it.next();
            if (grid.isRemoved() || !grid.hasScheduledTicks()) {
                it.remove();
                continue;
            }
            grid.takeDue(now, tick -> due.add(new Due(grid, tick)));
        }
        due.sort(Comparator.<Due>comparingLong(d -> d.tick().targetGameTime())
                .thenComparingInt(d -> d.tick().priority())
                .thenComparingLong(d -> d.tick().order()));
        for (Due d : due) d.grid().runDue(level, d.tick());
        // Run until none are left: an event can ask for another, as ServerLevel#runBlockEvents allows.
        while (!queues.events.isEmpty()) {
            List<BlockEvent> events = new ArrayList<>(queues.events);
            queues.events.clear();
            for (BlockEvent e : events) {
                if (!e.grid().isRemoved()) e.grid().runBlockEvent(level, e);
            }
        }
    }
}
