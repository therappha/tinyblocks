package com.therappha.tinyblocks.diff;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Cell (0,0,0) is the real block at {@code origin}, and cells run on block by block. */
public class RealSpaceView implements SpaceView {

    protected final Level level;
    protected final BlockPos origin;

    public RealSpaceView(Level level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
    }

    public BlockPos real(BlockPos cell) {
        return origin.offset(cell);
    }

    @Override
    public BlockState state(BlockPos cell) {
        return level.getBlockState(real(cell));
    }

    @Override
    public BlockState drawn(BlockPos cell) {
        return state(cell); // vanilla's own sync is not what is being tested
    }

    @Override
    public BlockEntity blockEntity(BlockPos cell) {
        return level.getBlockEntity(real(cell));
    }

    @Override
    public int analogSignal(BlockPos cell) {
        return state(cell).getAnalogOutputSignal(level, real(cell));
    }
}
