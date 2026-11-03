package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.diff.RealSpaceView;
import com.therappha.tinyblocks.diff.SpaceView;
import com.therappha.tinyblocks.diff.TinySpaceView;
import com.therappha.tinyblocks.items.GridSaveItem;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeLevel;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A space a scene can be built in and acted on, in each case through the entry points the game itself uses. */
interface ParitySpace extends SpaceView {

    void build(BlockPos cell, BlockState state, boolean shaped);

    void use(BlockPos cell);

    void breakCell(BlockPos cell);

    void clear(BlockPos cell);

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

    /** How many of each entity are in and around the cells up to {@code size}. */
    Map<String, Integer> entities(int[] size);

    /** Dropped items count by what they hold, however they have stacked up. A player here is only a test's. */
    private static Map<String, Integer> entitiesIn(ServerLevel level, AABB box) {
        Map<String, Integer> found = new TreeMap<>();
        for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            if (entity instanceof ItemEntity item) {
                found.merge("item " + BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).getPath(), item.getItem().getCount(), Integer::sum);
            } else {
                found.merge(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath(), 1, Integer::sum);
            }
        }
        return found;
    }

    /** What can happen to a tiny copy once it is running. */
    enum Move {
        STAYS, RELOAD, GRID_SAVE, SABLE_MOVE, SABLE_ASSEMBLE
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
        public void clear(BlockPos cell) {
            server.setBlockAndUpdate(real(cell), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }

        @Override
        public void place(BlockPos cell, BlockState state) {
            build(cell, state, true);
            server.gameEvent(GameEvent.BLOCK_PLACE, real(cell), GameEvent.Context.of(state));
        }

        @Override
        public Map<String, Integer> entities(int[] size) {
            return entitiesIn(server, new AABB(Vec3.atLowerCornerOf(real(BlockPos.ZERO)), Vec3.atLowerCornerOf(real(new BlockPos(size[0], size[1], size[2])))).inflate(1));
        }
    }

    final class Tiny extends TinySpaceView implements ParitySpace {
        private final Player player;

        Tiny(ServerLevel level, BlockPos origin, int gridSize, BlockPos seam, Player player) {
            super(level, origin, gridSize, seam);
            this.player = player;
        }

        @javax.annotation.Nullable private Object subLevel;

        /** The grids of this space, by real position, lowest corner first. */
        private List<BlockPos> grids() {
            List<BlockPos> grids = new ArrayList<>();
            for (BlockPos at : BlockPos.betweenClosed(origin, origin.offset(4, 2, 4))) {
                if (level.getBlockEntity(at) instanceof SubgridBlockEntity) grids.add(at.immutable());
            }
            return grids;
        }

        /** The whole copy goes to {@code by} from here, the way the player's action takes it. */
        void move(Move how, BlockPos by) {
            List<BlockPos> grids = grids();
            BlockPos low = grids.stream().reduce(BlockPos::min).orElseThrow(), high = grids.stream().reduce(BlockPos::max).orElseThrow();
            BlockPos shift = low.subtract(origin);
            switch (how) {
                case STAYS -> {
                    return;
                }
                case RELOAD -> {
                    // As a chunk unloading and loading back does: saved first, all of them, then each comes back from its tag.
                    Map<BlockPos, CompoundTag> saved = new LinkedHashMap<>();
                    for (BlockPos at : grids) saved.put(at, level.getBlockEntity(at).saveWithFullMetadata(level.registryAccess()));
                    for (BlockPos at : grids) {
                        level.getBlockEntity(at).onChunkUnloaded();
                        level.removeBlockEntity(at);
                    }
                    saved.forEach((at, tag) -> level.setBlockEntity(net.minecraft.world.level.block.entity.BlockEntity.loadStatic(at, level.getBlockState(at), tag, level.registryAccess())));
                }
                case GRID_SAVE -> {
                    ItemStack save = new ItemStack(Registration.GRID_SAVE.get());
                    if (!(grids.size() == 1 ? GridSaveItem.pickUp(level, low, save, player) : GridSaveItem.pickUpBox(level, low, high, save, player))) {
                        throw new IllegalStateException("the Grid Save took nothing at " + low);
                    }
                    BlockPos to = low.offset(by);
                    if (!GridSaveItem.place(level, to, Direction.UP, save, player)) throw new IllegalStateException("nothing placed at " + to);
                    origin = to.subtract(shift);
                }
                case SABLE_MOVE -> {
                    BlockPos to = low.offset(by);
                    SubLevelAssemblyHelper.moveBlocks(level, new SubLevelAssemblyHelper.AssemblyTransform(low, to, 0, Rotation.NONE, level), grids);
                    origin = to.subtract(shift);
                }
                case SABLE_ASSEMBLE -> {
                    subLevel = SableSupport.assemble(level, low, grids, low, high);
                    origin = SableSupport.plotCenter(subLevel).subtract(shift);
                }
            }
            grids().forEach(at -> {
                if (level.getBlockEntity(at) instanceof SubgridBlockEntity be) watch(be);
            });
        }

        void cleanUp() {
            if (subLevel != null) SableSupport.remove(level, subLevel);
        }

        @Override
        public Map<String, Integer> entities(int[] size) {
            Vec3 low = Vec3.atLowerCornerOf(gridPos(BlockPos.ZERO));
            // Wider than the grids: what comes out of a piece is full size, and so are its steps. Still far short of the next space.
            return entitiesIn(level, new AABB(low, Vec3.atLowerCornerOf(gridPos(new BlockPos(size[0], size[1], size[2]))).add(1, 1, 1)).inflate(4));
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
        public void clear(BlockPos cell) {
            breakCell(cell);
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
