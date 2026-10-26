package com.therappha.tinyblocks.compat.create;

import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.BlockAccess;
import com.therappha.tinyblocks.v2.FakeLevel;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Placing a ShaftConnectorPiece into a grid. */
public final class ConnectorPlacement {

    private ConnectorPlacement() {}

    /** The face of the grid along {@code axis} nearest to local {@code cell}. */
    public static Direction nearestFace(Direction.Axis axis, BlockPos cell, int gridSize) {
        int coordinate = axis.choose(cell.getX(), cell.getY(), cell.getZ());
        return Direction.fromAxisAndDirection(axis, coordinate * 2 < gridSize
                ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
    }

    /** Places a plate on the face nearest {@code target}; false if any of its cells is taken. */
    public static boolean place(SubgridBlockEntity be, ServerLevel level, Direction.Axis axis, BlockPos target) {
        return place(be, level, nearestFace(axis, target, be.getGridSize()));
    }

    /**
     * A click on {@code clicked} (null for an empty cell): along the clicked piece's own shaft axis when it has
     * one, otherwise along {@code clickedAxis}, on the face nearest the clicked cell.
     */
    public static boolean placeFrom(SubgridBlockEntity be, ServerLevel level, @javax.annotation.Nullable PlacedPiece clicked,
                                    Direction.Axis clickedAxis, BlockPos cell) {
        Direction.Axis axis = clickedAxis;
        if (clicked != null && clicked.runtimeState instanceof net.minecraft.world.level.block.state.BlockState state
                && state.getBlock() instanceof com.simibubi.create.content.kinetics.base.IRotate rotate) {
            axis = rotate.getRotationAxis(state);
        }
        return place(be, level, nearestFace(axis, cell, be.getGridSize()));
    }

    /**
     * A block clicked onto a plate goes into the plate's socket, turned to meet it: along the plate's axis, or,
     * for a block with a facing (a motor), facing the plate. False if the socket is taken.
     */
    public static boolean placeInSocket(SubgridBlockEntity be, ServerLevel level, PlacedPiece plate, Block block) {
        BlockPos socket = ShaftConnectorPiece.socket(plate.facing, be.getGridSize());
        if (socket == null || be.getPieceAt(socket.getX(), socket.getY(), socket.getZ()) != null) return false;
        net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
        if (state.hasProperty(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS)) {
            state = state.setValue(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS, plate.facing.getAxis());
        } else if (state.hasProperty(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING)) {
            state = state.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, plate.facing);
        }
        PlacedPiece piece = new PlacedPiece(VanillaBlockPiece.INSTANCE, socket, plate.facing);
        piece.runtimeState = state;
        if (!be.placePiece(piece)) return false;
        FakeLevel fakeLevel = VanillaBlockPiece.buildFakeSpace(be, level);
        BlockAccess.onPlace(state, fakeLevel, fakeLevel.world(socket), Blocks.AIR.defaultBlockState(), false);
        VanillaBlockPiece.scheduleCaptured(be, fakeLevel, level.getGameTime());
        BlockPos subgridPos = be.getBlockPos();
        level.sendBlockUpdated(subgridPos, level.getBlockState(subgridPos), level.getBlockState(subgridPos), Block.UPDATE_CLIENTS);
        return true;
    }

    /** Places a plate on {@code face} of {@code be}; false if any of its cells is taken. */
    public static boolean place(SubgridBlockEntity be, ServerLevel level, Direction face) {
        PlacedPiece piece = ShaftConnectorPiece.create(be, face);
        if (piece == null || !be.placePiece(piece)) return false;
        // Same as a one-cell placement: tell the block it was placed, so Create attaches it to the network.
        FakeLevel fakeLevel = VanillaBlockPiece.buildFakeSpace(be, level);
        BlockAccess.onPlace(ShaftConnectorPiece.stateFor(face, be.getGridSize()), fakeLevel, fakeLevel.world(piece.anchor),
                Blocks.AIR.defaultBlockState(), false);
        VanillaBlockPiece.scheduleCaptured(be, fakeLevel, level.getGameTime());
        BlockPos subgridPos = be.getBlockPos();
        level.sendBlockUpdated(subgridPos, level.getBlockState(subgridPos), level.getBlockState(subgridPos), Block.UPDATE_CLIENTS);
        return true;
    }
}
