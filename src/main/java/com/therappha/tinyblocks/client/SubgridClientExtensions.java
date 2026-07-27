package com.therappha.tinyblocks.client;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

public class SubgridClientExtensions implements IClientBlockExtensions {

    public static final SubgridClientExtensions INSTANCE = new SubgridClientExtensions();

    /**
     * Suppress vanilla hit particles — ParticleEngine.crack() already skips
     * RenderShape.INVISIBLE blocks, but this makes the intent explicit.
     */
    @Override
    public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
        return true;
    }

    /**
     * Suppress vanilla destroy burst — by default it spawns particles across the
     * entire cached VoxelShape (union of all pieces). We suppress it entirely;
     * the per-piece crack overlay in SubgridRenderer already gives visual feedback.
     */
    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
        return true;
    }
}
