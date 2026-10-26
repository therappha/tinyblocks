package com.therappha.tinyblocks.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildModeTest {

    private static final BlockPos A = new BlockPos(10, 0, 20);
    private static final BlockPos B = new BlockPos(13, 3, 23); // a 4x4x4 box, corners given either way round

    @Test
    void fillTakesEveryCell() {
        assertEquals(64, BuildMode.FILL.cells(B, A, Direction.Axis.Y, 10_000).size());
    }

    @Test
    void hollowLeavesTheInsideOut() {
        assertEquals(64 - 8, BuildMode.HOLLOW.cells(A, B, Direction.Axis.Y, 10_000).size());
        assertTrue(!BuildMode.HOLLOW.cells(A, B, Direction.Axis.Y, 10_000).contains(new BlockPos(11, 1, 21)));
    }

    @Test
    void wallsLeaveFloorAndCeilingOpen() {
        // 4 layers of a 4x4 ring of 12 cells.
        assertEquals(4 * 12, BuildMode.WALLS.cells(A, B, Direction.Axis.Y, 10_000).size());
    }

    /** First corner clicked on a floor: a horizontal panel at that corner's layer, whatever height the second is. */
    @Test
    void planeLiesOnTheFirstCornersFace() {
        var cells = BuildMode.PLANE.cells(A, B, Direction.Axis.Y, 10_000);
        assertEquals(16, cells.size());
        assertTrue(cells.stream().allMatch(c -> c.getY() == A.getY()));
        var wall = BuildMode.PLANE.cells(A, B, Direction.Axis.X, 10_000);
        assertTrue(wall.stream().allMatch(c -> c.getX() == A.getX()));
    }

    @Test
    void stopsJustPastTheLimit() {
        assertEquals(11, BuildMode.FILL.cells(A, B, Direction.Axis.Y, 10).size());
    }
}
