package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.diff.RealSpaceView;
import com.therappha.tinyblocks.diff.SpaceView;
import com.therappha.tinyblocks.diff.TinySpaceView;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeLevel;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** A space a scene can be built in and acted on, in each case through the entry points the game itself uses. */
interface ParitySpace extends SpaceView {

    void build(BlockPos cell, BlockState state, boolean shaped);

    void use(BlockPos cell);

    void breakCell(BlockPos cell);

    void place(BlockPos cell, BlockState state);

    /** What a player's open menu does: changes the container, which then says it changed. */
    default void insert(BlockPos cell, ItemStack stack) {
        if (!(blockEntity(cell) instanceof Container container)) return;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack there = container.getItem(slot);
            if (there.isEmpty()) {
                container.setItem(slot, stack.copy());
                container.setChanged();
                return;
            }
            if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() + stack.getCount() <= there.getMaxStackSize()) {
                there.grow(stack.getCount());
                container.setChanged();
                return;
            }
        }
    }

    default void take(BlockPos cell, int count) {
        if (!(blockEntity(cell) instanceof Container container)) return;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) continue;
            container.removeItem(slot, count);
            container.setChanged();
            return;
        }
    }

    final class Real extends RealSpaceView implements ParitySpace {
        private final ServerLevel server;
        private final Player player;

        Real(ServerLevel level, BlockPos origin, Player player) {
            super(level, origin);
            this.server = level;
            this.player = player;
        }

        @Override
        public void build(BlockPos cell, BlockState state, boolean shaped) {
            BlockPos at = real(cell);
            BlockState shapedState = shaped ? Block.updateFromNeighbourShapes(state, server, at) : state;
            server.setBlock(at, shapedState.isAir() ? state : shapedState, Block.UPDATE_ALL);
        }

        @Override
        public void use(BlockPos cell) {
            BlockPos at = real(cell);
            server.getBlockState(at).useWithoutItem(server, player, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
        }

        @Override
        public void breakCell(BlockPos cell) {
            server.destroyBlock(real(cell), false);
        }

        @Override
        public void place(BlockPos cell, BlockState state) {
            build(cell, state, true);
            server.gameEvent(GameEvent.BLOCK_PLACE, real(cell), GameEvent.Context.of(state));
        }
    }

    final class Tiny extends TinySpaceView implements ParitySpace {
        private final Player player;

        Tiny(ServerLevel level, BlockPos origin, int gridSize, BlockPos seam, Player player) {
            super(level, origin, gridSize, seam);
            this.player = player;
        }

        private SubgridBlockEntity gridFor(BlockPos cell) {
            SubgridBlockEntity be = SubgridBlockEntity.createAt(level, gridPos(cell), gridSize);
            if (be == null) throw new IllegalStateException("no grid at " + gridPos(cell) + ", there is " + level.getBlockState(gridPos(cell)));
            watch(be);
            return be;
        }

        @Override
        public void build(BlockPos cell, BlockState state, boolean shaped) {
            SubgridBlockEntity be = gridFor(cell);
            BlockPos at = local(cell);
            FakeLevel space = VanillaBlockPiece.buildFakeSpace(be, level);
            BlockState shapedState = shaped ? Block.updateFromNeighbourShapes(state, space, space.world(at)) : state;
            SubgridTestSupport.placePiece(be, level, at, shapedState.isAir() ? state : shapedState);
        }

        @Override
        public void use(BlockPos cell) {
            PlacedPiece piece = piece(cell);
            if (piece != null) SubgridTestSupport.useOnPiece(grid(cell), level, piece, player);
        }

        @Override
        public void breakCell(BlockPos cell) {
            SubgridBlockEntity be = grid(cell);
            BlockPos at = local(cell);
            if (be != null) be.removePieceAt(at.getX(), at.getY(), at.getZ());
        }

        @Override
        public void place(BlockPos cell, BlockState state) {
            build(cell, state, true);
            SubgridBlockEntity be = grid(cell);
            VanillaBlockPiece.buildFakeServerSpace(be, level).gameEvent(GameEvent.BLOCK_PLACE,
                    VanillaBlockPiece.buildFakeServerSpace(be, level).world(local(cell)), GameEvent.Context.of(state));
        }
    }
}
