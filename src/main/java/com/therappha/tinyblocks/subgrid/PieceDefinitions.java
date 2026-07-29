package com.therappha.tinyblocks.subgrid;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PieceDefinitions {

    public static final PieceDefinition STONE_BLOCK = new PieceDefinition(
            ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "stone_block"),
            new Vec3i(1, 1, 1)) {
        @Override
        public BlockState renderState(Direction.Axis axis) {
            return Blocks.STONE.defaultBlockState();
        }

        @Override
        public float destroyTime() { return 1.5f; }

        @Override
        public boolean requiresCorrectTool() { return true; }

        @Override
        public List<ItemStack> drops(PlacedPiece piece) {
            return List.of(new ItemStack(Items.COBBLESTONE));
        }
    };

    /** Debug piece for testing neighbor propagation — always "powered", never ticks. */
    public static final PieceDefinition TINY_REDSTONE_BLOCK = new PieceDefinition(
            ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "tiny_redstone_block"),
            new Vec3i(1, 1, 1)) {
        @Override
        public BlockState renderState(Direction.Axis axis) {
            return Blocks.REDSTONE_BLOCK.defaultBlockState();
        }

        @Override
        public List<ItemStack> drops(PlacedPiece piece) {
            return List.of(new ItemStack(Items.REDSTONE));
        }
    };

    /** Debug piece for testing neighbor propagation — conducts power like redstone dust. */
    public static final PieceDefinition TINY_REDSTONE_DUST = new PieceDefinition(
            ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "tiny_redstone_dust"),
            new Vec3i(1, 1, 1)) {
        @Override
        public BlockState renderState(Direction.Axis axis) {
            return Blocks.REDSTONE_WIRE.defaultBlockState();
        }

        @Override
        public BlockState renderState(PlacedPiece piece) {
            BlockState state = Blocks.REDSTONE_WIRE.defaultBlockState()
                    .setValue(BlockStateProperties.POWER, piece.extraData.getBoolean("connected") ? 15 : 0);
            for (Direction dir : HORIZONTAL) {
                boolean linked = piece.extraData.getBoolean(connKey(dir));
                state = state.setValue(wireProperty(dir), linked ? RedstoneSide.SIDE : RedstoneSide.NONE);
            }
            return state;
        }

        @Override
        public List<ItemStack> drops(PlacedPiece piece) {
            return List.of(new ItemStack(Items.REDSTONE));
        }

        @Override
        public void onNeighborChanged(PlacedPiece piece, ServerLevel level, BlockPos subgridPos,
                                       SubgridBlockEntity be, PlacedPiece changedNeighbor) {
            recomputeLightNetwork(be, piece);
        }
    };

    /**
     * Debug piece for testing self-modifying pieces — places/removes its own TINY_PISTON_HEAD
     * one cell ahead (along the positive direction of its placement axis) when it becomes
     * adjacent to power. Only extends within the same subgrid; blocked at the grid edge.
     */
    public static final PieceDefinition TINY_PISTON = new PieceDefinition(
            ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "tiny_piston"),
            new Vec3i(1, 1, 1)) {
        @Override
        public BlockState renderState(Direction.Axis axis) {
            return Blocks.PISTON.defaultBlockState();
        }

        @Override
        public BlockState renderState(PlacedPiece piece) {
            return Blocks.PISTON.defaultBlockState()
                    .setValue(BlockStateProperties.FACING, extendDirection(piece))
                    .setValue(BlockStateProperties.EXTENDED, piece.extraData.getBoolean("extended"));
        }

        @Override
        public List<ItemStack> drops(PlacedPiece piece) {
            return List.of(new ItemStack(Items.PISTON));
        }

        @Override
        public void onNeighborChanged(PlacedPiece piece, ServerLevel level, BlockPos subgridPos,
                                       SubgridBlockEntity be, PlacedPiece changedNeighbor) {
            Direction dir = extendDirection(piece);
            int tx = piece.anchor.getX() + dir.getStepX();
            int ty = piece.anchor.getY() + dir.getStepY();
            int tz = piece.anchor.getZ() + dir.getStepZ();
            if (!be.inBounds(tx, ty, tz)) return;

            PlacedPiece atTarget = be.getPieceAt(tx, ty, tz);
            boolean hasOwnHead = atTarget != null && atTarget.definition == TINY_PISTON_HEAD;
            boolean powered = isPowered(be, piece);

            if (powered == hasOwnHead) return;
            piece.extraData.putBoolean("extended", powered);
            if (powered) {
                if (atTarget == null) be.placePiece(new PlacedPiece(TINY_PISTON_HEAD, new BlockPos(tx, ty, tz), piece.axis));
            } else {
                be.removePieceAt(tx, ty, tz);
            }
        }
    };

    /** Placed and removed only by TINY_PISTON itself — not obtainable as an item. */
    public static final PieceDefinition TINY_PISTON_HEAD = new PieceDefinition(
            ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "tiny_piston_head"),
            new Vec3i(1, 1, 1)) {
        @Override
        public BlockState renderState(Direction.Axis axis) {
            return Blocks.PISTON_HEAD.defaultBlockState();
        }

        @Override
        public BlockState renderState(PlacedPiece piece) {
            return Blocks.PISTON_HEAD.defaultBlockState().setValue(BlockStateProperties.FACING, extendDirection(piece));
        }

        @Override
        public List<ItemStack> drops(PlacedPiece piece) {
            return List.of();
        }

        @Override
        public void onNeighborChanged(PlacedPiece piece, ServerLevel level, BlockPos subgridPos,
                                       SubgridBlockEntity be, PlacedPiece changedNeighbor) {
            // Self-destruct if the piston that placed this head is gone — the base is always
            // one cell behind, opposite of the direction this head faces.
            Direction back = extendDirection(piece).getOpposite();
            SubgridBlockEntity.Neighbor base = be.neighborFacing(piece, back);
            if (base == null || base.piece().definition != TINY_PISTON) {
                be.removePieceAt(piece.anchor.getX(), piece.anchor.getY(), piece.anchor.getZ());
            }
        }
    };

    private static Direction extendDirection(PlacedPiece piece) {
        return Direction.get(Direction.AxisDirection.POSITIVE, piece.axis);
    }

    private static boolean isPowered(SubgridBlockEntity be, PlacedPiece piece) {
        for (SubgridBlockEntity.Neighbor neighbor : be.neighborsOf(piece)) {
            PieceDefinition def = neighbor.piece().definition;
            if (def == TINY_REDSTONE_BLOCK) return true;
            if (def == TINY_REDSTONE_DUST && neighbor.piece().extraData.getBoolean("connected")) return true;
        }
        return false;
    }

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private static String connKey(Direction dir) {
        return "conn_" + dir.getName();
    }

    private static EnumProperty<RedstoneSide> wireProperty(Direction dir) {
        return switch (dir) {
            case NORTH -> RedStoneWireBlock.NORTH;
            case SOUTH -> RedStoneWireBlock.SOUTH;
            case EAST -> RedStoneWireBlock.EAST;
            case WEST -> RedStoneWireBlock.WEST;
            default -> throw new IllegalArgumentException("Not a horizontal direction: " + dir);
        };
    }

    /**
     * Flood-fills the whole connected component of touching TINY_REDSTONE_BLOCK/TINY_REDSTONE_DUST pieces
     * starting from `start`, crossing into neighboring SubgridBlocks via SubgridBlockEntity's
     * cross-grid neighbor lookup. Every TINY_REDSTONE_DUST in the component is powered if the
     * component contains at least one TINY_REDSTONE_BLOCK. Recomputing the whole component from
     * scratch (rather than checking only the immediate neighbor) avoids stale state when a
     * piece in the middle of a chain — possibly in another block — is removed.
     */
    private static void recomputeLightNetwork(SubgridBlockEntity be, PlacedPiece start) {
        record Ref(SubgridBlockEntity be, PlacedPiece piece) {}

        Set<Ref> component = new HashSet<>();
        Deque<Ref> queue = new ArrayDeque<>();
        Ref startRef = new Ref(be, start);
        component.add(startRef);
        queue.add(startRef);

        boolean powered = false;
        while (!queue.isEmpty()) {
            Ref current = queue.poll();
            if (current.piece().definition == TINY_REDSTONE_BLOCK) powered = true;
            for (SubgridBlockEntity.Neighbor neighbor : current.be().neighborsOf(current.piece())) {
                if (neighbor.piece().definition != TINY_REDSTONE_BLOCK && neighbor.piece().definition != TINY_REDSTONE_DUST) continue;
                Ref ref = new Ref(neighbor.be(), neighbor.piece());
                if (component.add(ref)) queue.add(ref);
            }
        }

        Set<SubgridBlockEntity> toSync = new HashSet<>();
        List<Ref> powerChanged = new ArrayList<>();
        for (Ref ref : component) {
            if (ref.piece().definition != TINY_REDSTONE_DUST) continue;
            boolean stateChanged = powered != ref.piece().extraData.getBoolean("connected");
            if (stateChanged) {
                ref.piece().extraData.putBoolean("connected", powered);
                powerChanged.add(ref);
            }

            for (Direction dir : HORIZONTAL) {
                SubgridBlockEntity.Neighbor facing = ref.be().neighborFacing(ref.piece(), dir);
                boolean linked = facing != null
                        && (facing.piece().definition == TINY_REDSTONE_BLOCK || facing.piece().definition == TINY_REDSTONE_DUST);
                if (linked != ref.piece().extraData.getBoolean(connKey(dir))) {
                    ref.piece().extraData.putBoolean(connKey(dir), linked);
                    stateChanged = true;
                }
            }
            if (stateChanged) toSync.add(ref.be());
        }
        toSync.forEach(SubgridBlockEntity::notifyUpdate);
        // Tell non-conducting neighbors (e.g. a piston) that this dust's power actually changed —
        // recomputing extraData directly above doesn't itself notify anyone outside this method.
        for (Ref ref : powerChanged) {
            ref.be().notifyNeighbors(ref.piece());
        }
    }

    public static void init() {}

    private PieceDefinitions() {}
}
