package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

            // dragonParts backs ServerLevel.getPartEntities(), which Level.getEntities(AABB,...)
            // calls internally (e.g. Block.pushEntitiesUp, hit when FarmBlock dries out and
            // reverts to dirt via a random tick) — an empty map is correct here: no dragon parts
            // live in the fake cell space.
            Field dragonPartsField = ServerLevel.class.getDeclaredField("dragonParts");
            dragonPartsField.setAccessible(true);
            dragonPartsField.set(instance, new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>());

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

    // ServerLevel doesn't re-override setBlockEntity/removeBlockEntity from Level, but this class
    // isn't a real Level either (reflection-allocated, see the class doc) — without these,
    // Level's own versions would resolve a real chunk via this class's own getChunkSource()
    // override below and register/remove there instead of landing in the fake cell space. Same
    // capture FakeLevel needs and has, restated here since FakeServerLevel is a sibling, not a
    // subclass, of FakeLevel.
    @Override
    public void setBlockEntity(BlockEntity blockEntity) { delegate.setBlockEntity(blockEntity); }

    @Override
    public void removeBlockEntity(BlockPos pos) { delegate.removeBlockEntity(pos); }

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

    @Override
    public boolean addFreshEntity(Entity entity) {
        // Real ServerLevel.addFreshEntity hands the entity to this.entityManager (a
        // PersistentEntitySectionManager), a ServerLevel-only field the create() field-transplant
        // never touches (it only copies Level-declared fields) — guaranteed null here, so the real
        // implementation would NPE the instant anything spawns an entity from inside a Tier 3 tick
        // call. FallingBlock.tick (sand, gravel, concrete powder, anvil, scaffolding — all share
        // this one base class, so this is generic, not block-specific) is the vanilla call path
        // that reaches this today. No GENUINE entity lives in the fake cell space (same documented
        // simplification as getEntities() above) — but FallingBlockEntity.fall() already cleared
        // the origin cell to air via its own setBlock() call right before this runs, so simulate
        // the actual point of that entity (the block relocating one cell down) directly instead of
        // just dropping it: write the state into the cell below. VanillaBlockPiece.applyChanges'
        // "a touched cell that wasn't an existing piece" scan (added once fluid spreading needed
        // the exact same capability) turns that write into a real placed piece, and fires its
        // onPlace so FallingBlock schedules its next fall check — the fall then continues cell by
        // cell over successive real scheduled ticks, never needing a genuine tracked entity.
        if (entity instanceof net.minecraft.world.entity.item.FallingBlockEntity falling) {
            BlockPos below = BlockPos.containing(entity.getX(), entity.getY() - 1, entity.getZ());
            cells().set(below, falling.getBlockState());
            return true;
        }
        return false;
    }

    // --- The rest of this section exists because ServerLevel re-overrides essentially every
    // method FakeLevel already handles, with its OWN field-dependent implementations —
    // inheriting from ServerLevel means those FakeLevel fixes are invisible here; each one
    // needs restating so it actually delegates to `delegate` instead of touching a null
    // ServerLevel-only field. Discovered by the same crash-then-fix cycle as dragonParts/server
    // above, generalized here instead of continuing one field at a time. ---

    @Nullable
    @Override
    public Entity getEntity(java.util.UUID uuid) { return null; }

    @Override
    public net.minecraft.world.TickRateManager tickRateManager() { return delegate.tickRateManager(); }

    @Nullable
    @Override
    public net.minecraft.world.level.saveddata.maps.MapItemSavedData getMapData(net.minecraft.world.level.saveddata.maps.MapId id) { return null; }

    @Override
    public void setMapData(net.minecraft.world.level.saveddata.maps.MapId id, net.minecraft.world.level.saveddata.maps.MapItemSavedData data) {}

    @Override
    public net.minecraft.world.level.saveddata.maps.MapId getFreeMapId() { return delegate.getFreeMapId(); }

    @Override
    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {}

    // Delegates to FakeLevel's own fix (real position substitution) instead of no-op'ing — see
    // that class for why this needs to actually play something now.
    @Override
    public void playSeededSound(@Nullable net.minecraft.world.entity.player.Player player, double x, double y, double z,
                                 net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound,
                                 net.minecraft.sounds.SoundSource source, float volume, float pitch, long seed) {
        delegate.playSeededSound(player, x, y, z, sound, source, volume, pitch, seed);
    }

    @Override
    public void playSeededSound(@Nullable net.minecraft.world.entity.player.Player player, Entity entity,
                                 net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound,
                                 net.minecraft.sounds.SoundSource source, float volume, float pitch, long seed) {
        delegate.playSeededSound(player, entity, sound, source, volume, pitch, seed);
    }

    // Found live (run/logs/latest.log): BlockEntity.setRemoved() -> IBlockEntityExtension
    // .invalidateCapabilities -> ServerLevel.invalidateCapabilities reads this.capListenerHolder,
    // a ServerLevel-only field create()'s transplant never touches (same category as blockEvent's
    // this.blockEvents above) — NPE'd every single tick a phantom BlockEntity ticked here called
    // setRemoved() (e.g. PistonMovingBlockEntity.tick finalizing), silently caught by
    // SubgridBlockEntity.serverTick's per-piece try/catch — which meant the exception aborted
    // tick() BEFORE its applyChanges() call ever ran, so the real finalize (removeBlockEntity +
    // setBlock to the real end state) never actually happened. Retried and failed identically
    // every following tick forever, leaving the piece permanently stuck as an invisible
    // MOVING_PISTON piece — the exact "extends fine, then disappears once it should settle"
    // symptom, predating the animation work in this same class entirely.
    @Override
    public void invalidateCapabilities(BlockPos pos) {}

    @Override
    public void invalidateCapabilities(net.minecraft.world.level.ChunkPos pos) {}

    @Override
    public void globalLevelEvent(int type, BlockPos pos, int data) {}

    @Override
    public void levelEvent(@Nullable net.minecraft.world.entity.player.Player player, int type, BlockPos pos, int data) {}

    // Found live (run/logs/latest.log): ServerLevel.blockEvent reads this.blockEvents (a
    // ServerLevel-only queue for batched block events — chest lid open/close signals, note block
    // notes, etc.), which is null here (not a Level field, so create()'s transplant never touches
    // it) — NPE'd every time ChestBlockEntity.stopOpen ran on menu close, via
    // ContainerOpenersCounter -> signalOpenCount -> blockEvent. Same no-op pattern as
    // sendBlockUpdated/levelEvent above: these are all real-world side effects (sounds, particles,
    // block-model-triggered animations) a fake position space has no meaningful way to perform.
    @Override
    public void blockEvent(BlockPos pos, net.minecraft.world.level.block.Block block, int eventId, int eventParam) {}

    @Override
    public net.minecraft.server.ServerScoreboard getScoreboard() {
        return (net.minecraft.server.ServerScoreboard) delegate.getScoreboard();
    }

    @Override
    public net.minecraft.world.item.crafting.RecipeManager getRecipeManager() { return delegate.getRecipeManager(); }

    @Override
    public net.minecraft.world.item.alchemy.PotionBrewing potionBrewing() { return delegate.potionBrewing(); }

    @Override
    public void setDayTimeFraction(float fraction) {}

    @Override
    public float getDayTimeFraction() { return 0f; }

    @Override
    public float getDayTimePerTick() { return 0f; }

    @Override
    public void setDayTimePerTick(float dayTimePerTick) {}

    @Override
    public void gameEvent(net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> event,
                           net.minecraft.world.phys.Vec3 pos,
                           net.minecraft.world.level.gameevent.GameEvent.Context context) {}

    @Override
    public net.minecraft.server.level.ServerChunkCache getChunkSource() {
        return (net.minecraft.server.level.ServerChunkCache) delegate.getChunkSource();
    }

    @Override
    public net.minecraft.world.ticks.LevelTicks<Block> getBlockTicks() {
        return new net.minecraft.world.ticks.LevelTicks<>(l -> false, () -> net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
    }

    @Override
    public net.minecraft.world.ticks.LevelTicks<net.minecraft.world.level.material.Fluid> getFluidTicks() {
        return new net.minecraft.world.ticks.LevelTicks<>(l -> false, () -> net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
    }

    @Override
    public List<net.minecraft.server.level.ServerPlayer> players() { return List.of(); }

    @Override
    public float getShade(Direction direction, boolean shade) { return delegate.getShade(direction, shade); }

    @Override
    public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { return delegate.enabledFeatures(); }

    @Override
    public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return delegate.getUncachedNoiseBiome(x, y, z);
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

    // ServerLevel does NOT re-override these two (verified via javap) — they'd otherwise fall
    // through to LevelAccessor's default, which calls getFluidTicks().schedule(...), and
    // getFluidTicks() below is a real BlackholeTickAccess. Same fix as FakeLevel's fluid-tick
    // overloads, needed here too since FakeServerLevel is a sibling, not a subclass, of FakeLevel.
    @Override
    public void scheduleTick(BlockPos pos, net.minecraft.world.level.material.Fluid fluid, int delay, TickPriority priority) {
        scheduled.add(new ScheduledEntry(pos, delay));
    }

    @Override
    public void scheduleTick(BlockPos pos, net.minecraft.world.level.material.Fluid fluid, int delay) {
        scheduled.add(new ScheduledEntry(pos, delay));
    }
}
