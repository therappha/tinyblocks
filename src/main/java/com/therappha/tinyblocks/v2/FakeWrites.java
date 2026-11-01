package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Level#setBlock, on a fake space: the block's onRemove and onPlace, as LevelChunk#setBlockState runs them, then the
 * neighbour and shape updates Level#markAndNotifyBlock runs for the flags given. Whatever follows from a write happens
 * in the same call, in vanilla's order: a torch that goes out tells the dust on the block above it, and a change two
 * blocks away is heard, without anything about redstone being written here.
 */
final class FakeWrites {

    private FakeWrites() {}

    static boolean setBlock(Level level, FakeCellGetter cells, BlockPos pos, BlockState state, int flags, int recursionLeft) {
        boolean[] written = new boolean[1];
        if (cells.afterItsCall(live -> written[0] = live.setBlock(pos, state, flags, recursionLeft))) return written[0];
        BlockState old = cells.getBlockState(pos);
        if (old == state) return false;
        cells.set(pos, state);
        cells.markSettled(pos);
        boolean moved = (flags & Block.UPDATE_MOVE_BY_PISTON) != 0;
        BlockAccess.onRemove(old, level, pos, state, moved);
        if (cells.getBlockState(pos) != state) return true; // onRemove replaced it, as a chunk checks
        BlockAccess.onPlace(state, level, pos, old, moved);
        // Level#markAndNotifyBlock only goes on if the block is still what was placed: a hopper locking itself in its
        // onPlace has changed it, and vanilla's neighbours hear nothing.
        if (cells.getBlockState(pos) != state) return true;
        if ((flags & Block.UPDATE_NEIGHBORS) != 0) {
            level.blockUpdated(pos, old.getBlock());
            if (state.hasAnalogOutputSignal()) level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
        if ((flags & Block.UPDATE_KNOWN_SHAPE) == 0 && recursionLeft > 0) {
            int shapeFlags = flags & ~(Block.UPDATE_NEIGHBORS | Block.UPDATE_SUPPRESS_DROPS);
            old.updateIndirectNeighbourShapes(level, pos, shapeFlags, recursionLeft - 1);
            state.updateNeighbourShapes(level, pos, shapeFlags, recursionLeft - 1);
            state.updateIndirectNeighbourShapes(level, pos, shapeFlags, recursionLeft - 1);
        }
        return true;
    }
}
