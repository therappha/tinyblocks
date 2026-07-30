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
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.ticks.TickPriority;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
public class FakeServerLevel extends ServerLevel implements FakeSpace {

    private FakeLevel delegate;

    // Not initialized inline — field initializers are compiled into the constructor, which never
    // runs for a reflection-allocated instance. Set explicitly in create() instead.
    private List<ScheduledEntry> scheduled;

    @Override
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
            FakeLevel delegateLevel = new FakeLevel(real, cells, realPos);
            instance.delegate = delegateLevel;
            instance.scheduled = new ArrayList<>();

            // Reflection allocation skips the *entire* constructor chain, not just ServerLevel's
            // part — none of Level's own fields (levelData, worldBorder, threadSafeRandom, ...)
            // are set either. Rather than discovering and hand-patching each one via a fresh NPE,
            // transplant every Level-declared field from delegateLevel, whose real constructor
            // DID run (FakeLevel extends Level normally) and so has all of them properly set.
            for (Field field : Level.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                field.set(instance, field.get(delegateLevel));
            }

            // neighborUpdater specifically gets rebound to `instance` itself (not delegateLevel)
            // so every redstone/shape-update cascade it drives operates through the object
            // actually passed around as the ServerLevel, not a sibling reference to it.
            Field neighborUpdaterField = Level.class.getDeclaredField("neighborUpdater");
            neighborUpdaterField.setAccessible(true);
            neighborUpdaterField.set(instance, new CollectingNeighborUpdater(instance, 1000000));

            // ServerLevel-declared fields (server, entityManager, ...) aren't covered by the
            // Level-field transplant above — there's no sibling ServerLevel to borrow them from.
            // `server` is the one real MinecraftServer, safe to reference directly.
            // entityManager is handled separately via the getEntities() override below, since no
            // fake entity manager exists (or is needed — no entities live in fake cell space).
            Field serverField = ServerLevel.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(instance, ((ServerLevel) real).getServer());

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

    @Override
    public net.minecraft.world.level.entity.LevelEntityGetter<Entity> getEntities() {
        // ServerLevel's own getEntities() reads this.entityManager, which is ServerLevel-declared
        // (not covered by the Level-field transplant in create()) and null here — no fake entity
        // manager exists or is needed, since no entities live in the fake cell space.
        return delegate.getEntities();
    }

    // --- Captured instead of touching a real tick list — both overloads, see FakeLevel ---

    @Override
    public void scheduleTick(BlockPos pos, Block block, int delay, TickPriority priority) {
        scheduled.add(new ScheduledEntry(pos, delay));
    }

    @Override
    public void scheduleTick(BlockPos pos, Block block, int delay) {
        scheduled.add(new ScheduledEntry(pos, delay));
    }
}
