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

    /** Where a block clicked onto a connector goes, and as what. */
    public record Target(BlockPos cell, net.minecraft.world.level.block.state.BlockState state) {}

    /**
     * Where {@code block} clicked onto connector {@code plate} at {@code realHit} goes: into a funnel's socket,
     * turned to meet it — along the plate's axis, or, for a block with a facing (a motor), facing the plate; or, for a
     * gear, a small cogwheel on its axis into the free side cell (ShaftConnectorPiece#meshCells) nearest the hit.
     * Null when nowhere: the socket taken, every side taken, or anything but a small cogwheel on a gear. The click
     * (SubgridEventHandler) and the ghost (SubgridPlacementPreview) both ask here, so they agree.
     */
    @javax.annotation.Nullable
    public static Target targetOn(SubgridBlockEntity be, PlacedPiece plate, Block block, net.minecraft.world.phys.Vec3 realHit) {
        int size = be.getGridSize();
        net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
        if (!ShaftConnectorPiece.isGear(size)) {
            BlockPos socket = ShaftConnectorPiece.socket(plate.facing, size);
            if (socket == null || be.getPieceAt(socket.getX(), socket.getY(), socket.getZ()) != null) return null;
            if (state.hasProperty(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS)) {
                state = state.setValue(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS, plate.facing.getAxis());
            } else if (state.hasProperty(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING)) {
                state = state.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, plate.facing);
            }
            return new Target(socket, state);
        }
        if (!com.simibubi.create.content.kinetics.simpleRelays.ICogWheel.isSmallCog(state)
                || !state.hasProperty(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS)) {
            return null;
        }
        state = state.setValue(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS, plate.facing.getAxis());
        net.minecraft.world.phys.Vec3 local = realHit.subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(be.getBlockPos())).scale(size);
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos side : ShaftConnectorPiece.meshCells(plate.facing, size)) {
            if (be.getPieceAt(side.getX(), side.getY(), side.getZ()) != null) continue;
            double distance = net.minecraft.world.phys.Vec3.atCenterOf(side).distanceToSqr(local);
            if (distance < best) {
                best = distance;
                nearest = side;
            }
        }
        return nearest == null ? null : new Target(nearest, state);
    }

    /** Places {@code target} in {@code be}, as a one-cell placement does; false if its cell is taken. */
    public static boolean placeAt(SubgridBlockEntity be, ServerLevel level, Target target, Direction facing) {
        BlockPos cell = target.cell();
        PlacedPiece piece = new PlacedPiece(VanillaBlockPiece.INSTANCE, cell, facing);
        piece.runtimeState = target.state();
        if (!be.placePiece(piece)) return false;
        FakeLevel fakeLevel = VanillaBlockPiece.buildFakeSpace(be, level);
        BlockAccess.onPlace(target.state(), fakeLevel, fakeLevel.world(cell), Blocks.AIR.defaultBlockState(), false);
        VanillaBlockPiece.scheduleCaptured(be, fakeLevel, level.getGameTime());
        BlockPos subgridPos = be.getBlockPos();
        level.sendBlockUpdated(subgridPos, level.getBlockState(subgridPos), level.getBlockState(subgridPos), Block.UPDATE_CLIENTS);
        return true;
    }

    /**
     * A wrench on connector {@code plate}: it moves to the next free face of the grid, in {@link Direction#values()}
     * order. A connector's axis is its face's, so it has no other way to turn. False, and it stays, if no face is free.
     */
    public static boolean moveToNextFace(SubgridBlockEntity be, ServerLevel level, PlacedPiece plate) {
        Direction from = plate.facing;
        be.removePieceAt(plate.anchor.getX(), plate.anchor.getY(), plate.anchor.getZ());
        Direction[] faces = Direction.values();
        for (int i = 1; i < faces.length; i++) {
            if (place(be, level, faces[(from.ordinal() + i) % faces.length])) return true;
        }
        place(be, level, from);
        return false;
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
