package com.therappha.tinyblocks.v2;

import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Which SubgridBlock (and which cell of it) a phantom block entity belongs to. A phantom's level is not a
 * reliable marker — it is re-pointed between the real level and fake spaces — so code that must tell a
 * phantom from a real block entity (the Create kinetic bridge) asks here.
 *
 * The owner holds the grid weakly: the piece holds the phantom strongly, so a strong grid reference here
 * would keep every phantom (the weak key) reachable forever.
 */
public final class PhantomOwners {

    private PhantomOwners() {}

    public record Owner(WeakReference<SubgridBlockEntity> gridRef, BlockPos cell) {
        @Nullable
        public SubgridBlockEntity grid() { return gridRef.get(); }
    }

    private static final Map<BlockEntity, Owner> OWNERS = Collections.synchronizedMap(new WeakHashMap<>());

    static void register(BlockEntity phantom, SubgridBlockEntity grid, PlacedPiece piece) {
        OWNERS.put(phantom, new Owner(new WeakReference<>(grid), piece.anchor.immutable()));
    }

    /**
     * Where in the real world a phantom is (its SubgridBlock's position), or null for anything else. Still answered
     * for one on its way out, its piece or grid already gone: a tiny link leaving tells its network from here, and
     * from its virtual position every link in the network would be out of range and stay powered.
     */
    @Nullable
    public static BlockPos realPosOf(BlockEntity be) {
        Owner owner = OWNERS.get(be);
        SubgridBlockEntity grid = owner == null ? null : owner.grid();
        return grid == null ? null : grid.getBlockPos();
    }

    /** The owner of a phantom that is still the live phantom of its piece, or null for anything else. */
    @Nullable
    public static Owner of(BlockEntity be) {
        Owner owner = OWNERS.get(be);
        if (owner == null) return null;
        SubgridBlockEntity grid = owner.grid();
        if (grid == null || grid.isRemoved()) return null;
        PlacedPiece piece = grid.getPieceAt(owner.cell().getX(), owner.cell().getY(), owner.cell().getZ());
        return piece != null && piece.runtimeBlockEntity == be ? owner : null;
    }
}
