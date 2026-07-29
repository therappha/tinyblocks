package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
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

import javax.annotation.Nullable;

/**
 * v2 prototype checkpoint 2: a real Level subclass that redirects block-position access to a
 * fake in-memory cell space, delegating everything else (registries, day time, sound, entities)
 * to the real wrapped Level. Lets us call neighborChanged()/useWithoutItem() — which require a
 * genuine Level, not just BlockGetter — against fake positions with zero reimplementation.
 */
public class FakeLevel extends Level {

    private final Level real;
    private final FakeCellGetter cells;

    public FakeLevel(Level real, FakeCellGetter cells) {
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
        return real.getUncachedNoiseBiome(x, y, z);
    }

    @Override
    public net.minecraft.world.level.biome.BiomeManager getBiomeManager() { return real.getBiomeManager(); }

    @Override
    public float getShade(net.minecraft.core.Direction direction, boolean shade) { return real.getShade(direction, shade); }

    @Override
    public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() { return real.getLightEngine(); }

    @Override
    public int getBlockTint(BlockPos pos, net.minecraft.world.level.ColorResolver resolver) { return 0xFFFFFF; }

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
