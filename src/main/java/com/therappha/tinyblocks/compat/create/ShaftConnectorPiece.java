package com.therappha.tinyblocks.compat.create;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * A ShaftConnectorBlock inside a subgrid: a funnel on one face of the grid. Its wide end, on the face, is as wide
 * as a full-size Create shaft (6/16 of a block, rounded up so it stays centred on the face) and meets the real
 * shaft outside; it narrows over a few cells of depth down to its socket, the one cell inside it connects to. The
 * piece is the box around the funnel. Its facing is the face of the grid it sits on, pointing out of the grid.
 */
public final class ShaftConnectorPiece extends VanillaBlockPiece {

    /** Width of a Create shaft, in sixteenths of a block (AllShapes.SIX_VOXEL_POLE). */
    private static final int SHAFT_WIDTH_SIXTEENTHS = 6;

    /** One per grid size a SubgridBlock can have. */
    private static final Map<Integer, ShaftConnectorPiece> BY_GRID_SIZE = new java.util.HashMap<>();

    static {
        for (int size = com.therappha.tinyblocks.setup.Registration.MIN_GRID_SIZE;
             size <= com.therappha.tinyblocks.setup.Registration.MAX_GRID_SIZE; size++) {
            BY_GRID_SIZE.put(size, new ShaftConnectorPiece(size));
        }
    }

    private ShaftConnectorPiece(int gridSize) {
        super(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "shaft_connector_" + gridSize),
                new Vec3i(width(gridSize), width(gridSize), depth(gridSize)));
    }

    /** Same reason as VanillaBlockPiece#init: saved pieces must find their definition before first use. */
    public static void init() {}

    @Nullable
    public static ShaftConnectorPiece forGrid(int gridSize) {
        return BY_GRID_SIZE.get(gridSize);
    }

    /**
     * Cells across the face: the shaft's width in cells, rounded up to the next count that leaves an even
     * margin on both sides so the plate is centred (2 -> 2, 4 -> 2, 8 -> 4, 16 -> 6).
     */
    public static int width(int gridSize) {
        int cells = Math.max(1, (SHAFT_WIDTH_SIXTEENTHS * gridSize + 15) / 16);
        if ((gridSize - cells) % 2 != 0) cells++;
        return Math.min(cells, gridSize);
    }

    /** Cells from the face to the socket: half the funnel's width, at least one (2 -> 1, 4 -> 1, 8 -> 2, 16 -> 3). */
    public static int depth(int gridSize) {
        return Math.max(1, width(gridSize) / 2);
    }

    /** Local cell of the funnel's anchor (its lowest corner) on {@code face} of a grid of {@code gridSize}. */
    public static BlockPos anchorOn(Direction face, int gridSize) {
        int edge = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? gridSize - depth(gridSize) : 0;
        int margin = (gridSize - width(gridSize)) / 2;
        return switch (face.getAxis()) {
            case X -> new BlockPos(edge, margin, margin);
            case Y -> new BlockPos(margin, edge, margin);
            case Z -> new BlockPos(margin, margin, edge);
        };
    }

    /** The block state a connector on {@code face} holds: a shaft along that face's axis. */
    public static BlockState stateFor(Direction face) {
        return CreateConnectorSetup.SHAFT_CONNECTOR.get().defaultBlockState()
                .setValue(RotatedPillarKineticBlock.AXIS, face.getAxis());
    }

    /** A connector piece on {@code face} of {@code grid}, not yet placed. */
    @Nullable
    public static PlacedPiece create(SubgridBlockEntity grid, Direction face) {
        ShaftConnectorPiece definition = forGrid(grid.getGridSize());
        if (definition == null) return null;
        PlacedPiece piece = new PlacedPiece(definition, anchorOn(face, grid.getGridSize()), face);
        piece.runtimeState = stateFor(face);
        return piece;
    }

    @Override
    public void onRemoved(PlacedPiece piece, net.minecraft.server.level.ServerLevel level, SubgridBlockEntity be) {
        // By now the piece is out of its grid, so the bridge can no longer find the plate's shape by itself.
        KineticBridge.whileRemoving(be, piece, () -> super.onRemoved(piece, level, be));
    }

    /** The face of the grid {@code piece} connects through, or null if it is not a connector. */
    @Nullable
    public static Direction faceOf(PlacedPiece piece) {
        return piece.definition instanceof ShaftConnectorPiece ? piece.facing : null;
    }

    /**
     * The one cell inside the grid a funnel on {@code face} connects to: right behind its narrow end, on the
     * centre line of the face (cell gridSize/2 across — half a cell off the real shaft's axis, which the funnel
     * bends over to meet).
     */
    public static BlockPos socket(Direction face, int gridSize) {
        int behind = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? gridSize - 1 - depth(gridSize) : depth(gridSize);
        int centre = gridSize / 2;
        return switch (face.getAxis()) {
            case X -> new BlockPos(behind, centre, centre);
            case Y -> new BlockPos(centre, behind, centre);
            case Z -> new BlockPos(centre, centre, behind);
        };
    }

    /**
     * Across the face, the cell of a neighbouring grid of {@code otherSize} that meets this one's socket line: the
     * one whose square on the shared face holds the socket line's centre. The same index for the same size; for
     * odd sizes both are the face's centre.
     */
    public static int partnerIndex(int gridSize, int otherSize) {
        double centre = (gridSize / 2 + 0.5) / gridSize;
        return Math.min(otherSize - 1, (int) Math.floor(centre * otherSize));
    }

    /** That cell, in the neighbouring grid of {@code otherSize} across {@code face} (on its side of the face). */
    public static BlockPos partnerCell(Direction face, int gridSize, int otherSize) {
        int across = partnerIndex(gridSize, otherSize);
        int along = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : otherSize - 1;
        return switch (face.getAxis()) {
            case X -> new BlockPos(along, across, across);
            case Y -> new BlockPos(across, along, across);
            case Z -> new BlockPos(across, across, along);
        };
    }

    /** The grid size of the SubgridBlock across {@code face} of {@code grid}, or 0 when it is anything else. */
    public static int sizeAcross(SubgridBlockEntity grid, Direction face) {
        net.minecraft.world.level.Level level = grid.getLevel();
        return level != null && level.getBlockEntity(grid.getBlockPos().relative(face)) instanceof SubgridBlockEntity other
                ? other.getGridSize() : 0;
    }

    /** True if local {@code cell} is the plate's socket. */
    public static boolean isSocket(PlacedPiece plate, BlockPos cell, int gridSize) {
        return socket(plate.facing, gridSize).equals(cell);
    }
}
