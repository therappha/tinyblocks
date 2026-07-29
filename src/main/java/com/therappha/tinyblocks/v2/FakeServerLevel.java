package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.ticks.TickPriority;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * v2 prototype, Tier 3: a real ServerLevel subclass, needed because Block.tick/randomTick are
 * statically typed to require a genuine ServerLevel instance — no amount of Level-level
 * delegation (FakeLevel) satisfies that type requirement.
 *
 * ServerLevel's real constructor wires up an actual LevelStorageAccess (real save files on disk)
 * and a real ServerChunkCache (background chunk-loading threads) — calling it naively for a
 * per-interaction fake object would risk touching the real world save or leaking threads. Instead
 * this class is allocated via ReflectionFactory.newConstructorForSerialization, which bypasses
 * the entire constructor chain (Level's and ServerLevel's alike) — no disk I/O, no threads, every
 * field starts null/zero. The one declared constructor below exists only so this class
 * type-checks against ServerLevel's constructor; it is never actually invoked (see create()).
 *
 * Because no constructor runs, none of Level's/ServerLevel's fields are populated by the usual
 * means — everything this class needs is wired up by delegating to a FakeLevel (populated
 * normally, via its own real constructor) instead, discovered iteratively by compiling/running
 * against the specific block ticks we actually exercise (redstone lamp delay, crop growth), not
 * by exhaustively reimplementing ServerLevel.
 */
public class FakeServerLevel extends ServerLevel {

    private FakeLevel delegate;

    /** A scheduleTick request captured during this call, relative to when it was made. */
    public record ScheduledEntry(BlockPos pos, int delay) {}

    private final List<ScheduledEntry> scheduled = new ArrayList<>();

    /** scheduleTick calls captured this call — the caller merges these into its own persistent queue. */
    public List<ScheduledEntry> scheduledTicks() { return scheduled; }

    // Never actually invoked — see create(). Exists only so FakeServerLevel type-checks as a
    // ServerLevel subclass; the real constructor's heavy side effects never run.
    private FakeServerLevel(MinecraftServer server, Executor executor, LevelStorageSource.LevelStorageAccess storage,
                             ServerLevelData levelData, ResourceKey<Level> dimension, LevelStem levelStem,
                             ChunkProgressListener listener, boolean isDebug, long seed,
                             List<CustomSpawner> spawners, boolean tickTime, RandomSequences randomSequences) {
        super(server, executor, storage, levelData, dimension, levelStem, listener, isDebug, seed, spawners, tickTime, randomSequences);
    }

    public static FakeServerLevel create(Level real, FakeCellGetter cells, BlockPos realPos) {
        try {
            sun.reflect.ReflectionFactory rf = sun.reflect.ReflectionFactory.getReflectionFactory();
            Constructor<Object> objectCtor = Object.class.getDeclaredConstructor();
            @SuppressWarnings("unchecked")
            Constructor<FakeServerLevel> ctor =
                    (Constructor<FakeServerLevel>) rf.newConstructorForSerialization(FakeServerLevel.class, objectCtor);
            ctor.setAccessible(true);
            FakeServerLevel instance = ctor.newInstance();
            instance.delegate = new FakeLevel(real, cells, realPos);
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to allocate FakeServerLevel", e);
        }
    }

    public FakeCellGetter cells() { return delegate.cells(); }

    // --- Delegated to the FakeLevel wrapping the same fake cell space ---

    @Override
    public BlockState getBlockState(BlockPos pos) { return delegate.getBlockState(pos); }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        return delegate.setBlock(pos, state, flags, recursionLeft);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) { return delegate.getBlockEntity(pos); }

    @Override
    public FluidState getFluidState(BlockPos pos) { return delegate.getFluidState(pos); }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {}

    @Nullable
    @Override
    public Entity getEntity(int id) { return delegate.getEntity(id); }

    @Override
    public String gatherChunkSourceStats() { return "FakeServerLevel"; }

    // --- Captured instead of touching a real tick list ---

    @Override
    public void scheduleTick(BlockPos pos, Block block, int delay, TickPriority priority) {
        scheduled.add(new ScheduledEntry(pos, delay));
    }
}
