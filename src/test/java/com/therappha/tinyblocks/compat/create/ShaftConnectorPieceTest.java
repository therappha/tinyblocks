package com.therappha.tinyblocks.compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShaftConnectorPieceTest {

    @Test
    void plateIsAShaftWideRoundedUpToStayCentred() {
        assertEquals(2, ShaftConnectorPiece.width(2));
        assertEquals(2, ShaftConnectorPiece.width(4));
        assertEquals(4, ShaftConnectorPiece.width(8));
        assertEquals(6, ShaftConnectorPiece.width(16));
    }

    @Test
    void plateSitsOnTheEdgeCentredAcrossTheFace() {
        assertEquals(new BlockPos(6, 2, 2), ShaftConnectorPiece.anchorOn(Direction.EAST, 8));
        assertEquals(new BlockPos(5, 0, 5), ShaftConnectorPiece.anchorOn(Direction.DOWN, 16));
        assertEquals(new BlockPos(1, 1, 0), ShaftConnectorPiece.anchorOn(Direction.NORTH, 4));
    }

    @Test
    void funnelIsHalfAsDeepAsItIsWide() {
        assertEquals(1, ShaftConnectorPiece.depth(2));
        assertEquals(1, ShaftConnectorPiece.depth(4));
        assertEquals(2, ShaftConnectorPiece.depth(8));
        assertEquals(3, ShaftConnectorPiece.depth(16));
    }

    @Test
    void socketIsRightBehindTheFunnelOnTheCentreLine() {
        assertEquals(new BlockPos(5, 4, 4), ShaftConnectorPiece.socket(Direction.EAST, 8));
        assertEquals(new BlockPos(8, 3, 8), ShaftConnectorPiece.socket(Direction.DOWN, 16));
        assertEquals(new BlockPos(1, 1, 0), ShaftConnectorPiece.socket(Direction.SOUTH, 2));
    }

    @Test
    void socketIsAlwaysBehindACellOfTheFunnel() {
        for (int size : new int[]{2, 4, 8, 16}) {
            for (Direction face : Direction.values()) {
                BlockPos front = ShaftConnectorPiece.socket(face, size).relative(face);
                BlockPos anchor = ShaftConnectorPiece.anchorOn(face, size);
                int width = ShaftConnectorPiece.width(size);
                for (Direction.Axis axis : Direction.Axis.values()) {
                    int c = axis.choose(front.getX(), front.getY(), front.getZ());
                    int a = axis.choose(anchor.getX(), anchor.getY(), anchor.getZ());
                    int extent = axis == face.getAxis() ? ShaftConnectorPiece.depth(size) : width;
                    assertEquals(true, c >= a && c < a + extent, "grid " + size + " face " + face + " axis " + axis);
                }
            }
        }
    }

    /** In a 9-grid the socket is the middle cell, in line with a full-size shaft's centre, and the funnel around it. */
    @Test
    void anOddGridCentresTheSocketOnTheBlock() {
        assertEquals(5, ShaftConnectorPiece.width(9));
        assertEquals(2, ShaftConnectorPiece.depth(9));
        assertEquals(new BlockPos(6, 4, 4), ShaftConnectorPiece.socket(Direction.EAST, 9));
        assertEquals(new BlockPos(7, 2, 2), ShaftConnectorPiece.anchorOn(Direction.EAST, 9));
    }

    /** Every grid size has a connector, and its funnel fits on the face with its socket inside the grid. */
    @Test
    void everyGridSizeHasAConnectorThatFits() {
        for (int size = 2; size <= 16; size++) {
            assertEquals(true, ShaftConnectorPiece.forGrid(size) != null, "no connector for 1/" + size);
            int width = ShaftConnectorPiece.width(size);
            assertEquals(true, width <= size && (size - width) % 2 == 0, "1/" + size + ": width " + width + " is not centred");
            BlockPos socket = ShaftConnectorPiece.socket(Direction.EAST, size);
            assertEquals(true, socket.getX() >= 0 && socket.getX() < size, "1/" + size + ": socket " + socket + " outside");
        }
    }

    /** Across to a grid of the same size: the cell right across. To another size: the one the socket line meets. */
    @Test
    void thePartnerCellIsWhereTheSocketLineMeetsTheNeighbour() {
        assertEquals(3, ShaftConnectorPiece.partnerIndex(7, 7));
        assertEquals(4, ShaftConnectorPiece.partnerIndex(8, 8));
        assertEquals(2, ShaftConnectorPiece.partnerIndex(9, 5)); // both odd: the middle of each
        assertEquals(4, ShaftConnectorPiece.partnerIndex(5, 9));
        assertEquals(2, ShaftConnectorPiece.partnerIndex(8, 5)); // 4.5/8 of the face falls in 5-grid cell 2
        assertEquals(new BlockPos(0, 2, 2), ShaftConnectorPiece.partnerCell(Direction.EAST, 9, 5));
        assertEquals(new BlockPos(4, 8, 4), ShaftConnectorPiece.partnerCell(Direction.DOWN, 5, 9));
    }
}
