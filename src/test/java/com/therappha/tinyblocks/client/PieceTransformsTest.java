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

    /** Next to a 1/5 grid a 1/9 funnel starts at a 1/5 shaft and narrows to a 1/9 one. */
    @Test
    void againstAnotherSizeItStartsAtThatSize() {
        List<PieceTransforms.Slice> slices = funnel(9, 5);
        assertEquals(1f / 5, slices.get(0).scale().y, 1e-6);
        float last = slices.get(slices.size() - 1).scale().y;
        assertEquals(true, last < 1f / 5 && last > 1f / 9, "ends at " + last);
    }
}
