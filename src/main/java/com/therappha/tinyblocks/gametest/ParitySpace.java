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

    /** What a player's click on a hand crank does: it turns for a few ticks. */
    default void crank(BlockPos cell) {
        if (blockEntity(cell) instanceof com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity crank) crank.turn(false);
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

    /**
     * Where an entity that appears belongs to this space: in its cells, or in the blocks of its grids. Entities are
     * counted by where they came from, not where they are: a full-size item out of a tiny piece can be pushed out of
     * its block, and water carries items along, into the box of the space next door.
     */
    AABB spawnBox(int[] size);

    default boolean bornIn(int[] size, Vec3 at) {
        return spawnBox(size).contains(at);
    }

    /** The entities that appeared in this space while it was watched (ParityEntities). */
    List<Entity> born();

    /** How many of each entity that came out of this space are still there; dropped items count by what they hold. */
    default Map<String, Integer> entities() {
        Map<String, Integer> found = new TreeMap<>();
        for (Entity entity : born()) {
            if (entity.isRemoved()) continue;
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

    class Real extends RealSpaceView implements ParitySpace {
        protected final ServerLevel server;
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

        private final List<Entity> born = new ArrayList<>();

        @Override
        public List<Entity> born() {
            return born;
        }

        @Override
        public AABB spawnBox(int[] size) {
            return new AABB(Vec3.atLowerCornerOf(real(BlockPos.ZERO)), Vec3.atLowerCornerOf(real(new BlockPos(size[0], size[1], size[2])))).inflate(0.5);
        }

        /**
         * The block entities of the box go and come back from what they saved, as when their chunk unloads and loads:
         * what a grid reloaded is compared with (Move.RELOAD), since Create's own blocks don't come back unchanged either.
         */
        void reload(int[] size) {
            Map<BlockPos, CompoundTag> saved = new LinkedHashMap<>();
            for (BlockPos cell : com.therappha.tinyblocks.diff.SpaceDiff.box(size[0], size[1], size[2])) {
                BlockPos at = real(cell);
                var be = server.getBlockEntity(at);
                if (be != null) saved.put(at, be.saveWithFullMetadata(server.registryAccess()));
            }
            for (BlockPos at : saved.keySet()) {
                server.getBlockEntity(at).onChunkUnloaded();
                server.removeBlockEntity(at);
            }
            saved.forEach((at, tag) -> server.setBlockEntity(net.minecraft.world.level.block.entity.BlockEntity.loadStatic(at, server.getBlockState(at), tag, server.registryAccess())));
        }
    }

    final class Tiny extends TinySpaceView implements ParitySpace {
        private final Player player;

        Tiny(ServerLevel level, BlockPos origin, int gridSize, BlockPos seam, Player player) {
            super(level, origin, gridSize, seam);
            this.player = player;
        }

        @javax.annotation.Nullable private Object subLevel;
        /** Where the copy stood before it was assembled: the sub-level is still there in the world, and what drops from it falls there. */
        @javax.annotation.Nullable private BlockPos assembledFrom;

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
                    assembledFrom = origin;
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

        private final List<Entity> born = new ArrayList<>();

        @Override
        public List<Entity> born() {
            return born;
        }

        @Override
        public AABB spawnBox(int[] size) {
            return new AABB(Vec3.atLowerCornerOf(gridPos(BlockPos.ZERO)), Vec3.atLowerCornerOf(gridPos(new BlockPos(size[0], size[1], size[2]))).add(1, 1, 1)).inflate(0.5);
        }

        @Override
        public boolean bornIn(int[] size, Vec3 at) {
            AABB box = spawnBox(size);
            if (box.contains(at) || assembledFrom == null) return box.contains(at);
            BlockPos shift = assembledFrom.subtract(origin);
            return box.move(shift.getX(), shift.getY(), shift.getZ()).contains(at);
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

    /**
     * Real blocks, but for the cells a scene stands a grid in for (ParityScene.StandIn): there a grid of that size, with
     * connectors on the two faces along the block's axis and tiny parts joining them, turns what the block would have.
     * Such a cell shows the block it stands in for, turning as the grid's connector on its downstream face turns:
     * what leaves the grid, which is what the rest of the network sees of it. Breaking it breaks a tiny part halfway
     * along, placing it puts that part back.
     */
    final class Bridged extends Real {
        private final Map<BlockPos, ParityScene.StandIn> standIns = new java.util.HashMap<>();
        private final Map<BlockPos, BlockState> stoodFor = new java.util.HashMap<>();

        Bridged(ServerLevel level, BlockPos origin, Player player, List<ParityScene.StandIn> standIns) {
            super(level, origin, player);
            for (ParityScene.StandIn standIn : standIns) this.standIns.put(standIn.cell(), standIn);
        }

        record Part(BlockPos cell, BlockState state) {}

        private static BlockState along(net.minecraft.world.level.block.state.BlockState block, Direction.Axis axis) {
            return block.setValue(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS, axis);
        }

        /**
         * The tiny parts from one connector to the other: shafts on the line between a funnel's sockets, or between
         * small cogwheels on the two gears' sides; with a jog, the line steps aside through two pairs of meshing
         * cogwheels, which turn it back the right way.
         */
        static List<Part> path(ParityScene.StandIn standIn) {
            int n = standIn.gridSize();
            Direction.Axis axis = standIn.axis();
            Direction low = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
            BlockPos line;
            int from, to;
            boolean gear = com.therappha.tinyblocks.compat.create.ShaftConnectorPiece.isGear(n);
            if (gear) {
                line = com.therappha.tinyblocks.compat.create.ShaftConnectorPiece.meshCells(low, n).get(0);
                from = 0;
                to = n - 1;
            } else {
                line = com.therappha.tinyblocks.compat.create.ShaftConnectorPiece.socket(low, n);
                from = axis.choose(line.getX(), line.getY(), line.getZ());
                to = n - 1 - from;
            }
            BlockState shaft = along(com.simibubi.create.AllBlocks.SHAFT.getDefaultState(), axis);
            BlockState cog = along(com.simibubi.create.AllBlocks.COGWHEEL.getDefaultState(), axis);
            int jogFrom = gear ? 2 : from + 1, jogTo = gear ? n - 3 : to - 1;
            boolean jog = standIn.jog() && jogTo >= jogFrom + 2;
            Direction aside = null;
            if (jog) {
                for (Direction direction : Direction.values()) {
                    BlockPos next = line.relative(direction);
                    if (direction.getAxis() != axis && Math.min(Math.min(next.getX(), next.getY()), next.getZ()) >= 0
                            && Math.max(Math.max(next.getX(), next.getY()), next.getZ()) < n) {
                        aside = direction;
                        break;
                    }
                }
            }
            List<Part> parts = new ArrayList<>();
            for (int i = from; i <= to; i++) {
                BlockPos at = withCoordinate(line, axis, i);
                boolean end = gear && (i == from || i == to);
                if (aside == null || i < jogFrom || i > jogTo) {
                    parts.add(new Part(at, end ? cog : shaft));
                } else if (i == jogFrom || i == jogTo) {
                    parts.add(new Part(at, cog));
                    parts.add(new Part(at.relative(aside), cog));
                } else {
                    parts.add(new Part(at.relative(aside), shaft));
                }
            }
            return parts;
        }

        private static BlockPos withCoordinate(BlockPos cell, Direction.Axis axis, int value) {
            return switch (axis) {
                case X -> new BlockPos(value, cell.getY(), cell.getZ());
                case Y -> new BlockPos(cell.getX(), value, cell.getZ());
                case Z -> new BlockPos(cell.getX(), cell.getY(), value);
            };
        }

        /** The part a break in the middle of the stand-in takes out. */
        private static Part middle(ParityScene.StandIn standIn) {
            List<Part> parts = path(standIn);
            return parts.get(parts.size() / 2);
        }

        @javax.annotation.Nullable
        private SubgridBlockEntity gridAt(BlockPos cell) {
            return server.getBlockEntity(real(cell)) instanceof SubgridBlockEntity be ? be : null;
        }

        @Override
        public void build(BlockPos cell, BlockState state, boolean shaped) {
            ParityScene.StandIn standIn = standIns.get(cell);
            if (standIn == null) {
                super.build(cell, state, shaped);
                return;
            }
            stoodFor.put(cell, state);
            server.setBlock(real(cell), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            SubgridBlockEntity be = SubgridBlockEntity.createAt(server, real(cell), standIn.gridSize());
            if (be == null) throw new IllegalStateException("no grid at " + real(cell));
            for (Direction.AxisDirection way : Direction.AxisDirection.values()) {
                if (!com.therappha.tinyblocks.compat.create.ConnectorPlacement.place(be, server, Direction.fromAxisAndDirection(standIn.axis(), way))) {
                    throw new IllegalStateException("no connector on " + way + " " + standIn.axis() + " of the 1/" + standIn.gridSize() + " grid");
                }
            }
            for (Part part : path(standIn)) SubgridTestSupport.placePiece(be, server, part.cell(), part.state());
        }

        @Override
        public void breakCell(BlockPos cell) {
            ParityScene.StandIn standIn = standIns.get(cell);
            SubgridBlockEntity be = standIn == null ? null : gridAt(cell);
            if (standIn == null) super.breakCell(cell);
            else if (be != null) be.removePieceAt(middle(standIn).cell().getX(), middle(standIn).cell().getY(), middle(standIn).cell().getZ());
        }

        @Override
        public void place(BlockPos cell, BlockState state) {
            ParityScene.StandIn standIn = standIns.get(cell);
            SubgridBlockEntity be = standIn == null ? null : gridAt(cell);
            if (standIn == null) {
                super.place(cell, state);
                return;
            }
            Part part = middle(standIn);
            if (be != null && be.getPieceAt(part.cell().getX(), part.cell().getY(), part.cell().getZ()) == null) {
                SubgridTestSupport.placePiece(be, server, part.cell(), part.state());
            }
        }

        /** Whether the stand-in at {@code cell} is whole: its grid there, and the part a break takes out. */
        private boolean whole(BlockPos cell) {
            SubgridBlockEntity be = gridAt(cell);
            BlockPos part = middle(standIns.get(cell)).cell();
            return be != null && be.getPieceAt(part.getX(), part.getY(), part.getZ()) != null;
        }

        @Override
        public BlockState state(BlockPos cell) {
            if (!standIns.containsKey(cell)) return super.state(cell);
            return whole(cell) && stoodFor.containsKey(cell) ? stoodFor.get(cell) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }

        @Override
        public BlockState drawn(BlockPos cell) {
            return state(cell);
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity blockEntity(BlockPos cell) {
            ParityScene.StandIn standIn = standIns.get(cell);
            SubgridBlockEntity be = standIn == null ? null : gridAt(cell);
            if (standIn == null) return super.blockEntity(cell);
            if (be == null || !whole(cell)) return null;
            for (PlacedPiece piece : be.getPieces()) {
                if (com.therappha.tinyblocks.compat.create.ShaftConnectorPiece.faceOf(piece) == standIn.downstream()) {
                    return VanillaBlockPiece.boundPhantom(piece, be, server);
                }
            }
            return null;
        }

        @Override
        public int analogSignal(BlockPos cell) {
            return standIns.containsKey(cell) ? 0 : super.analogSignal(cell);
        }
    }
}
