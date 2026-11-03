package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeLevel;
import com.therappha.tinyblocks.v2.FakeServerLevel;
import com.therappha.tinyblocks.v2.GridWriteBack;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ScheduledTick;

import java.util.List;

/** A block item placed into a cell, an item used on a cell, and a piece broken with its drops. */
final class PiecePlacement {

    private PiecePlacement() {}

    /** Removes the piece at cell and spawns its drops (if the tool qualifies and player isn't creative). */
    static PlacedPiece removeAndDrop(SubgridBlockEntity subgrid, ServerLevel level, BlockPos pos, Vec3i cell, Player player) {
        PlacedPiece removed = subgrid.removePieceAt(cell.getX(), cell.getY(), cell.getZ());
        if (removed == null) return null;

        BlockState renderState = removed.definition.renderState(removed);
        // Both real removal paths (the flicker-free mining-completion path and the debug-fast-
        // removal fallback) go through here, so this one call covers piece breaking generically —
        // same volume/pitch formula vanilla's own Level.destroyBlock uses for a block's own sound
        // type, played at the subgrid's real position (pieces don't have one of their own).
        var soundType = renderState.getSoundType();
        level.playSound(null, subgrid.getBlockPos(), soundType.getBreakSound(), SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        boolean correctTool = !removed.definition.requiresCorrectTool()
                || player.hasCorrectToolForDrops(renderState);
        if (correctTool && !player.isCreative()) {
            List<ItemStack> drops = removed.definition.drops(removed, level, pos, subgrid, player.getMainHandItem());
            dropAt(level, subgrid.realPositionOf(new BlockPos(cell)), drops);
        }
        return removed;
    }

    /**
     * A block item into the empty cell (nx, ny, nz) of {@code be}, as vanilla places it: its placement state against
     * the clicked face, canSurvive, setPlacedBy, and any further block it writes (a door's upper half).
     */
    static void placeBlockItem(SubgridBlockEntity be, ServerLevel serverLevel, Player player, InteractionHand hand,
                               ItemStack stack, BlockItem blockItem, Direction face, BlockHitResult hit,
                               int nx, int ny, int nz) {
        BlockPos fakeAnchor = new BlockPos(nx, ny, nz); // local cell
        FakeLevel fakeLevel = VanillaBlockPiece.buildFakeSpace(be, serverLevel);
        BlockPos at = fakeLevel.world(fakeAnchor); // what vanilla code is handed (the grid's virtual world frame)
        // Handed to vanilla as a click on the cell behind the clicked face, at the spot that was hit, as a real
        // placement would be: a wall torch, a button or a lever attaches to the face (not the ground), a slab or
        // stairs takes its half from where the face was hit.
        BlockHitResult fakeHit = new BlockHitResult(clickOnCell(at, face, hit.getLocation(), be.gridSize), face,
                at.relative(face.getOpposite()), hit.isInside());
        BlockPlaceContext placeCtx = new BlockPlaceContext(fakeLevel, player, hand, stack, fakeHit) {{
            // The clicked cell is never replaced: the piece goes into the empty cell at, even when the clicked
            // cell reads as replaceable (an empty cell past the grid's edge).
            replaceClicked = false;
        }};

        // The item's placement state, not just the block's: the item picks between two blocks (a torch or a wall
        // torch, a sign or a wall sign) by the clicked face.
        BlockState state = ((com.therappha.tinyblocks.mixin.BlockItemAccessor) blockItem).tinyblocks$getPlacementState(placeCtx);
        if (state == null || !state.canSurvive(fakeLevel, at)) return;

        PlacedPiece piece = new PlacedPiece(VanillaBlockPiece.INSTANCE, fakeAnchor, face);
        piece.runtimeState = state;
        if (be.placePiece(piece)) {
            fakeLevel.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_PLACE, at,
                    net.minecraft.world.level.gameevent.GameEvent.Context.of(player, state));
            state.getBlock().setPlacedBy(fakeLevel, at, state, player, stack);
            // A door's setPlacedBy writes ITS OWN second block (the upper half, via its own
            // internal level.setBlock(pos.above(), state.setValue(HALF, UPPER), ...) call) — same
            // "untracked write" pattern GridWriteBack.applyChanges already generalizes for
            // fluids/falling blocks, but this placement flow doesn't go through applyChanges at
            // all. Reuse the same scan here so any such second (or third, etc.) write becomes a
            // real piece too — this is what actually makes a placed door end up as two pieces
            // (lower+upper) instead of just the one this function explicitly constructed above.
            for (java.util.Map.Entry<BlockPos, BlockState> touched : fakeLevel.cells().touchedCells().entrySet()) {
                BlockPos touchedPos = touched.getKey();
                if (touchedPos.equals(fakeAnchor) || touched.getValue().isAir()
                        || be.getPieceAt(touchedPos.getX(), touchedPos.getY(), touchedPos.getZ()) != null) {
                    continue;
                }
                be.placePieceCrossBoundary(VanillaBlockPiece.INSTANCE, touchedPos, face, touched.getValue(), serverLevel);
            }
            if (!player.isCreative()) stack.shrink(1);
            // Same NeoForge post-cancel resync race onBlockBreak/onLeftClickBlock already work
            // around: an immediate sendBlockUpdated wasn't enough on a freshly-created
            // SubgridBlock, so also schedule a delayed re-notify to land after that resync.
            BlockPos subgridPos = be.getBlockPos();
            serverLevel.sendBlockUpdated(subgridPos, serverLevel.getBlockState(subgridPos), serverLevel.getBlockState(subgridPos), Block.UPDATE_CLIENTS);
            resyncLater(serverLevel, subgridPos, serverLevel.getBlockState(subgridPos).getBlock());
        }
    }
    }

    /**
     * The real hit, moved onto the face of the cell at (in the grid's virtual world frame) it was placed against.
     * Cells are 1/gridSize of a block and every grid sits on whole blocks, so the hit's place within a cell is the
     * fractional part of its position times gridSize; along the face's axis it lies on the side of at facing the
     * clicked cell.
     */
    static Vec3 clickOnCell(BlockPos at, Direction face, Vec3 hit, int gridSize) {
        double[] in = new double[3];
        for (Direction.Axis axis : Direction.Axis.values()) {
            double scaled = hit.get(axis) * gridSize;
            in[axis.ordinal()] = axis == face.getAxis()
                    ? (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 1)
                    : scaled - Math.floor(scaled);
        }
        return new Vec3(at.getX() + in[0], at.getY() + in[1], at.getZ() + in[2]);
    }

    /**
     * Runs the held item's own useOn (hoe tilling dirt into farmland, axe stripping logs, shovel
     * making a path, a bucket placing water/lava, etc.) against a cell — the same generic
     * mechanism BlockItem placement already goes through, extended to any item.
     *
     * IMPORTANT: the write doesn't necessarily land on (x,y,z), the cell that was actually
     * clicked. BucketItem.useOn (like BlockItem placement) computes its own internal offset —
     * it places at the clicked position ONLY if that cell is replaceable; otherwise it places one
     * cell over in the direction of the clicked face (the natural way to "place water next to
     * this block"). Checking only (x,y,z)'s before/after silently ate clicks that landed on an
     * occupied cell — the actual write went to the neighbor cell and was never noticed. Scans
     * every position the fake space actually saw written instead, same touchedCells()-based
     * mechanism GridWriteBack.applyChanges and the placement flow above already use, so this
     * composes correctly whether the write lands on the clicked cell or an offset one, and
     * crosses into an adjacent subgrid via placePieceCrossBoundary if it overflows this one.
     * Returns true if the click was consumed (stop further fallthrough to placement), regardless
     * of whether state changed.
     */
    static boolean runItemInteraction(SubgridBlockEntity be, ServerLevel serverLevel, Player player,
                                       InteractionHand hand, ItemStack stack, Direction face,
                                       BlockHitResult realHit, int x, int y, int z) {
        BlockPos fakeAnchor = new BlockPos(x, y, z); // local cell
        // A genuine ServerLevel, not just Level — some items' useOn() needs one internally (e.g.
        // BonemealableBlock.performBonemeal), same reason scheduled/random ticks need one.
        FakeServerLevel fakeLevel = VanillaBlockPiece.buildFakeServerSpace(be, serverLevel);
        java.util.Map<BlockPos, BlockState> before = VanillaBlockPiece.snapshotStates(be); // the fake space no longer holds every piece
        BlockPos at = fakeLevel.world(fakeAnchor); // what vanilla code is handed (the grid's virtual world frame)

        BlockHitResult fakeHit = new BlockHitResult(Vec3.atCenterOf(at), face, at, realHit.isInside());
        InteractionResult result = stack.useOn(new UseOnContext(fakeLevel, player, hand, stack, fakeHit));

        // Vanilla dispatches useOn first, then falls back to a SEPARATE Item#use(Level, Player,
        // InteractionHand) call if useOn PASSes (Minecraft#startUseItem / ServerPlayerGameMode
        // #useItemOn). BucketItem — like any DispensibleContainerItem/BucketPickup — only
        // overrides use(), never useOn(), so the stack.useOn(...) call above is always a no-op
        // PASS for it. use() itself would be no help either: it computes its OWN internal
        // player-eye-position raycast (BucketItem#getPlayerPOVHitResult), which can't resolve
        // against this fake, small-integer local-grid coordinate space — the player's real eye
        // position has nothing to do with cell coordinates 0..gridSize. DispensibleContainerItem
        // (emptying) and BucketPickup (filling) are the same POSITION-based hooks vanilla's own
        // dispenser code uses instead of a raycast — call them directly with the fakeAnchor/fakeHit
        // already computed above, generic to any item implementing them, not bucket-specific.
        if (result == InteractionResult.PASS) {
            if (stack.getItem() instanceof net.minecraft.world.item.DispensibleContainerItem dispensible
                    && dispensible.emptyContents(player, fakeLevel, at, fakeHit)) {
                dispensible.checkExtraContent(player, fakeLevel, stack, at);
                ItemStack emptied = net.minecraft.world.item.ItemUtils.createFilledResult(
                        stack, player, net.minecraft.world.item.BucketItem.getEmptySuccessItem(stack, player));
                player.setItemInHand(hand, emptied);
                result = InteractionResult.SUCCESS;
            } else {
                BlockState currentState = fakeLevel.getBlockState(at);
                if (currentState.getBlock() instanceof net.minecraft.world.level.block.BucketPickup bucketPickup) {
                    ItemStack picked = bucketPickup.pickupBlock(player, fakeLevel, at, currentState);
                    if (!picked.isEmpty()) {
                        ItemStack filled = net.minecraft.world.item.ItemUtils.createFilledResult(stack, player, picked);
                        player.setItemInHand(hand, filled);
                        result = InteractionResult.SUCCESS;
                    }
                }
            }
        }

        for (java.util.Map.Entry<BlockPos, BlockState> touched : fakeLevel.cells().touchedCells().entrySet()) {
            BlockPos touchedPos = touched.getKey();
            BlockState touchedState = touched.getValue();
            BlockState wasState = before.get(touchedPos);
            if (touchedState.equals(wasState)) continue;

            PlacedPiece piece = be.getPieceAt(touchedPos.getX(), touchedPos.getY(), touchedPos.getZ());
            if (piece != null && piece.definition instanceof VanillaBlockPiece) {
                if (touchedState.isAir()) {
                    // A piece that
                    // became air here (e.g. BucketPickup emptying a water piece back into a
                    // bucket) needs the same real removal applyChanges already does for tick-
                    // driven self-destruction — just overwriting runtimeState with air would leave
                    // a ghost piece still occupying (and blocking) the cell.
                    removeAndDrop(be, serverLevel, be.getBlockPos(),
                            new Vec3i(touchedPos.getX(), touchedPos.getY(), touchedPos.getZ()), player);
                } else {
                    piece.runtimeState = touchedState;
                    // A wrench switching a tiny redstone link: its block entity has to see the new state too.
                    GridWriteBack.followStateChange(be, piece, wasState, touchedState);
                    if (!fakeLevel.cells().isSettled(touchedPos)) be.notifyNeighbors(piece);
                    be.notifyUpdate();
                }
            } else if (piece == null && (wasState == null || wasState.isAir()) && !touchedState.isAir()) {
                be.placePieceCrossBoundary(VanillaBlockPiece.INSTANCE, touchedPos, face, touchedState, serverLevel);
            }
        }
        // Mirrors onBlockBreak/
        // handleMinePiece's own cleanup: removeAndDrop above (BucketPickup emptying the last
        // piece in this subgrid) leaves an empty-but-still-present SubgridBlock behind otherwise
        // — every other removal path already deletes it once nothing's left.
        if (be.getPieces().isEmpty()) {
            serverLevel.removeBlock(be.getBlockPos(), false);
        }
        // Whatever ran above (existing-piece update, new-piece placement, or neither) may have
        // scheduled a tick — e.g. LiquidBlock.onPlace scheduling water's first spread step, via
        // the onPlace call just above. Same drain FakeSpace callers always do after touching a
        // fake space; skipped here previously since this function never called applyChanges.
        GridWriteBack.scheduleCaptured(be, fakeLevel, serverLevel.getGameTime());
        return result != InteractionResult.PASS;
    }

    /**
     * NeoForge sends a block resync after a cancelled click or break, which makes the client rebuild the grid's block
     * entity empty: a tick two ticks out re-sends the grid after that resync has landed.
     */
    static void resyncLater(ServerLevel level, BlockPos pos, Block block) {
        level.getBlockTicks().schedule(new ScheduledTick<>(block, pos, level.getGameTime() + 2, 0L));
    }

    /** Spawns {@code stacks} as item entities at {@code at}. */
    static void dropAt(ServerLevel level, Vec3 at, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, stack));
        }
    }
}
