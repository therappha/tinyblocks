package com.therappha.tinyblocks.client;

import com.therappha.tinyblocks.compat.create.ShaftConnectorPiece;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PieceTransformsTest {

    private static List<PieceTransforms.Slice> funnel(int gridSize, int sizeAcross) {
        PlacedPiece plate = new PlacedPiece(ShaftConnectorPiece.forGrid(gridSize),
                ShaftConnectorPiece.anchorOn(Direction.EAST, gridSize), Direction.EAST);
        return PieceTransforms.slices(plate, gridSize, sizeAcross);
    }

    /** Next to a real block the funnel starts as a full-size shaft. */
    @Test
    void againstARealBlockItStartsFullSize() {
        assertEquals(1f, funnel(7, 0).get(0).scale().y, 1e-6);
    }

    /** Next to a grid of the same size it is a plain tiny shaft all along: no taper. */
    @Test
    void againstTheSameSizeItIsAStraightTinyShaft() {
        for (PieceTransforms.Slice slice : funnel(7, 7)) {
            assertEquals(1f / 7, slice.scale().y, 1e-6);
            assertEquals(3f / 7, slice.translation().y, 1e-6); // the middle cell, both sides
        }
    }

    /**
     * An 8-grid's gear connector: the shaftless gear a cell's scale (two cells across), then a full-size shaft end
     * from the face to the gear's middle, half a cell.
     */
    @Test
    void aGearIsTheGearThenAFullSizeShaftEndOnTheFace() {
        List<PieceTransforms.Slice> slices = funnel(8, 0);
        assertEquals(2, slices.size());
        assertEquals(1f / 8, slices.get(0).scale().y, 1e-6);
        assertEquals(1f, slices.get(1).scale().y, 1e-6);
        assertEquals(1f / 16, slices.get(1).scale().x, 1e-6);
        assertEquals(1f - 1f / 16, slices.get(1).translation().x, 1e-6);
    }
}
