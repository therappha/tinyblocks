package com.therappha.tinyblocks.subgrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/** What a grid's six faces give in redstone, cell by cell and per face; one per grid. */
final class GridSignals {

    private final SubgridBlockEntity grid;

    GridSignals(SubgridBlockEntity grid) {
        this.grid = grid;
    }

    /**
     * Each face gives the block on that side the strongest signal among the pieces against it, weak and strong
     * alike (SubgridBlock#getSignal), and, for a SubgridBlock of another size on that side, cell by cell
     * (overlapSignal). Per face, indexed by the cell's two coordinates across it (faceIndex).
     */
    private int[][] weakCells = new int[6][0], strongCells = new int[6][0];
    private final int[] weakFace = new int[6], strongFace = new int[6];
    private int signalKey;
    private boolean signalsBuilt = false;

    /** Index of the cell (x,y,z) on a face along {@code axis}: its two other coordinates, in x, y, z order. */
    private int faceIndex(Direction.Axis axis, int x, int y, int z) {
        return switch (axis) {
            case X -> y * grid.gridSize + z;
            case Y -> x * grid.gridSize + z;
            case Z -> x * grid.gridSize + y;
        };
    }

    static boolean onFace(Direction face, int x, int y, int z, int size) {
        int coordinate = face.getAxis().choose(x, y, z);
        return coordinate == (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? size - 1 : 0);
    }

    private void refreshSignals() {
        int key = grid.pieceVersion();
        if (signalsBuilt && key == signalKey) return;
        signalsBuilt = true;
        signalKey = key;
        weakCells = new int[6][grid.gridSize * grid.gridSize];
        strongCells = new int[6][grid.gridSize * grid.gridSize];
        Arrays.fill(weakFace, 0);
        Arrays.fill(strongFace, 0);
        if (!(grid.getLevel() instanceof ServerLevel server)) return;
        net.minecraft.world.level.Level space = null;
        for (PlacedPiece p : grid.getPieces()) {
            if (!(p.runtimeState instanceof BlockState state) || !state.isSignalSource()) continue;
            for (int x = p.anchor.getX(); x < p.anchor.getX() + p.footprint.getX(); x++)
                for (int y = p.anchor.getY(); y < p.anchor.getY() + p.footprint.getY(); y++)
                    for (int z = p.anchor.getZ(); z < p.anchor.getZ() + p.footprint.getZ(); z++)
                        for (Direction face : Direction.values()) {
                            if (!onFace(face, x, y, z, grid.gridSize)) continue;
                            if (space == null) space = com.therappha.tinyblocks.v2.VanillaBlockPiece.buildFakeSpace(grid, server);
                            // Asked as the block across that face would: the direction points from it into the grid.
                            BlockPos at = grid.virtualPosOf(new BlockPos(x, y, z));
                            Direction towards = face.getOpposite();
                            int weak = state.getSignal(space, at, towards), strong = state.getDirectSignal(space, at, towards);
                            int f = face.ordinal(), i = faceIndex(face.getAxis(), x, y, z);
                            weakCells[f][i] = Math.max(weakCells[f][i], weak);
                            strongCells[f][i] = Math.max(strongCells[f][i], strong);
                            weakFace[f] = Math.max(weakFace[f], weak);
                            strongFace[f] = Math.max(strongFace[f], strong);
                        }
        }
    }

    /** The signal this grid gives the block across {@code face}: the strongest of the pieces against it. */
    int faceSignal(Direction face, boolean strong) {
        refreshSignals();
        return (strong ? strongFace : weakFace)[face.ordinal()];
    }

    /**
     * The signal this grid gives, across {@code face}, the cell (u, v) of a SubgridBlock of {@code otherSize} there:
     * the strongest of the pieces whose squares on the shared face overlap that cell's.
     */
    int overlapSignal(Direction face, int otherSize, int u, int v, boolean strong) {
        refreshSignals();
        int[] cells = (strong ? strongCells : weakCells)[face.ordinal()];
        int best = 0;
        for (int i = u * grid.gridSize / otherSize; i < grid.gridSize && i * otherSize < (u + 1) * grid.gridSize; i++) {
            if ((i + 1) * otherSize <= u * grid.gridSize) continue;
            for (int j = v * grid.gridSize / otherSize; j < grid.gridSize && j * otherSize < (v + 1) * grid.gridSize; j++) {
                if ((j + 1) * otherSize <= v * grid.gridSize) continue;
                best = Math.max(best, cells[i * grid.gridSize + j]);
            }
        }
        return best;
    }

    /** The two coordinates across {@code axis} of the cell (x,y,z), as faceIndex orders them. */
    static int[] acrossFace(Direction.Axis axis, int x, int y, int z) {
        return switch (axis) {
            case X -> new int[] {y, z};
            case Y -> new int[] {x, z};
            case Z -> new int[] {x, y};
        };
    }

    /** Once a tick: when what the faces give changed, the blocks around are told, as a lever tells them. */
    void updateRedstoneOutputs(ServerLevel level) {
        if (signalsBuilt && grid.pieceVersion() == signalKey) return;
        int[][] weakBefore = weakCells, strongBefore = strongCells;
        int[] strongFaceBefore = strongFace.clone();
        refreshSignals();
        if (Arrays.deepEquals(weakBefore, weakCells) && Arrays.deepEquals(strongBefore, strongCells)) return;
        Block block = grid.getBlockState().getBlock();
        level.updateNeighborsAt(grid.getBlockPos(), block);
        for (Direction face : Direction.values()) {
            if (strongFaceBefore[face.ordinal()] != strongFace[face.ordinal()]) {
                // What that block now powers in turn; not back to this grid, whose pieces have nothing new to hear.
                level.updateNeighborsAtExceptFromFacing(grid.getBlockPos().relative(face), block, face.getOpposite());
            }
        }
    }
}
