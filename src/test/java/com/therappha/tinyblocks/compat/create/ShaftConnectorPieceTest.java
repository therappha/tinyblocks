package com.therappha.tinyblocks.compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShaftConnectorPieceTest {

    // --- the funnel: odd grids ---

    @Test
    void plateIsAShaftWideRoundedUpToStayCentred() {
        assertEquals(3, ShaftConnectorPiece.width(3));
        assertEquals(3, ShaftConnectorPiece.width(7));
        assertEquals(5, ShaftConnectorPiece.width(9));
        assertEquals(7, ShaftConnectorPiece.width(15));
    }

    @Test
    void plateSitsOnTheEdgeCentredAcrossTheFace() {
        assertEquals(new BlockPos(6, 2, 2), ShaftConnectorPiece.anchorOn(Direction.EAST, 7));
        assertEquals(new BlockPos(4, 0, 4), ShaftConnectorPiece.anchorOn(Direction.DOWN, 15));
        assertEquals(new BlockPos(1, 1, 0), ShaftConnectorPiece.anchorOn(Direction.NORTH, 5));
    }

    @Test
    void funnelIsHalfAsDeepAsItIsWide() {
        assertEquals(1, ShaftConnectorPiece.depth(3));
        assertEquals(1, ShaftConnectorPiece.depth(7));
        assertEquals(2, ShaftConnectorPiece.depth(9));
        assertEquals(3, ShaftConnectorPiece.depth(15));
    }

    @Test
    void socketIsRightBehindTheFunnelOnTheCentreLine() {
        assertEquals(new BlockPos(5, 3, 3), ShaftConnectorPiece.socket(Direction.EAST, 7));
        assertEquals(new BlockPos(7, 3, 7), ShaftConnectorPiece.socket(Direction.DOWN, 15));
        assertEquals(new BlockPos(1, 1, 1), ShaftConnectorPiece.socket(Direction.SOUTH, 3));
    }

    @Test
    void socketIsAlwaysBehindACellOfTheFunnel() {
        for (int size = 3; size <= 15; size += 2) {
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

    // --- the gear: even grids ---

    /** In an 8-grid the connector is a gear two cells across, in the layer against the face, centred on it, with no socket. */
    @Test
    void anEvenGridsConnectorIsATwoByTwoGear() {
        assertEquals(true, ShaftConnectorPiece.isGear(8));
        assertEquals(2, ShaftConnectorPiece.width(8));
        assertEquals(1, ShaftConnectorPiece.depth(8));
        assertEquals(new BlockPos(7, 3, 3), ShaftConnectorPiece.anchorOn(Direction.EAST, 8));
        assertEquals(null, ShaftConnectorPiece.socket(Direction.EAST, 8));
    }

    /** Small cogwheels mesh on its eight side cells, large ones on its four diagonal cells, all in its layer. */
    @Test
    void theGearMeshesOnItsSidesAndDiagonals() {
        ShaftConnectorPiece.Mesh mesh = ShaftConnectorPiece.meshCells(Direction.EAST, 8);
        assertEquals(8, mesh.sides().size());
        assertEquals(4, mesh.diagonals().size());
        assertEquals(true, mesh.sides().contains(new BlockPos(7, 2, 3)) && mesh.sides().contains(new BlockPos(7, 4, 5)));
        assertEquals(true, mesh.diagonals().contains(new BlockPos(7, 2, 2)) && mesh.diagonals().contains(new BlockPos(7, 5, 5)));
        assertEquals(0, ShaftConnectorPiece.meshCells(Direction.EAST, 2).sides().size()); // a 1/2 gear fills the face
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
            if (ShaftConnectorPiece.isGear(size)) continue; // no socket
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
        assertEquals(2, ShaftConnectorPiece.partnerIndex(8, 5)); // an 8-grid gear sits on the face centre: 5-grid cell 2
        assertEquals(new BlockPos(0, 2, 2), ShaftConnectorPiece.partnerCell(Direction.EAST, 9, 5));
        assertEquals(new BlockPos(4, 8, 4), ShaftConnectorPiece.partnerCell(Direction.DOWN, 5, 9));
    }
}
