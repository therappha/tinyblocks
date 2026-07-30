package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;

/**
 * onPlace/onRemove are the two hooks real vanilla placement always fires around a block state
 * change (inside LevelChunk.setBlockState) — e.g. FallingBlock.onPlace is what schedules its very
 * first fall check. Both are declared protected on BlockBehaviour with no public entry point
 * outside that real chunk-write path, which our fake space bypasses entirely. Reflects them open
 * instead — same idiom as FakeServerLevel's reflective constructor bypass elsewhere in v2.
 */
public final class BlockAccess {

    private static final Method ON_PLACE = find("onPlace");
    private static final Method ON_REMOVE = find("onRemove");

    private static Method find(String name) {
        try {
            Method m = BlockBehaviour.class.getDeclaredMethod(name,
                    BlockState.class, Level.class, BlockPos.class, BlockState.class, boolean.class);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        invoke(ON_PLACE, state, level, pos, oldState, movedByPiston);
    }

    public static void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        invoke(ON_REMOVE, state, level, pos, newState, movedByPiston);
    }

    private static void invoke(Method m, BlockState state, Level level, BlockPos pos, BlockState other, boolean movedByPiston) {
        try {
            m.invoke(state.getBlock(), state, level, pos, other, movedByPiston);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private BlockAccess() {}
}
