package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.ticks.TickPriority;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * v2 prototype checkpoint 2: a real Level subclass that redirects block-position access to a
 * fake in-memory cell space, delegating everything else (registries, day time, sound, entities)
 * to the real wrapped Level. Lets us call neighborChanged()/useWithoutItem() — which require a
 * genuine Level, not just BlockGetter — against fake positions with zero reimplementation.
 */
public class FakeLevel extends Level implements FakeSpace {

    private final Level real;
    private final FakeCellGetter cells;
    private final List<ScheduledEntry> scheduled = new ArrayList<>();
    /**
     * The real-world position of the SubgridBlockEntity this fake space represents. A whole
     * subgrid lives inside one real block, so it shares that one block's light/biome/weather —
     * environment queries below substitute this for whatever fake position they're asked about,
     * rather than reading meaningless small-integer local grid coordinates as if they were real
     * world coordinates.
     */
    private final BlockPos realPos;

    public FakeLevel(Level real, FakeCellGetter cells, BlockPos realPos) {
        super(
            new DelegatingLevelData(real),
            real.dimension(),
            real.registryAccess(),
            real.dimensionTypeRegistration(),
            real.getProfilerSupplier(),
            real.isClientSide(),
            false,
            0L,
            1000000
        );
        this.real = real;
        this.cells = cells;
        this.realPos = realPos;
    }

    @Override
    public FakeCellGetter cells() { return cells; }

    @Override
    public List<ScheduledEntry> scheduledTicks() { return scheduled; }

    /**
     * Captured instead of touching a real (blackholed) tick list — see getBlockTicks() below.
     * Both overloads need overriding: the 3-arg one (what most vanilla blocks actually call,
     * e.g. RedstoneLampBlock's delayed turn-off) does NOT delegate to the 4-arg one — its
     * LevelAccessor default calls getBlockTicks().schedule(...) directly, bypassing it entirely.
     */
    @Override
    public void scheduleTick(BlockPos pos, Block block, int delay, TickPriority priority) {
        scheduled.add(new ScheduledEntry(pos, delay));
    }

    @Override
    public void scheduleTick(BlockPos pos, Block block, int delay) {
        scheduled.add(new ScheduledEntry(pos, delay));
    }

    // --- Redirected to the fake cell space ---

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return cells.getBlockState(pos);
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        cells.set(pos, state);
        return true;
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return cells.getBlockEntity(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return cells.getFluidState(pos);
    }

    // --- Delegated to the real level ---

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {}

    @Override
    public void playSeededSound(@Nullable Player player, double x, double y, double z,
                                 Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {}

    @Override
    public void playSeededSound(@Nullable Player player, Entity entity, Holder<SoundEvent> sound,
                                 SoundSource source, float volume, float pitch, long seed) {}

    @Override
    public String gatherChunkSourceStats() { return "FakeLevel"; }

    @Nullable
    @Override
    public Entity getEntity(int id) { return real.getEntity(id); }

    @Override
    public TickRateManager tickRateManager() { return real.tickRateManager(); }

    @Nullable
    @Override
    public MapItemSavedData getMapData(MapId id) { return null; }

    @Override
    public void setMapData(MapId id, MapItemSavedData data) {}

    @Override
    public MapId getFreeMapId() { return real.getFreeMapId(); }

    @Override
    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {}

    @Override
    public Scoreboard getScoreboard() { return real.getScoreboard(); }

    @Override
    public RecipeManager getRecipeManager() { return real.getRecipeManager(); }

    @Override
    protected LevelEntityGetter<Entity> getEntities() { return EMPTY_ENTITIES; }

    @Override
    public PotionBrewing potionBrewing() { return real.potionBrewing(); }

    @Override
    public void setDayTimeFraction(float fraction) {}

    @Override
    public float getDayTimeFraction() { return 0f; }

    @Override
    public float getDayTimePerTick() { return 0f; }

    @Override
    public void setDayTimePerTick(float dayTimePerTick) {}

    @Override
    public void gameEvent(Holder<net.minecraft.world.level.gameevent.GameEvent> event,
                           net.minecraft.world.phys.Vec3 pos,
                           net.minecraft.world.level.gameevent.GameEvent.Context context) {}

    @Override
    public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data) {}

    @Override
    public net.minecraft.world.level.chunk.ChunkSource getChunkSource() { return real.getChunkSource(); }

    @Override
    public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.block.Block> getBlockTicks() {
        return net.minecraft.world.ticks.BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.material.Fluid> getFluidTicks() {
        return net.minecraft.world.ticks.BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public java.util.List<? extends Player> players() { return java.util.List.of(); }

    @Override
    public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { return real.enabledFeatures(); }

    @Override
    public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(int x, int y, int z) {
        // Ignore the fake x,y,z — the whole subgrid shares the one real biome of its real position.
        return real.getUncachedNoiseBiome(realPos.getX(), realPos.getY(), realPos.getZ());
    }

    @Override
    public net.minecraft.world.level.biome.BiomeManager getBiomeManager() { return real.getBiomeManager(); }

    @Override
    public float getShade(net.minecraft.core.Direction direction, boolean shade) { return real.getShade(direction, shade); }

    @Override
    public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() { return real.getLightEngine(); }

    @Override
    public int getBlockTint(BlockPos pos, net.minecraft.world.level.ColorResolver resolver) {
        return real.getBlockTint(realPos, resolver);
    }

    // --- Light/sky queries: substitute the real position for whatever fake position is asked
    // about. Same reasoning as getUncachedNoiseBiome above — a subgrid shares its one real
    // block's light, it doesn't have per-cell sky access of its own. Without this, the default
    // BlockAndTintGetter/LevelReader implementations of these would query the real light engine
    // at small-integer fake coordinates like (0,0,0), which mean nothing in the real world. ---

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return real.getBrightness(layer, realPos);
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        return real.getRawBrightness(realPos, amount);
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return real.canSeeSky(realPos);
    }

    /** No entities live in the fake position space (yet). */
    private static final LevelEntityGetter<Entity> EMPTY_ENTITIES = new LevelEntityGetter<>() {
        @Override
        public Entity get(int id) { return null; }

        @Override
        public Entity get(java.util.UUID id) { return null; }

        @Override
        public Iterable<Entity> getAll() { return java.util.List.of(); }

        @Override
        public <U extends Entity> void get(net.minecraft.world.level.entity.EntityTypeTest<Entity, U> test,
                                            net.minecraft.util.AbortableIterationConsumer<U> consumer) {}

        @Override
        public void get(net.minecraft.world.phys.AABB bounds, java.util.function.Consumer<Entity> consumer) {}

        @Override
        public <U extends Entity> void get(net.minecraft.world.level.entity.EntityTypeTest<Entity, U> test,
                                            net.minecraft.world.phys.AABB bounds,
                                            net.minecraft.util.AbortableIterationConsumer<U> consumer) {}
    };
}
