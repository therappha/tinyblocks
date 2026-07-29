package com.therappha.tinyblocks.v2;

import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.storage.WritableLevelData;

/** Forwards everything to the real Level's LevelData; setSpawn() is a local no-op. */
public class DelegatingLevelData implements WritableLevelData {

    private final Level real;
    private BlockPos spawnPos = BlockPos.ZERO;
    private float spawnAngle = 0f;

    public DelegatingLevelData(Level real) {
        this.real = real;
    }

    @Override
    public void setSpawn(BlockPos pos, float angle) {
        this.spawnPos = pos;
        this.spawnAngle = angle;
    }

    @Override
    public BlockPos getSpawnPos() { return spawnPos; }

    @Override
    public float getSpawnAngle() { return spawnAngle; }

    @Override
    public long getGameTime() { return real.getLevelData().getGameTime(); }

    @Override
    public long getDayTime() { return real.getLevelData().getDayTime(); }

    @Override
    public boolean isThundering() { return real.getLevelData().isThundering(); }

    @Override
    public boolean isRaining() { return real.getLevelData().isRaining(); }

    @Override
    public void setRaining(boolean raining) { real.getLevelData().setRaining(raining); }

    @Override
    public boolean isHardcore() { return real.getLevelData().isHardcore(); }

    @Override
    public GameRules getGameRules() { return real.getLevelData().getGameRules(); }

    @Override
    public Difficulty getDifficulty() { return real.getLevelData().getDifficulty(); }

    @Override
    public boolean isDifficultyLocked() { return real.getLevelData().isDifficultyLocked(); }

    @Override
    public void fillCrashReportCategory(CrashReportCategory category, LevelHeightAccessor level) {
        real.getLevelData().fillCrashReportCategory(category, level);
    }
}
