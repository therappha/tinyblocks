package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.items.TinyPieceItem;
import com.therappha.tinyblocks.subgrid.GridRay;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeLevel;
import com.therappha.tinyblocks.v2.FakeServerLevel;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ScheduledTick;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class SubgridEventHandler {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof SubgridBlock)) return;
        event.setCanceled(true);

        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        Player player = event.getPlayer();

        HitResult hit = player.pick(5.0, 0f, false);
        if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(pos)) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SubgridBlockEntity subgrid)) return;

        Vec3i cell = GridRay.cellAt(pos, bhr.getLocation(), bhr.getDirection(), subgrid.gridSize);
        PlacedPiece removed = removeAndDrop(subgrid, level, pos, cell, player);
        if (removed == null) return;

        if (subgrid.getPieces().isEmpty()) {
            level.removeBlock(pos, false);
        } else {
            // NeoForge sends a block resync packet after cancel which causes the client
            // to recreate the BE as empty. Schedule a tick 2 ticks out so notifyUpdate()
            // fires AFTER the client has already processed that resync.
            level.getBlockTicks().schedule(new ScheduledTick<>(event.getState().getBlock(), pos, level.getGameTime() + 2, 0L));
        }
    }

    /**
     * Server-authoritative completion of a mine started by SubgridMiningInterceptor — the client
     * decided (mirroring SubgridBlock.pieceDestroyProgress itself) that this piece just finished
     * mining, and suppressed vanilla's own client-local destroy() call to avoid it. No BreakEvent
     * involved here at all, so none of onBlockBreak's post-cancel-resync workaround applies —
     * removeAndDrop -> SubgridBlockEntity#removePieceAt's own delta packet is enough on its own.
     */
    public static void handleMinePiece(com.therappha.tinyblocks.network.SubgridMinePiecePayload payload,
                                        Player player, ServerLevel level) {
        BlockPos pos = payload.pos();
        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity subgrid)) return;

        HitResult hit = player.pick(5.0, 0f, false);
        if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(pos)) return;

        BlockPos cellPos = payload.cell();
        Vec3i cell = new Vec3i(cellPos.getX(), cellPos.getY(), cellPos.getZ());
        if (subgrid.getPieceAt(cell.getX(), cell.getY(), cell.getZ()) == null) return;

        PlacedPiece removed = removeAndDrop(subgrid, level, pos, cell, player);
        if (removed != null && subgrid.getPieces().isEmpty()) {
            level.removeBlock(pos, false);
        }
    }

    /** Removes the piece at cell and spawns its drops (if the tool qualifies and player isn't creative). */
    private static PlacedPiece removeAndDrop(SubgridBlockEntity subgrid, ServerLevel level, BlockPos pos, Vec3i cell, Player player) {
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
            Vec3 dropPos = subgrid.realPositionOf(new BlockPos(cell));
            for (ItemStack stack : drops) {
                level.addFreshEntity(new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, stack));
            }
        }
        return removed;
    }

    /**
     * Phase C debug convenience (v2): holding any real BlockItem and right-clicking an existing
     * SubgridBlock places a real v2.VanillaBlockPiece using vanilla's own getStateForPlacement/
     * canSurvive logic, instead of needing a dedicated debug item per vanilla block. Holding the
     * Minimizer in the offhand additionally lets you right-click any normal block — a new
     * subgrid_block is created there first (mirroring TinyPieceItem.useOn's own else branch),
     * so you don't need an existing subgrid to reference before placing into one.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        // Only a real BlockItem can ever PLACE a new piece; other items (hoe, axe, shovel, ...)
        // can still MODIFY an existing piece via their own useOn — see runItemInteraction below.
        BlockItem blockItem = stack.getItem() instanceof BlockItem bi && !(bi.getBlock() instanceof SubgridBlock)
                ? bi : null;

        BlockPos pos = event.getPos();
        BlockState clickedState = level.getBlockState(pos);
        boolean clickedIsSubgrid = clickedState.getBlock() instanceof SubgridBlock;
        MinimizerItem minimizer = player.getOffhandItem().getItem() instanceof MinimizerItem mi ? mi : null;
        // Any item might interact with or modify a piece on an existing subgrid, or (with the
        // Minimizer in the offhand) start a brand new one — not just BlockItems; a bucket of
        // water should be able to create a fresh subgrid and place water into it the same way a
        // block does, not just modify a subgrid that already exists.
        if (!clickedIsSubgrid && minimizer == null) return;

        BlockHitResult hit = event.getHitVec();
        Direction face = hit.getDirection();

        // Decide whether we're intercepting this click using only checks that resolve
        // identically on both logical sides (read-only, no mutation yet) — needed so we can
        // cancel on the CLIENT too. Cancelling only server-side left the client's own
        // speculative "place the real block" prediction flashing before the correction landed.
        // Whether the clicked cell should be interacted with (vs. placed next to) depends on
        // whether its own onUse actually does anything, which can only be decided
        // authoritatively server-side — so on an existing subgrid we always cancel, and work
        // out interact-vs-place below.
        if (!clickedIsSubgrid) {
            BlockPos targetPos = pos.relative(face);
            BlockState targetState = level.getBlockState(targetPos);
            if (!targetState.isAir() && !(targetState.getBlock() instanceof SubgridBlock)) return;
        }

        event.setCanceled(true);
        // Without this, a cancelled RightClickBlock defaults its result to PASS (see NeoForge's
        // own PlayerInteractEvent.RightClickBlock javadoc: "if result equals PASS, we proceed to
        // RightClickItem" — and RightClickItem's own docs say a non-SUCCESS result there makes the
        // client "continue to other hands"). We DID fully handle this click (a lever flip, a piece
        // interaction, a placement) — telling vanilla it was a PASS instead let it cascade into
        // trying the item's own use, and then the OTHER hand's full interaction sequence too,
        // running this exact handler a second time for one physical click. Found live two separate
        // ways: a lever (or anything else routed through this handler) toggled twice per click
        // whenever the player had anything usable in the other hand — a piston wired to it would
        // extend AND retract within the same tick, interrupting its own animation before it could
        // ever settle; and separately, a water bucket's fallback Item.use() call (Minecraft
        // #startUseItem / ServerPlayerGameMode#useItemOn both try this after a PASSed useOn) ran
        // uncontrolled against the REAL world, placing a real, full-size water block next to/on top
        // of the SubgridBlock instead of ever reaching our own useOn-only handling below.
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(level instanceof ServerLevel serverLevel)) return;

        // --- Everything below is server-authoritative; recompute against live state. ---
        SubgridBlockEntity be;
        int nx, ny, nz;

        if (clickedIsSubgrid) {
            if (!(serverLevel.getBlockEntity(pos) instanceof SubgridBlockEntity existing)) return;
            Vec3i clickedCell = GridRay.cellAt(pos, hit.getLocation(), face, existing.gridSize);

            // Try interacting with whatever's at the exact clicked cell first — same priority
            // vanilla gives "interact with what you clicked" over "place a new block" — but only
            // defer to it if it actually did something (a lever flips); a non-interactive piece
            // (stone, wire) falls through to placement, same as vanilla blocks with no
            // useWithoutItem override do. Sneaking always skips straight to placement.
            PlacedPiece existingPiece = existing.getPieceAt(clickedCell.getX(), clickedCell.getY(), clickedCell.getZ());
            if (existingPiece != null && !player.isShiftKeyDown()) {
                InteractionResult result = existingPiece.definition.onUse(existingPiece, serverLevel, pos, existing, player, hit);
                if (result != InteractionResult.PASS) return;

                // onUse didn't do anything (e.g. dirt has no useWithoutItem) — try the held
                // item's own useOn against the exact clicked cell (hoe tilling, axe stripping,
                // shovel path, etc.), but ONLY for non-BlockItems. A real BlockItem's useOn
                // computes its own internal offset position (e.g. "place next to what I
                // clicked"), which this fallback's diff never checks (it only looks at the
                // clicked cell) — that silently ate the click (and the item) without actually
                // placing anything, since BlockItem placement already has its own correct
                // offset/overflow-aware path just below.
                if (blockItem == null && runItemInteraction(existing, serverLevel, player, event.getHand(), stack, face, hit,
                        clickedCell.getX(), clickedCell.getY(), clickedCell.getZ())) {
                    return;
                }
            } else if (existingPiece == null && blockItem == null) {
                // Clicked an EMPTY cell with a non-BlockItem — e.g. a water/lava bucket. Buckets
                // don't go through BlockItem placement at all (BucketItem isn't a BlockItem), so
                // without this they silently did nothing on an empty subgrid cell. Same generic
                // mechanism as above, but may CREATE a new piece instead of just updating one.
                if (runItemInteraction(existing, serverLevel, player, event.getHand(), stack, face, hit,
                        clickedCell.getX(), clickedCell.getY(), clickedCell.getZ())) {
                    return;
                }
            }

            if (blockItem == null) return;

            // Overflow (if any) continues into the adjacent real-world position, same size as the
            // grid being left — resolveOrCreate creates one if that position is air, matching what
            // notifyNeighbors/realBlockStateAt already require for cross-grid propagation to see it.
            int cx = clickedCell.getX() + face.getStepX();
            int cy = clickedCell.getY() + face.getStepY();
            int cz = clickedCell.getZ() + face.getStepZ();

            SubgridBlockEntity.CellRef ref = existing.resolveOrCreate(cx, cy, cz, serverLevel);
            switch (ref) {
                case SubgridBlockEntity.CellRef.Local l -> { be = existing; nx = l.x(); ny = l.y(); nz = l.z(); }
                case SubgridBlockEntity.CellRef.InAdjacentGrid a -> { be = a.grid(); nx = a.x(); ny = a.y(); nz = a.z(); }
                case SubgridBlockEntity.CellRef.Created c -> { be = c.grid(); nx = c.x(); ny = c.y(); nz = c.z(); }
                default -> { return; }
            }
        } else {
            BlockPos targetPos = pos.relative(face);
            SubgridBlockEntity created = SubgridBlockEntity.createAt(serverLevel, targetPos, minimizer.preferredSubgrid().gridSize);
            if (created == null) return;
            be = created;
            int[] cell = TinyPieceItem.computeGridCell(pos, face, hit.getLocation(), be.gridSize);
            nx = cell[0]; ny = cell[1]; nz = cell[2];
        }

        if (be.getPieceAt(nx, ny, nz) != null) return;

        if (blockItem == null) {
            // Not a BlockItem (bucket, hoe, ...) — no getStateForPlacement/canSurvive path to run,
            // that's BlockItem-specific. Same generic useOn() mechanism as modifying an existing
            // subgrid, just against a freshly created (possibly still empty) one — lets e.g. a
            // water bucket create a brand new subgrid and place water into it in one click,
            // instead of needing an existing piece there first.
            runItemInteraction(be, serverLevel, player, event.getHand(), stack, face, hit, nx, ny, nz);
            return;
        }

        BlockPos fakeAnchor = new BlockPos(nx, ny, nz);
        FakeLevel fakeLevel = VanillaBlockPiece.buildFakeSpace(be, serverLevel);
        BlockHitResult fakeHit = new BlockHitResult(Vec3.atCenterOf(fakeAnchor), face, fakeAnchor, hit.isInside());
        BlockPlaceContext placeCtx = new BlockPlaceContext(
                new UseOnContext(fakeLevel, player, event.getHand(), stack, fakeHit));

        BlockState state = blockItem.getBlock().getStateForPlacement(placeCtx);
        if (state == null || !state.canSurvive(fakeLevel, fakeAnchor)) return;

        PlacedPiece piece = new PlacedPiece(VanillaBlockPiece.INSTANCE, fakeAnchor, face);
        piece.runtimeState = state;
        if (be.placePiece(piece)) {
            // Real placement goes through Level.setBlock, which (inside LevelChunk.setBlockState)
            // unconditionally fires newState.onPlace(...) — that's what e.g. FallingBlock relies
            // on to schedule its very first fall check. be.placePiece writes the piece directly,
            // bypassing setBlock entirely, so nothing ever told a freshly placed piece "you were
            // just placed." Rebuild the fake space (now including this piece) and fire onPlace
            // explicitly so any block relying on it behaves the same as it would in the real world.
            FakeLevel placedFakeLevel = VanillaBlockPiece.buildFakeSpace(be, serverLevel);
            com.therappha.tinyblocks.v2.BlockAccess.onPlace(state, placedFakeLevel, fakeAnchor, Blocks.AIR.defaultBlockState(), false);
            for (com.therappha.tinyblocks.v2.FakeSpace.ScheduledEntry entry : placedFakeLevel.scheduledTicks()) {
                be.scheduleTick(entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(),
                        serverLevel.getGameTime() + entry.delay());
            }
            state.getBlock().setPlacedBy(fakeLevel, fakeAnchor, state, player, stack);
            // A door's setPlacedBy writes ITS OWN second block (the upper half, via its own
            // internal level.setBlock(pos.above(), state.setValue(HALF, UPPER), ...) call) — same
            // "untracked write" pattern VanillaBlockPiece.applyChanges already generalizes for
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
                if (be.placePieceCrossBoundary(VanillaBlockPiece.INSTANCE, touchedPos, face, touched.getValue(), serverLevel) != null) {
                    com.therappha.tinyblocks.v2.BlockAccess.onPlace(touched.getValue(), fakeLevel, touchedPos, Blocks.AIR.defaultBlockState(), false);
                }
            }
            if (!player.isCreative()) stack.shrink(1);
            // Same NeoForge post-cancel resync race onBlockBreak/onLeftClickBlock already work
            // around: an immediate sendBlockUpdated wasn't enough on a freshly-created
            // SubgridBlock, so also schedule a delayed re-notify to land after that resync.
            BlockPos subgridPos = be.getBlockPos();
            serverLevel.sendBlockUpdated(subgridPos, serverLevel.getBlockState(subgridPos), serverLevel.getBlockState(subgridPos), Block.UPDATE_CLIENTS);
            serverLevel.getBlockTicks().schedule(new ScheduledTick<>(serverLevel.getBlockState(subgridPos).getBlock(), subgridPos, serverLevel.getGameTime() + 2, 0L));
        }
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
     * mechanism VanillaBlockPiece.applyChanges and the placement flow above already use, so this
     * composes correctly whether the write lands on the clicked cell or an offset one, and
     * crosses into an adjacent subgrid via placePieceCrossBoundary if it overflows this one.
     * Returns true if the click was consumed (stop further fallthrough to placement), regardless
     * of whether state changed.
     */
    private static boolean runItemInteraction(SubgridBlockEntity be, ServerLevel serverLevel, Player player,
                                               InteractionHand hand, ItemStack stack, Direction face,
                                               BlockHitResult realHit, int x, int y, int z) {
        BlockPos fakeAnchor = new BlockPos(x, y, z);
        // A genuine ServerLevel, not just Level — some items' useOn() needs one internally (e.g.
        // BonemealableBlock.performBonemeal), same reason scheduled/random ticks need one.
        FakeServerLevel fakeLevel = VanillaBlockPiece.buildFakeServerSpace(be, serverLevel);
        java.util.Map<BlockPos, BlockState> before = new java.util.HashMap<>(fakeLevel.cells().touchedCells());

        BlockHitResult fakeHit = new BlockHitResult(Vec3.atCenterOf(fakeAnchor), face, fakeAnchor, realHit.isInside());
        InteractionResult result = stack.useOn(new UseOnContext(fakeLevel, player, hand, stack, fakeHit));

        // #TODO remove before release (or fold into a permanent comment) once verified live —
        // water-placement investigation.
        //
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
                    && dispensible.emptyContents(player, fakeLevel, fakeAnchor, fakeHit)) {
                dispensible.checkExtraContent(player, fakeLevel, stack, fakeAnchor);
                ItemStack emptied = net.minecraft.world.item.ItemUtils.createFilledResult(
                        stack, player, net.minecraft.world.item.BucketItem.getEmptySuccessItem(stack, player));
                player.setItemInHand(hand, emptied);
                result = InteractionResult.SUCCESS;
            } else {
                BlockState currentState = fakeLevel.getBlockState(fakeAnchor);
                if (currentState.getBlock() instanceof net.minecraft.world.level.block.BucketPickup bucketPickup) {
                    ItemStack picked = bucketPickup.pickupBlock(player, fakeLevel, fakeAnchor, currentState);
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
            if (piece != null && piece.definition == VanillaBlockPiece.INSTANCE) {
                if (touchedState.isAir()) {
                    // #TODO remove before release — water-placement investigation. A piece that
                    // became air here (e.g. BucketPickup emptying a water piece back into a
                    // bucket) needs the same real removal applyChanges already does for tick-
                    // driven self-destruction — just overwriting runtimeState with air would leave
                    // a ghost piece still occupying (and blocking) the cell.
                    removeAndDrop(be, serverLevel, be.getBlockPos(),
                            new Vec3i(touchedPos.getX(), touchedPos.getY(), touchedPos.getZ()), player);
                } else {
                    piece.runtimeState = touchedState;
                    be.notifyNeighbors(piece);
                    be.notifyUpdate();
                }
            } else if (piece == null && (wasState == null || wasState.isAir()) && !touchedState.isAir()) {
                PlacedPiece placed = be.placePieceCrossBoundary(VanillaBlockPiece.INSTANCE, touchedPos, face, touchedState, serverLevel);
                if (placed != null) {
                    com.therappha.tinyblocks.v2.BlockAccess.onPlace(touchedState, fakeLevel, touchedPos, Blocks.AIR.defaultBlockState(), false);
                }
            }
        }
        // #TODO remove before release — water-placement investigation. Mirrors onBlockBreak/
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
        for (com.therappha.tinyblocks.v2.FakeSpace.ScheduledEntry entry : fakeLevel.scheduledTicks()) {
            be.scheduleTick(entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(),
                    serverLevel.getGameTime() + entry.delay());
        }
        return result != InteractionResult.PASS;
    }

    /**
     * Phase C debug convenience (v2): holding a real BlockItem and left-clicking a piece inside
     * a SubgridBlock removes it instantly — skips destroyTime()/mining-progress entirely, for
     * fast placement/removal iteration while testing. The real hardness path (mining without a
     * block item in hand) is unaffected.
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof SubgridBlock)) return;

        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof BlockItem)) return;
        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) return;

        HitResult hit = player.pick(5.0, 0f, false);
        if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(pos)) return;

        Vec3i cell = GridRay.cellAt(pos, bhr.getLocation(), bhr.getDirection(), be.gridSize);
        PlacedPiece removed = be.removePieceAt(cell.getX(), cell.getY(), cell.getZ());
        if (removed == null) return;

        event.setCanceled(true);
        if (!player.isCreative()) {
            List<ItemStack> drops = removed.definition.drops(removed, level, pos, be, player.getMainHandItem());
            Vec3 dropPos = be.realPositionOf(new BlockPos(cell));
            for (ItemStack drop : drops) {
                level.addFreshEntity(new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, drop));
            }
        }
        if (be.getPieces().isEmpty()) {
            level.removeBlock(pos, false);
        } else {
            // Same NeoForge post-cancel resync race onBlockBreak already works around: schedule
            // a tick 2 ticks out so notifyUpdate() fires AFTER the client has processed that
            // resync, instead of being stomped by it.
            level.getBlockTicks().schedule(new ScheduledTick<>(level.getBlockState(pos).getBlock(), pos, level.getGameTime() + 2, 0L));
        }
    }
}
