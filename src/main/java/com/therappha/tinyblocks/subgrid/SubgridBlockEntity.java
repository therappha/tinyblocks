package com.therappha.tinyblocks.subgrid;

import net.minecraft.core.BlockPos;
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
import java.util.List;

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
        return removed;
    }

    public void serverTick(ServerLevel level) {
        boolean dirty = false;
        for (PlacedPiece piece : pieces) {
            if (piece.definition.requiresTick()) {
                dirty |= piece.definition.tick(piece, level, worldPosition, this);
            }
        }
        if (dirty) setChanged();
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
