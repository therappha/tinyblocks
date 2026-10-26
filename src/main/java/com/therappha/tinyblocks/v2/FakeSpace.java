package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Common surface shared by FakeLevel and FakeServerLevel: the fake cell space itself, plus any
 * scheduleTick requests captured instead of touching a real (blackholed) tick list. Lets
 * VanillaBlockPiece's shared apply/merge logic work identically for both — interact/
 * neighborChanged use the simpler, proven FakeLevel; only scheduledTick/randomTick (which need a
 * type-genuine ServerLevel) use the more fragile FakeServerLevel.
 */
public interface FakeSpace {

    /** A scheduleTick request captured during a call, relative to when it was made. */
    record ScheduledEntry(BlockPos pos, int delay) {}

    FakeCellGetter cells();

    /** The real level the SubgridBlock this space was built for lives in. */
    net.minecraft.world.level.Level realLevel();

    List<ScheduledEntry> scheduledTicks();

    /** Position to hand to vanilla code for local cell {@code local}. */
    default net.minecraft.core.BlockPos world(net.minecraft.core.BlockPos local) { return cells().toWorld(local); }
}
