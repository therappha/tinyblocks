package com.therappha.tinyblocks.subgrid;

import com.therappha.tinyblocks.network.PhantomDataPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/** Pushing a grid's phantom block entity data to clients; one per grid. */
final class PhantomSync {

    private final SubgridBlockEntity grid;

    PhantomSync(SubgridBlockEntity grid) {
        this.grid = grid;
    }

    /**
     * Set when a fake level's sendBlockUpdated fires (a phantom BlockEntity asked to sync, e.g. Create's
     * SmartBlockEntity#sendData); flushed once per tick in serverTick as PhantomDataPayloads.
     */
    private boolean phantomDataDirty = false;

    void markDirty() {
        phantomDataDirty = true;
    }

    /** How many times phantom data was pushed to clients; lets GameTests observe the sync without a client. */
    private int phantomDataFlushes = 0;

    int flushes() {
        return phantomDataFlushes;
    }

    /** How often (ticks) phantom data is compared against what clients last received. */
    private static final int PHANTOM_CHANGE_SCAN_INTERVAL = 20;

    /**
     * Pushes phantom block entity data to clients. Always when something marked it dirty (everything is
     * resent), and otherwise every few ticks for just the phantoms whose saved data changed since the last
     * push: block entities change on their own after the event that touched them (a kinetic block dropping
     * to speed 0 a while after its motor was removed) and nothing reliably calls back for that (Create's
     * sendData goes through ServerChunkCache#blockChanged, which the fake levels cannot intercept).
     * Containers are left out of the periodic scan: serializing a full inventory every pass is wasteful and
     * their menus already sync themselves.
     */
    void flush(ServerLevel level) {
        boolean all = phantomDataDirty;
        if (!all && level.getGameTime() % PHANTOM_CHANGE_SCAN_INTERVAL != 0) return;
        phantomDataDirty = false;
        boolean sentAny = false;
        for (PlacedPiece piece : grid.getPieces()) {
            if (!(piece.runtimeBlockEntity instanceof BlockEntity phantom)) continue;
            // A piston's moving-block BE has its own dedicated animation payload (see PistonAnimationPayload).
            if (phantom instanceof PistonMovingBlockEntity) continue;
            if (!all && phantom instanceof Container) continue;
            var tag = phantom.saveCustomOnly(level.registryAccess());
            int hash = tag.hashCode();
            if (!all && hash == piece.lastSyncedPhantomHash) continue;
            piece.lastSyncedPhantomHash = hash;
            sentAny = true;
            // What a chunk would send a client for this block entity, when it sends anything: a block entity can
            // read a client's update differently from its save (Create's tank only moves the fluid it draws on one).
            var update = phantom.getUpdateTag(level.registryAccess());
            boolean clientUpdate = !update.isEmpty();
            PacketDistributor.sendToPlayersTrackingChunk(level,
                    new ChunkPos(grid.getBlockPos()),
                    new PhantomDataPayload(grid.getBlockPos(), new BlockPos(piece.anchor), clientUpdate ? update : tag, clientUpdate));
        }
        if (sentAny) phantomDataFlushes++;
    }

    /**
     * A change in this grid can alter the phantom block entities of the grids next to it without anything
     * telling THEM (Create's rotation propagating across the edge sets the neighbour's speed, but only this
     * grid is re-synced); queue a phantom data push for each adjacent same-size grid. Skipped when this grid
     * holds no phantom block entity of its own, so plain block grids (water, redstone) cause no extra traffic.
     */
    void markNeighboursDirty() {
        boolean anyPhantom = false;
        for (PlacedPiece piece : grid.getPieces()) {
            if (piece.runtimeBlockEntity != null) { anyPhantom = true; break; }
        }
        if (!anyPhantom) return;
        for (Direction dir : Direction.values()) {
            if (grid.getLevel().getBlockEntity(grid.getBlockPos().relative(dir)) instanceof SubgridBlockEntity other && other.gridSize == grid.gridSize) {
                other.markPhantomDataDirty();
            }
        }
    }
}
