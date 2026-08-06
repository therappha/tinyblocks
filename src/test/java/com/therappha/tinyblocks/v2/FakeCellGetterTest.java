package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeCellGetterTest {

    @Test
    void unsetCellFallsBackToAir() {
        FakeCellGetter cells = new FakeCellGetter();
        assertTrue(cells.getBlockState(BlockPos.ZERO).isAir());
    }

    @Test
    void setThenGetRoundTrips() {
        FakeCellGetter cells = new FakeCellGetter();
        cells.set(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        assertEquals(Blocks.STONE.defaultBlockState(), cells.getBlockState(BlockPos.ZERO));
    }
}
