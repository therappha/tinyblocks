package com.therappha.tinyblocks.subgrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SubgridBlockEntity extends BlockEntity {

    private static final short EMPTY = -1;

    public final int gridSize;
    private final short[] cellOwner;
    private final List<PlacedPiece> pieces = new ArrayList<>();
    private VoxelShape cachedShape = Shapes.empty();

    public SubgridBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.gridSize = state.getBlock() instanceof SubgridBlock sb ? sb.gridSize : 8;
        this.cellOwner = new short[gridSize * gridSize * gridSize];
        Arrays.fill(cellOwner, EMPTY);
    }

    public VoxelShape getCachedShape() { return cachedShape; }
    public int getGridSize() { return gridSize; }
    public List<PlacedPiece> getPieces() { return pieces; }

    public short ownerAt(int x, int y, int z) {
        return cellOwner[indexOf(x, y, z)];
    }

    // --- Public API ---

    public boolean placePiece(PlacedPiece piece) {
        if (!canFit(piece)) return false;
        addToGrid(piece);
        notifyUpdate();
        notifyNeighbors(piece);
        // The piece also needs to react to the neighbors it was just placed next to —
        // notifyNeighbors() only informs the pieces around it, not itself.
        if (level instanceof ServerLevel serverLevel) {
            piece.definition.onNeighborChanged(piece, serverLevel, worldPosition, this, piece);
        }
        return true;
    }

    @Nullable
    public PlacedPiece getPieceAt(int x, int y, int z) {
        short idx = cellOwner[indexOf(x, y, z)];
        if (idx == EMPTY) return null;
        return pieces.get(idx);
    }

    @Nullable
    public PlacedPiece removePieceAt(int x, int y, int z) {
        short idx = cellOwner[indexOf(x, y, z)];
        if (idx == EMPTY) return null;
        PlacedPiece removed = pieces.get(idx);
        rebuildAfterRemove(idx);
        notifyUpdate();
        notifyNeighbors(removed);
        return removed;
    }

    public void serverTick(ServerLevel level) {
        boolean dirty = false;
        for (PlacedPiece piece : pieces) {
            if (piece.definition.requiresTick() && piece.definition.tick(piece, level, worldPosition, this)) {
                dirty = true;
                notifyNeighbors(piece);
            }
        }
        if (dirty) notifyUpdate();
    }

    /** A piece and the SubgridBlockEntity that hosts it — a neighbor may live in another block. */
    public record Neighbor(SubgridBlockEntity be, PlacedPiece piece) {}

    /**
     * Calls onNeighborChanged on every piece adjacent to changed's footprint, including pieces
     * hosted by a neighboring SubgridBlock across the grid boundary (same grid size only).
     */
    public void notifyNeighbors(PlacedPiece changed) {
        if (!(level instanceof ServerLevel)) return;
        for (Neighbor neighbor : neighborsOf(changed)) {
            if (neighbor.be().level instanceof ServerLevel neighborLevel) {
                neighbor.piece().definition.onNeighborChanged(
                        neighbor.piece(), neighborLevel, neighbor.be().getBlockPos(), neighbor.be(), changed);
            }
        }
    }

    /**
     * Pieces currently touching piece's footprint face-to-face. Cells on the edge of this grid
     * also check the neighboring real-world block: if it hosts a SubgridBlockEntity with the
     * same gridSize, the mirrored cell on its near face counts as touching too. Grids of
     * different sizes never propagate into each other — their cells don't line up.
     */
    public Set<Neighbor> neighborsOf(PlacedPiece piece) {
        Set<Neighbor> result = new LinkedHashSet<>();
        for (int x = piece.anchor.getX(); x < piece.anchor.getX() + piece.footprint.getX(); x++)
            for (int y = piece.anchor.getY(); y < piece.anchor.getY() + piece.footprint.getY(); y++)
                for (int z = piece.anchor.getZ(); z < piece.anchor.getZ() + piece.footprint.getZ(); z++)
                    for (Direction dir : Direction.values()) {
                        if (piece.occupies(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ())) continue;
                        Neighbor neighbor = neighborAt(x, y, z, dir);
                        if (neighbor != null) result.add(neighbor);
                    }
        return result;
    }

    /**
     * The single piece touching cell (piece.anchor + dir), if any. Only meaningful for
     * single-cell (1x1x1 footprint) pieces — larger footprints should use neighborsOf instead.
     */
    @Nullable
    public Neighbor neighborFacing(PlacedPiece piece, Direction dir) {
        return neighborAt(piece.anchor.getX(), piece.anchor.getY(), piece.anchor.getZ(), dir);
    }

    @Nullable
    private Neighbor neighborAt(int x, int y, int z, Direction dir) {
        int nx = x + dir.getStepX(), ny = y + dir.getStepY(), nz = z + dir.getStepZ();
        if (outOfBounds(nx, ny, nz)) return crossGridNeighborAt(dir, nx, ny, nz);
        short idx = cellOwner[indexOf(nx, ny, nz)];
        return idx != EMPTY ? new Neighbor(this, pieces.get(idx)) : null;
    }

    @Nullable
    private Neighbor crossGridNeighborAt(Direction dir, int nx, int ny, int nz) {
        if (!(level instanceof ServerLevel)) return null;
        if (!(level.getBlockEntity(worldPosition.relative(dir)) instanceof SubgridBlockEntity other)) return null;
        if (other.gridSize != gridSize) return null;
        int max = gridSize - 1;
        PlacedPiece piece = other.getPieceAt(wrap(nx, max), wrap(ny, max), wrap(nz, max));
        return piece != null ? new Neighbor(other, piece) : null;
    }

    private static int wrap(int v, int max) {
        if (v < 0) return max;
        if (v > max) return 0;
        return v;
    }

    public void notifyUpdate() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- Grid helpers ---

    private boolean canFit(PlacedPiece p) {
        for (int x = p.anchor.getX(); x < p.anchor.getX() + p.footprint.getX(); x++)
            for (int y = p.anchor.getY(); y < p.anchor.getY() + p.footprint.getY(); y++)
                for (int z = p.anchor.getZ(); z < p.anchor.getZ() + p.footprint.getZ(); z++) {
                    if (outOfBounds(x, y, z)) return false;
                    if (cellOwner[indexOf(x, y, z)] != EMPTY) return false;
                }
        return true;
    }

    private void addToGrid(PlacedPiece p) {
        short idx = (short) pieces.size();
        pieces.add(p);
        for (int x = p.anchor.getX(); x < p.anchor.getX() + p.footprint.getX(); x++)
            for (int y = p.anchor.getY(); y < p.anchor.getY() + p.footprint.getY(); y++)
                for (int z = p.anchor.getZ(); z < p.anchor.getZ() + p.footprint.getZ(); z++)
                    cellOwner[indexOf(x, y, z)] = idx;
        rebuildShape();
    }

    private void rebuildAfterRemove(short removedIdx) {
        pieces.remove(removedIdx);
        Arrays.fill(cellOwner, EMPTY);
        for (int i = 0; i < pieces.size(); i++) {
            PlacedPiece p = pieces.get(i);
            for (int x = p.anchor.getX(); x < p.anchor.getX() + p.footprint.getX(); x++)
                for (int y = p.anchor.getY(); y < p.anchor.getY() + p.footprint.getY(); y++)
                    for (int z = p.anchor.getZ(); z < p.anchor.getZ() + p.footprint.getZ(); z++)
                        cellOwner[indexOf(x, y, z)] = (short) i;
        }
        rebuildShape();
    }

    private void rebuildShape() {
        double cell = 16.0 / gridSize;
        VoxelShape result = Shapes.empty();
        for (PlacedPiece p : pieces) {
            double x1 = p.anchor.getX() * cell;
            double y1 = p.anchor.getY() * cell;
            double z1 = p.anchor.getZ() * cell;
            result = Shapes.or(result, Block.box(
                x1, y1, z1,
                x1 + p.footprint.getX() * cell,
                y1 + p.footprint.getY() * cell,
                z1 + p.footprint.getZ() * cell
            ));
        }
        cachedShape = result;
    }

    public int indexOf(int x, int y, int z) {
        return y * gridSize * gridSize + z * gridSize + x;
    }

    private boolean outOfBounds(int x, int y, int z) {
        return x < 0 || x >= gridSize || y < 0 || y >= gridSize || z < 0 || z >= gridSize;
    }

    public boolean inBounds(int x, int y, int z) {
        return !outOfBounds(x, y, z);
    }

    // --- NBT ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (PlacedPiece p : pieces)
            p.definition.onSaving(p, registries);
        ListTag list = new ListTag();
        for (PlacedPiece p : pieces)
            list.add(p.save());
        tag.put("pieces", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pieces.clear();
        Arrays.fill(cellOwner, EMPTY);
        ListTag list = tag.getList("pieces", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            PlacedPiece piece = PlacedPiece.load(list.getCompound(i));
            addToGrid(piece);
            piece.definition.onLoaded(piece, registries);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
