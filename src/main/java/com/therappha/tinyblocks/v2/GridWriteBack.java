package com.therappha.tinyblocks.v2;

import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes what a fake space's call changed back into the grid: piece states, pieces that appeared or went, the block
 * entities vanilla handed over, and the ticks it scheduled.
 */
public final class GridWriteBack {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private GridWriteBack() {}

    /**
     * Writes any sibling states that changed during the fake-space call back into their pieces,
     * and merges any scheduleTick requests the vanilla logic made (e.g. a redstone lamp asking
     * to turn itself off in 4 ticks) into be's own persistent queue — both FakeLevel and
     * FakeServerLevel capture these instead of touching a real tick list, since scheduledTick(...)
     * is what actually drains and fires them later.
     */
    static void applyChanges(SubgridBlockEntity be, FakeSpace fakeSpace, ServerLevel realLevel) {
        FakeCellGetter cells = fakeSpace.cells();
        boolean anyChanged = false;
        // What the written cells held before their first write this call (a write reaching a cell from outside in
        // the meantime, another grid's call placing a piece here, is not this call's change), and those pieces in the
        // grid's own order, so neighbours hear of changes in the order they did when every piece was diffed.
        Map<BlockPos, BlockState> before = new HashMap<>();
        List<PlacedPiece> written = new ArrayList<>();
        for (BlockPos pos : cells.touchedCells().keySet()) {
            PlacedPiece p = be.getPieceAt(pos.getX(), pos.getY(), pos.getZ());
            if (p == null || !(p.definition instanceof VanillaBlockPiece) || !p.anchor.equals(pos)) continue;
            BlockState original = cells.originalState(pos);
            before.put(p.anchor, original != null ? original : VanillaBlockPiece.stateOf(p));
            written.add(p);
        }
        written.sort(java.util.Comparator.comparingInt(p -> be.ownerAt(p.anchor.getX(), p.anchor.getY(), p.anchor.getZ())));
        // Collected rather than removed inline — removePieceAt mutates be.getPieces() itself,
        // which we're still iterating.
        List<PlacedPiece> selfDestructed = new ArrayList<>();
        // A snapshot, not the live list: be.notifyNeighbors(p) below can synchronously cascade
        // into another piece's own onNeighborChanged, which can run its OWN applyChanges call and
        // add/remove pieces via placePieceCrossBoundary/removePieceAt — mutating be.getPieces()
        // while THIS loop's iterator is still walking it (a piston's moving-block BE finalizing is
        // exactly this: found live via a ConcurrentModificationException from onUse -> applyChanges
        // once the finalize path started actually completing instead of crashing before reaching
        // notifyNeighbors at all). Same fix as SubgridBlockEntity.serverTick's own loop.
        for (PlacedPiece p : written) {
            // getStateRaw, NOT getBlockState: p.anchor is already this cell space's own local
            // coordinate, not a realPos-relative one — feeding it through an active alias (onUse,
            // tick) would double-translate it into a nonsense position, permanently landing on
            // fallback()'s out-of-bounds branch (AIR) for every single piece. That's exactly what
            // silently destroyed the whole subgrid on any interaction once resolve() started doing
            // full neighborhood translation (see SubgridFakeCellGetter.resolve's own comment) —
            // this diff read addresses cells by raw anchor instead of by a vanilla-supplied
            // position, so it needs the raw, alias-bypassing accessor.
            BlockState now = cells.getStateRaw(p.anchor);
            if (!now.equals(before.get(p.anchor))) {
                if (now.isAir()) {
                    // A piece's own updateShape/handleNeighborChanged (e.g. a crop losing its
                    // farmland support) decided it can no longer exist here — matches vanilla's
                    // own "setBlock to air = this block was destroyed" semantics. Just storing
                    // air as this piece's new state would leave a ghost piece still occupying
                    // the cell (invisible, but still "there") — same real vanilla drop path any
                    // other removal uses instead.
                    selfDestructed.add(p);
                } else {
                    p.runtimeState = now;
                    syncBlockEntity(be, p, p.anchor, p.anchor, cells, (Level) fakeSpace, realLevel);
                    followStateChange(be, p, before.get(p.anchor), now, cells);
                    if (cells.isSettled(p.anchor)) be.markPiecesChanged();
                    else be.notifyNeighbors(p);
                    // This piece's new state might be the delayed arrival of a block that vacated
                    // some OTHER cell several ticks ago (see the deferred-drop comment below) — a
                    // sticky piston retract, for instance, sets the pulled block's old cell to air
                    // immediately (onNeighborChanged) but only writes its arrival at the new cell
                    // once that cell's own captured BE finalizes, ticks later. Cancel any pending
                    // drop with a matching state so it isn't ALSO dropped as an item once its grace
                    // window expires — found live as a duplication bug (block visibly moved, but an
                    // item of the same block dropped anyway).
                    be.cancelPendingDrop(now);
                }
                anyChanged = true;
            }
        }
        for (PlacedPiece p : selfDestructed) {
            // A piston pushing/pulling a block vacates its ORIGINAL cell the exact same way as a
            // genuine destruction: a plain setBlock to air, via moveBlocks' own final "clear the
            // old positions" loop (PistonBaseBlock.moveBlocks) — vanilla itself only calls
            // dropResources for blocks it couldn't push (getToDestroy()), never for ones it
            // successfully moved (getToPush()). The diff loop above can't otherwise tell "destroyed"
            // from "moved elsewhere this same call" apart, since both look identical from here: an
            // existing piece's anchor going from some state to air. If that SAME state shows up at
            // a DIFFERENT position in this call's touchedCells (the moved block's destination,
            // about to become a new piece below), treat it as relocated — drop nothing, since the
            // block still exists, just at a new cell.
            BlockState previousState = before.get(p.anchor);
            boolean movedElsewhere = false;
            for (Map.Entry<BlockPos, BlockState> touched : cells.touchedCells().entrySet()) {
                if (!touched.getKey().equals(p.anchor) && previousState.equals(touched.getValue())) {
                    movedElsewhere = true;
                    break;
                }
            }
            // The cell map used to hold every piece's state too, so a same state anywhere else in the grid also
            // counted as "moved elsewhere"; kept as it was (rare: only a piece gone to air gets here).
            for (PlacedPiece q : be.getPieces()) {
                if (movedElsewhere) break;
                if (q.definition instanceof VanillaBlockPiece && !q.anchor.equals(p.anchor)
                        && !cells.touchedCells().containsKey(q.anchor) && previousState.equals(VanillaBlockPiece.stateOf(q))) {
                    movedElsewhere = true;
                }
            }
            // realPositionOf, not be.getBlockPos()+0.5 — that would collapse every piece's drop
            // to the whole SubgridBlock's shared center regardless of where inside it the piece
            // actually was.
            Vec3 dropPos = be.realPositionOf(p.anchor);
            PlacedPiece[] removedPiece = new PlacedPiece[1];
            Runnable remove = () -> removedPiece[0] = be.removePieceAt(p.anchor.getX(), p.anchor.getY(), p.anchor.getZ());
            if (cells.isSettled(p.anchor)) SubgridBlockEntity.whileApplyingSettled(remove);
            else remove.run();
            PlacedPiece removed = removedPiece[0];
            // In a server space vanilla's own code has dropped whatever this block drops (FakeServerLevel#addFreshEntity):
            // going to air by itself drops nothing, as with burnt planks or lit TNT. A plain fake level can't drop.
            if (removed != null && !movedElsewhere && !(fakeSpace instanceof FakeServerLevel)) {
                // Deferred, not dropped immediately: the "moved elsewhere" scan above only sees
                // positions touched THIS SAME call, but a piston's own move sequence can span
                // several calls (the vacate happens now, via onNeighborChanged; the matching
                // arrival at the new cell only gets written ticks later, when that cell's own
                // captured BE finalizes via tick() — see PistonMovingBlockEntity). Queuing the drop
                // for a short grace window instead of dropping right away gives that later arrival
                // a chance to cancel it (see cancelPendingDrop's call sites above/below) — found
                // live as a duplication bug: sticky piston retract visibly moved the block AND
                // dropped an item of it, because the immediate same-call check alone could never
                // see the delayed arrival.
                List<ItemStack> drops = removed.definition.drops(removed, realLevel, be.getBlockPos(), be, ItemStack.EMPTY);
                be.deferDrop(previousState, dropPos, drops, realLevel.getGameTime());
            }
        }
        // A cell that WASN'T an existing piece before this call but now holds real state — e.g.
        // water spreading into a previously-empty neighbor, or a falling block relocating, via
        // that logic's own setBlock calls. The diff loop above only ever looks at existing
        // pieces' anchors, so a genuinely new position like this is otherwise silently lost — the
        // write happens in the fake cell map and then the whole map is discarded when this call
        // ends. Turn it into a real piece instead, matching vanilla's actual "setBlock somewhere
        // new = a block now exists there" semantics, generic to whatever spread/moved it there.
        int max = be.getGridSize() - 1;
        for (Map.Entry<BlockPos, BlockState> touched : cells.touchedCells().entrySet()) {
            BlockPos pos = touched.getKey();
            BlockState touchedState = touched.getValue();
            if (touchedState.isAir() && !be.inBounds(pos.getX(), pos.getY(), pos.getZ())) {
                // Gone from the next grid: what ran here was its block's own logic, on a cell of that grid.
                if (be.resolve(pos.getX(), pos.getY(), pos.getZ()) instanceof SubgridBlockEntity.CellRef.InAdjacentGrid a
                        && a.grid().getPieceAt(a.x(), a.y(), a.z()) instanceof PlacedPiece gone
                        && gone.definition instanceof VanillaBlockPiece && gone.anchor.equals(new BlockPos(a.x(), a.y(), a.z()))) {
                    SubgridBlockEntity.whileApplyingSettled(() -> a.grid().removePieceAt(a.x(), a.y(), a.z()));
                    anyChanged = true;
                }
                continue;
            }
            if (touchedState.isAir() || before.containsKey(pos) || be.getPieceAt(pos.getX(), pos.getY(), pos.getZ()) != null) {
                continue;
            }
            boolean withinBounds = pos.getX() >= 0 && pos.getX() <= max
                    && pos.getY() >= 0 && pos.getY() <= max && pos.getZ() >= 0 && pos.getZ() <= max;
            // placePieceCrossBoundary finds/creates an adjacent same-size SubgridBlockEntity when
            // pos overflows past exactly one edge (e.g. water/a falling block/a growing tree
            // crossing this subgrid's boundary), instead of the write just being silently dropped.
            boolean settled = cells.isSettled(pos);
            SubgridBlockEntity.PlacementResult[] placement = new SubgridBlockEntity.PlacementResult[1];
            Runnable place = () -> placement[0] = be.placePieceCrossBoundary(VanillaBlockPiece.INSTANCE, pos, Direction.UP, touchedState, realLevel);
            if (settled) SubgridBlockEntity.whileApplyingSettled(place);
            else place.run();
            SubgridBlockEntity.PlacementResult result = placement[0];
            if (result != null) {
                PlacedPiece placed = result.piece();
                be.cancelPendingDrop(touchedState);
                // result.owner() is the grid the piece actually landed in — placeOrUpdatePiece
                // already stored the correctly-wrapped local coordinate as placed.anchor, so this
                // one call is correct whether the write stayed in be or crossed into a neighbor.
                // Previously the cross-grid case skipped this entirely (captureStarted/captureEnded
                // ran against `be`, never the real owner) — issue #46's live-reported sticky piston
                // retract duplication: hasActiveCaptures() on the piece's ACTUAL grid never saw it
                // as still-animating, so its deferred drop's grace window fired immediately instead
                // of waiting for this exact arrival to cancel it. Also fixes the "won't animate that
                // specific hop client-side" gap this used to accept — now genuinely correct instead
                // of a documented shortcut.
                syncBlockEntity(result.owner(), placed, pos, placed.anchor, cells, (Level) fakeSpace, realLevel);
                // placePiece already calls notifyNeighbors + the new piece's own onNeighborChanged
                // — but NOT onPlace, same gap BlockAccess was added for at the top-level placement
                // flow. A freshly-spread cell needs its own onPlace to schedule ITS next step, or
                // the spread/fall stops after one cell. Only valid to fire against fakeSpace when
                // the piece landed IN THIS grid — a cross-boundary placement lands in a different
                // SubgridBlockEntity, whose position semantics fakeSpace knows nothing about; that
                // piece will still react normally to its own future onNeighborChanged calls, just
                // not this exact tick.
                if (withinBounds && !settled) {
                    BlockAccess.onPlace(touchedState, (Level) fakeSpace, fakeSpace.world(pos), Blocks.AIR.defaultBlockState(), false);
                }
                anyChanged = true;
            }
        }
        if (!fakeSpace.scheduledTicks().isEmpty()) {
            scheduleCaptured(be, fakeSpace, realLevel.getGameTime());
            anyChanged = true;
        }
        if (anyChanged) be.notifyUpdate();
        if (cells instanceof SubgridFakeCellGetter own && own.be == be) own.live = false;
    }

    /**
     * Moves a fake space's captured scheduleTick requests into be's own queue. Vanilla supplied world-frame
     * positions, the queue is keyed by local cell — convert (requests outside the grid are dropped by
     * SubgridBlockEntity#scheduleTick).
     */
    public static void scheduleCaptured(SubgridBlockEntity be, FakeSpace space, long gameTime) {
        for (FakeSpace.ScheduledEntry entry : space.scheduledTicks()) {
            BlockPos local = space.cells().toLocal(entry.pos());
            be.scheduleTick(local.getX(), local.getY(), local.getZ(), gameTime + entry.delay());
        }
    }

    /**
     * A vanilla setBlockEntity/removeBlockEntity call captured this vanilla call at pos (e.g.
     * PistonBaseBlock attaching a fully-parameterized PistonMovingBlockEntity to a freshly-placed
     * MOVING_PISTON piece) — mirror it onto the piece's own runtimeBlockEntity cache so
     * blockEntityFor picks up the real instance next time instead of lazily building a blank one.
     * MovingPistonBlock.newBlockEntity deliberately returns null for exactly this reason: the real
     * instance is only ever handed over via setBlockEntity, never built lazily.
     *
     * A freshly captured PistonMovingBlockEntity also gets broadcast to nearby clients as a
     * PistonAnimationPayload — see that class for why this can't just ride the normal piece-state
     * resync. This only fires once per animation: subsequent server ticks advance progress via the
     * SAME cached BE instance without another setBlockEntity call (PistonMovingBlockEntity.tick
     * mutates its own fields in place), so touchedBlockEntities() is empty on those ticks and this
     * whole method no-ops via the captured == null check below.
     *
     * Two distinct positions, not one (issue #46): fakeSpacePos is whatever key vanilla's own
     * setBlock/setBlockEntity call actually used against the fake cell map — always relative to
     * the ORIGIN grid that built this fake space, even when the piece landed in a different real
     * SubgridBlockEntity (owner) after crossing a boundary. ownerLocalPos is that piece's own
     * correct local coordinate WITHIN owner (already wrapped by placeOrUpdatePiece). Conflating
     * the two used to mean a cross-grid landing either read the wrong fake-space key or announced
     * the wrong local position — previously sidestepped entirely by skipping this method for
     * cross-grid pieces, which is what let their capture bookkeeping (owner.captureStarted/
     * captureEnded) never run at all, defeating the deferred-drop anti-duplication check on
     * whichever grid the piece actually lives in.
     */
    private static void syncBlockEntity(SubgridBlockEntity owner, PlacedPiece piece, BlockPos fakeSpacePos,
                                         BlockPos ownerLocalPos, FakeCellGetter cells, Level fakeLevel, ServerLevel realLevel) {
        if (cells.removedBlockEntityPositions().contains(fakeSpacePos)) {
            piece.runtimeBlockEntity = null;
            // This piece's own captured operation just finished — see SubgridBlockEntity's
            // activeCaptures doc for why any pending drop elsewhere in this subgrid waits on this.
            owner.captureEnded(piece);
            return;
        }
        BlockEntity captured = cells.touchedBlockEntities().get(fakeSpacePos);
        if (captured == null) return;
        captured.setLevel(fakeLevel);
        piece.runtimeBlockEntity = captured;
        PhantomOwners.register(captured, owner, piece);
        // Vanilla just handed us a fully-parameterized BE for an operation it's driving (today
        // only a piston does this) — see SubgridBlockEntity's activeCaptures doc.
        owner.captureStarted(piece);
        if (captured instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity moving) {
            CompoundTag beNbt = moving.saveCustomOnly(realLevel.registryAccess());
            // PistonMovingBlockEntity's constructor requires its OWN outer BlockState (MOVING_PISTON,
            // matching BlockEntityType.PISTON) — saveCustomOnly above doesn't include it (that's the
            // BE's own NBT, not the block state it sits on). Embed piece.runtimeState explicitly
            // (known correct right here, synchronously, on the server) rather than having the client
            // re-derive it from the live piece when the payload is processed — that piece may already
            // have moved on to a DIFFERENT state by then (e.g. a same-tick full resync racing ahead
            // of this payload), which is exactly what threw "Invalid block entity" client-side before
            // this fix: the client tried to build a PistonMovingBlockEntity out of whatever state the
            // piece already held at that later moment, not the MOVING_PISTON state that was true when
            // this BE was actually captured.
            beNbt.put("outerState", NbtUtils.writeBlockState(VanillaBlockPiece.stateOf(piece)));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(realLevel,
                    new net.minecraft.world.level.ChunkPos(owner.getBlockPos()),
                    new com.therappha.tinyblocks.network.PistonAnimationPayload(owner.getBlockPos(), ownerLocalPos, beNbt));
        }
    }

    /**
     * What a real chunk does to the block entity when its block's state changes (LevelChunk#setBlockState): the
     * same block keeps its block entity and hands it the new state; a different block removes it (the old block's
     * onRemove), and the new block gets a fresh one. A fake space's setBlock only records the state, so without
     * this a phantom kept its old state: a tiny redstone link switched to receiver still saw itself a transmitter
     * (Create rebuilds its link from getBlockState), and a tiny shaft encased by a casing kept the shaft's entity.
     * A block entity vanilla code handed over itself this call (a piston's) is syncBlockEntity's business instead.
     */
    private static void followStateChange(SubgridBlockEntity be, PlacedPiece piece, @javax.annotation.Nullable BlockState was,
                                          BlockState now, FakeCellGetter cells) {
        if (cells.touchedBlockEntities().containsKey(piece.anchor) || cells.removedBlockEntityPositions().contains(piece.anchor)) return;
        followStateChange(be, piece, was, now);
    }

    /**
     * The same for a piece whose state is changed from outside its own grid's fake space — a write that crossed
     * into it from a neighbouring grid (SubgridBlockEntity#placePieceCrossBoundary): a Create belt laid from one
     * grid into the next turns the shaft there into a belt, and that piece needs a belt's block entity.
     */
    public static void followStateChange(SubgridBlockEntity be, PlacedPiece piece, @javax.annotation.Nullable BlockState was,
                                         BlockState now) {
        if (!(piece.runtimeBlockEntity instanceof BlockEntity phantom)) return;
        if (was != null && now.is(was.getBlock()) && phantom.getType().isValid(now)) {
            phantom.setBlockState(now);
            return;
        }
        try {
            phantom.setRemoved();
        } catch (RuntimeException e) {
            LOGGER.warn("Removing the block entity of a piece at {} (cell {}) failed", be.getBlockPos(), piece.anchor, e);
        }
        piece.runtimeBlockEntity = null;
        piece.extraData.remove("be"); // the old block's data, not the new one's
    }
}
