package com.therappha.tinyblocks.v2;

import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * One fake server space per grid, reused by every ticking piece of it, tick after tick (issue #64): building a
 * FakeServerLevel runs Level's constructor, which was most of what a ticking piece cost once the cells stopped
 * being copied. Before each use its cell map and captured ticks are cleared and it is pointed at the piece being
 * ticked, so a use sees only its own writes, as a fresh space did. A phantom keeps the space as its level between
 * ticks, as it kept its last fresh one; a use while the space is already in use (a tick reaching a tick) gets a
 * fresh one.
 */
final class TickSpace {
    final SubgridFakeCellGetter cells;
    final FakeServerLevel level;
    final ServerLevel real;
    boolean busy;

    private TickSpace(SubgridBlockEntity be, ServerLevel real, BlockPos operatedCell) {
        this.real = real;
        this.cells = new SubgridFakeCellGetter(be, operatedCell);
        this.level = FakeServerLevel.create(real, cells, be.getBlockPos());
        cells.attachLevel(level);
    }

    static TickSpace acquire(SubgridBlockEntity be, ServerLevel real, BlockPos operatedCell) {
        TickSpace space;
        if (be.tickSpace instanceof TickSpace cached && cached.real == real && !cached.busy) {
            space = cached;
            space.cells.reuseFor(operatedCell);
        } else {
            space = new TickSpace(be, real, operatedCell);
            if (!(be.tickSpace instanceof TickSpace cached && cached.busy)) be.tickSpace = space;
        }
        space.busy = true;
        space.cells.live = true;
        space.level.forgetCapabilities();
        return space;
    }

    /** Empties the space of this use's writes and captured ticks and frees it for the next one. */
    void release() {
        cells.reuseFor(null);
        level.clearScheduledTicks();
        cells.live = false;
        busy = false;
    }
}
