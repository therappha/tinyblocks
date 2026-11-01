package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.ticks.LevelTicks;

/** What a block asks about ticks already scheduled, answered from its grid's queue: it stands in for the level's. */
final class FakeTicks<T> extends LevelTicks<T> {

    private final FakeCellGetter cells;

    FakeTicks(FakeCellGetter cells) {
        super(chunk -> false, () -> InactiveProfiler.INSTANCE);
        this.cells = cells;
    }

    @Override
    public boolean hasScheduledTick(BlockPos pos, T type) {
        return cells.tickPending(pos, type);
    }

    @Override
    public boolean willTickThisTick(BlockPos pos, T type) {
        return cells.tickDue(pos, type);
    }
}
