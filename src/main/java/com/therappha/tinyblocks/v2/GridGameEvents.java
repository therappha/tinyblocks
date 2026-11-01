package com.therappha.tinyblocks.v2;

import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Game events among pieces. A real listener (a sculk sensor) sits in its chunk's registry, which a phantom block entity
 * never reaches, so an event in a fake space is passed here to the phantoms that listen, as a level's dispatcher passes
 * it to listeners in range. Grids of one size share a virtual frame, so a sensor hears across a seam. Range is counted
 * in cells, the way everything else in a grid is: 8 cells away is 8 blocks to the block at that scale. Events and
 * listeners on opposite sides of the real world and a grid don't reach each other.
 */
final class GridGameEvents {

    private GridGameEvents() {}

    /** Only grids holding a listener: most grids hold none, and events are many. */
    private static final Set<SubgridBlockEntity> LISTENING = Collections.newSetFromMap(new WeakHashMap<>());

    static void register(BlockEntity phantom, SubgridBlockEntity grid) {
        if (phantom instanceof GameEventListener.Provider<?> && grid.getLevel() instanceof ServerLevel) LISTENING.add(grid);
    }

    static void post(SubgridBlockEntity from, Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
        if (LISTENING.isEmpty() || !(from.getLevel() instanceof ServerLevel level)) return;
        BlockPos source = BlockPos.containing(pos);
        for (SubgridBlockEntity grid : new ArrayList<>(LISTENING)) {
            if (grid.isRemoved() || grid.getLevel() != level || grid.getGridSize() != from.getGridSize()) continue;
            for (PlacedPiece piece : new ArrayList<>(grid.getPieces())) {
                if (!(piece.runtimeBlockEntity instanceof GameEventListener.Provider<?> provider)) continue;
                BlockEntity phantom = VanillaBlockPiece.boundPhantom(piece, grid, level);
                if (!(phantom.getLevel() instanceof ServerLevel space)) continue;
                GameEventListener listener = provider.getListener();
                Optional<Vec3> at = listener.getListenerSource().getPosition(space);
                int radius = listener.getListenerRadius();
                if (at.isEmpty() || BlockPos.containing(at.get()).distSqr(source) > radius * radius) continue;
                listener.handleGameEvent(space, event, context, pos);
            }
        }
    }
}
