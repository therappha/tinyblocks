package com.therappha.tinyblocks.client;

import com.therappha.tinyblocks.compat.create.ShaftConnectorPiece;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a piece's block model is drawn inside its SubgridBlock, in block units: one or more slices, each a
 * translate then a scale of the block model. A one-cell piece is one slice, its anchor cell scaled to
 * 1/gridSize.
 *
 * A shaft connector is a funnel of slices of the shaft model, from its face inwards: the first is a full-size
 * shaft on the real shaft's axis (the block's centre line), each next one thinner and shifted towards the
 * socket's centre line, ending just short of a tiny shaft on the socket's axis. That is what lines the real shaft
 * up with the tiny one, which sits half a cell off the block's centre line.
 */
public final class PieceTransforms {

    private PieceTransforms() {}

    public record Slice(Vector3f translation, Vector3f scale) {}

    /** How much bigger than a cell's scale the gear connector's cogwheel is drawn. */
    public static final float GEAR_SCALE = 1.15f;

    /** Slices drawn per cell of a funnel's depth: enough for the taper to read as a cone, not as steps. */
    private static final int SLICES_PER_CELL = 4;

    public static List<Slice> slices(PlacedPiece piece, int gridSize) {
        return slices(piece, gridSize, 0);
    }

    /**
     * {@code sizeAcross}: the grid size of a SubgridBlock of the same size across a connector's face, 0 for anything
     * else (SubgridRenderer#acrossOf). The funnel starts from what it meets there: a full-size shaft on the block's
     * centre line — a real one, or another size's connector — or, next to the same size, that grid's tiny shaft on
     * the same line, so no taper at all.
     */
    public static List<Slice> slices(PlacedPiece piece, int gridSize, int sizeAcross) {
        float cell = 1f / gridSize;
        if (ShaftConnectorPiece.faceOf(piece) == null) {
            return List.of(new Slice(
                    new Vector3f(piece.anchor.getX() * cell, piece.anchor.getY() * cell, piece.anchor.getZ() * cell),
                    new Vector3f(cell, cell, cell)));
        }
        Direction face = piece.facing;
        Direction.Axis axis = face.getAxis();
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        if (ShaftConnectorPiece.isGear(gridSize)) {
            // 0: the large cogwheel model, a block across (its teeth reach ~15/16 out), shrunk to a cell: two cells
            // across, centred on the point between the four middle cells, in the layer against the face.
            float along = (positive ? gridSize - 0.5f : 0.5f) * cell;
            float centre = 0.5f;
            Vector3f middle = new Vector3f(axis == Direction.Axis.X ? along : centre,
                                           axis == Direction.Axis.Y ? along : centre,
                                           axis == Direction.Axis.Z ? along : centre);
            // A little bigger than a cell's scale (GEAR_SCALE): closer to meeting the small cogwheels on its sides.
            float gearScale = cell * GEAR_SCALE;
            Slice gear = new Slice(middle.sub(gearScale / 2, gearScale / 2, gearScale / 2), new Vector3f(gearScale, gearScale, gearScale));
            // 1: a full-size shaft end from the face to the gear's middle: what meets the shaft outside.
            float thickness = cell / 2;
            float at = positive ? 1 - thickness : 0;
            Slice end = new Slice(
                    new Vector3f(axis == Direction.Axis.X ? at : 0, axis == Direction.Axis.Y ? at : 0, axis == Direction.Axis.Z ? at : 0),
                    new Vector3f(axis == Direction.Axis.X ? thickness : 1, axis == Direction.Axis.Y ? thickness : 1,
                                 axis == Direction.Axis.Z ? thickness : 1));
            return List.of(gear, end);
        }
        int count = ShaftConnectorPiece.depth(gridSize) * SLICES_PER_CELL;
        float thickness = cell / SLICES_PER_CELL;
        float socketCentre = (gridSize / 2 + 0.5f) * cell;
        float startSize = sizeAcross > 0 ? 1f / sizeAcross : 1;
        float startCentre = sizeAcross > 0
                ? (ShaftConnectorPiece.partnerIndex(gridSize, sizeAcross) + 0.5f) / sizeAcross : 0.5f;

        List<Slice> slices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            float t = (float) i / count;
            float size = startSize + (cell - startSize) * t;
            float centre = startCentre + (socketCentre - startCentre) * t;
            float across = centre - size / 2;
            float along = positive ? 1 - (i + 1) * thickness : i * thickness;
            slices.add(new Slice(
                    new Vector3f(axis == Direction.Axis.X ? along : across,
                                 axis == Direction.Axis.Y ? along : across,
                                 axis == Direction.Axis.Z ? along : across),
                    new Vector3f(axis == Direction.Axis.X ? thickness : size,
                                 axis == Direction.Axis.Y ? thickness : size,
                                 axis == Direction.Axis.Z ? thickness : size)));
        }
        return slices;
    }
}
