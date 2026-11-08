package com.therappha.tinyblocks.setup;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.compat.create.ConnectorPlacement;
import com.therappha.tinyblocks.compat.create.ShaftConnectorBlock;
import com.therappha.tinyblocks.compat.create.ShaftConnectorPiece;
import com.therappha.tinyblocks.items.BuildMode;
import com.therappha.tinyblocks.items.GridSaveItem;
import com.therappha.tinyblocks.items.MinimizerBuild;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.items.TinyPieceItem;
import com.therappha.tinyblocks.network.SubgridMinePiecePayload;
import com.therappha.tinyblocks.subgrid.GridRay;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class SubgridEventHandler {

    /** A locked Minimizer in hand (MinimizerItem#mayBreak): no mining starts on what it keeps, on either side. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickLocked(PlayerInteractEvent.LeftClickBlock event) {
        if (!MinimizerItem.mayBreak(event.getEntity(), event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // Only a question (may the player take the whole grid?), for the protections to answer.
        if (GridSaveItem.isAskingToBreak()) return;
        if (!MinimizerItem.mayBreak(event.getPlayer(), event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getState().getBlock() instanceof SubgridBlock)) return;
        event.setCanceled(true);

        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        Player player = event.getPlayer();
        // A real player's client mines a piece itself and has the server remove exactly that cell
        // (SubgridMiningInterceptor -> handleMinePiece). Vanilla still sends its own "destroy the block" action
        // on the same click, which lands here: re-tracing the crosshair now — after the mined piece is already
        // gone — hits the piece behind it, and one click broke two pieces. For a real player this only stops
        // the whole SubgridBlock from going; machines breaking blocks (fake players) have no client and still
        // remove the piece under their aim here.
        if (!(player instanceof FakePlayer)) {
            PiecePlacement.resyncLater(level, pos, event.getState().getBlock());
            return;
        }

        HitResult hit = player.pick(5.0, 0f, false);
        if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(pos)) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SubgridBlockEntity subgrid)) return;

        Vec3i cell = GridRay.cellAt(pos, bhr.getLocation(), bhr.getDirection(), subgrid.gridSize);
        PlacedPiece removed = PiecePlacement.removeAndDrop(subgrid, level, pos, cell, player);
        if (removed == null) return;

        if (subgrid.getPieces().isEmpty()) {
            level.removeBlock(pos, false);
        } else {
            // NeoForge sends a block resync packet after cancel which causes the client
            // to recreate the BE as empty. Schedule a tick 2 ticks out so notifyUpdate()
            // fires AFTER the client has already processed that resync.
            PiecePlacement.resyncLater(level, pos, event.getState().getBlock());
        }
    }

    /**
     * Server-authoritative completion of a mine started by SubgridMiningInterceptor — the client
     * decided (mirroring SubgridBlock.pieceDestroyProgress itself) that this piece just finished
     * mining, and suppressed vanilla's own client-local destroy() call to avoid it. No BreakEvent
     * involved here at all, so none of onBlockBreak's post-cancel-resync workaround applies —
     * PiecePlacement.removeAndDrop -> SubgridBlockEntity#removePieceAt's own delta packet is enough on its own.
     */
    public static void handleMinePiece(SubgridMinePiecePayload payload,
                                        Player player, ServerLevel level) {
        BlockPos pos = payload.pos();
        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity subgrid)) return;
        if (!MinimizerItem.mayBreak(player, level, pos)) return;

        HitResult hit = player.pick(5.0, 0f, false);
        if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(pos)) return;

        BlockPos cellPos = payload.cell();
        Vec3i cell = new Vec3i(cellPos.getX(), cellPos.getY(), cellPos.getZ());
        if (subgrid.getPieceAt(cell.getX(), cell.getY(), cell.getZ()) == null) return;

        PlacedPiece removed = PiecePlacement.removeAndDrop(subgrid, level, pos, cell, player);
        if (removed != null && subgrid.getPieces().isEmpty()) {
            level.removeBlock(pos, false);
        }
    }

    /** How many grids in a row a whole block placed against one goes past. */
    private static final int MAX_GRIDS_PASSED = 64;

    /**
     * Holding any real BlockItem and right-clicking an existing
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
        // A click PieceClickDispatch re-posts inside a piece's fake space is for the piece's own listeners.
        if (level instanceof FakeSpace) return;

        // A Minimizer in Destroy mode breaks the clicked grid, whatever is in the main hand, or nothing.
        if (event.getHand() == InteractionHand.MAIN_HAND
                && MinimizerItem.isOn(player.getOffhandItem())
                && MinimizerBuild.mode(player.getOffhandItem()) == BuildMode.DESTROY
                && level.getBlockEntity(event.getPos()) instanceof SubgridBlockEntity grid) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (level instanceof ServerLevel serverLevel) MinimizerShapes.destroy(serverLevel, player, grid);
            return;
        }

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        // A Grid Save takes or places the whole grid (its onItemUseFirst), not a piece in it.
        if (stack.getItem() instanceof GridSaveItem) return;
        // Only a real BlockItem can ever PLACE a new piece; other items (hoe, axe, shovel, ...)
        // can still MODIFY an existing piece via their own useOn — see runItemInteraction below.
        BlockItem blockItem = stack.getItem() instanceof BlockItem bi && !(bi.getBlock() instanceof SubgridBlock)
                ? bi : null;

        BlockPos pos = event.getPos();
        BlockState clickedState = level.getBlockState(pos);
        boolean clickedIsSubgrid = clickedState.getBlock() instanceof SubgridBlock;
        // A Minimizer switched off: a grid is a block like any other, and a whole block goes against it, past any
        // more grids that way, on the first free spot.
        if (clickedIsSubgrid && blockItem != null && event.getHand() == InteractionHand.MAIN_HAND && MinimizerItem.isOff(player.getOffhandItem())) {
            BlockHitResult hit = event.getHitVec();
            BlockPos against = pos;
            for (int i = 0; i < MAX_GRIDS_PASSED && level.getBlockState(against.relative(hit.getDirection())).getBlock() instanceof SubgridBlock; i++) {
                against = against.relative(hit.getDirection());
            }
            event.setCanceled(true);
            event.setCancellationResult(blockItem.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(hit.getLocation(), hit.getDirection(), against, false))));
            return;
        }
        MinimizerItem minimizer = MinimizerItem.isOn(player.getOffhandItem()) && player.getOffhandItem().getItem() instanceof MinimizerItem mi ? mi : null;
        // A Minimizer in a shape mode builds between two corners instead of placing one piece — of any size, the
        // normal size (1/1) included. Taken whatever was clicked, so a shape click never places a block of its own.
        if (minimizer != null && blockItem != null
                && MinimizerBuild.mode(player.getOffhandItem()) != BuildMode.PLACE) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (level instanceof ServerLevel serverLevel) {
                MinimizerShapes.click(serverLevel, player, player.getOffhandItem(), minimizer, blockItem, stack, event.getHitVec());
            }
            return;
        }
        // At the normal size the Minimizer places normal blocks: it stays out of the way, even on a piece.
        if (minimizer != null && minimizer.gridSize(player.getOffhandItem()) == MinimizerItem.NORMAL_SIZE) return;
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

            // A connector's wide end meets what is outside: without a Minimizer, a block clicked onto it is a normal
            // block out there. SubgridPlacementPreview shows it.
            if (minimizer == null && blockItem != null
                    && ConnectorPlacement.goesOutside(existing, blockItem.getBlock(), hit)) {
                ConnectorPlacement.placeOutside(serverLevel, player, event.getHand(), stack, blockItem, hit);
                return;
            }

            if (blockItem != null && blockItem.getBlock() instanceof ShaftConnectorBlock) {
                // Before the clicked piece's own use: a tiny shaft would otherwise run Create's shaft placement
                // helper and extend itself with a one-cell connector. Clicking a shaft puts the plate at the end
                // of the shaft's own axis, so it meets the shaft.
                PlacedPiece clickedPiece = existing.getPieceAt(clickedCell.getX(), clickedCell.getY(), clickedCell.getZ());
                if (ConnectorPlacement.placeFrom(existing, serverLevel, clickedPiece,
                        face.getAxis(), new BlockPos(clickedCell)) && !player.isCreative()) {
                    stack.shrink(1);
                }
                return;
            }

            PlacedPiece clickedPlate = existing.getPieceAt(clickedCell.getX(), clickedCell.getY(), clickedCell.getZ());
            if (blockItem != null && clickedPlate != null
                    && ShaftConnectorPiece.faceOf(clickedPlate) != null) {
                // A block clicked onto a connector: into a funnel's socket, or a small cogwheel onto a gear's side.
                var target = ConnectorPlacement.targetOn(existing, clickedPlate,
                        blockItem.getBlock(), hit.getLocation());
                boolean gear = ShaftConnectorPiece.isGear(existing.getGridSize());
                if (target != null || !gear) {
                    if (target != null && ConnectorPlacement.placeAt(existing, serverLevel,
                            target, clickedPlate.facing) && !player.isCreative()) {
                        stack.shrink(1);
                    }
                    return;
                }
                // Anything else clicked onto a gear goes next to the clicked face, as onto any piece.
            }

            if (stack.getItem() instanceof WrenchItem && !player.isShiftKeyDown()
                    && clickedPlate != null && ShaftConnectorPiece.faceOf(clickedPlate) != null) {
                // Not the shaft it stands in as: a wrench would turn that across the face, out of line with it.
                if (ConnectorPlacement.moveToNextFace(existing, serverLevel, clickedPlate)) {
                    IWrenchable.playRotateSound(serverLevel, pos);
                }
                return;
            }

            // Try interacting with whatever's at the exact clicked cell first — same priority
            // vanilla gives "interact with what you clicked" over "place a new block" — but only
            // defer to it if it actually did something (a lever flips); a non-interactive piece
            // (stone, wire) falls through to placement, same as vanilla blocks with no
            // useWithoutItem override do. Sneaking skips the piece's own use (see the item-only branch below).
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
                if (blockItem == null && PiecePlacement.runItemInteraction(existing, serverLevel, player, event.getHand(), stack, face, hit,
                        clickedCell.getX(), clickedCell.getY(), clickedCell.getZ())) {
                    return;
                }
            } else if (existingPiece != null && blockItem == null) {
                // Sneaking with an item skips the block's own use, as in vanilla, but not the item's: a wrench
                // still dismantles, for instance.
                if (PiecePlacement.runItemInteraction(existing, serverLevel, player, event.getHand(), stack, face, hit,
                        clickedCell.getX(), clickedCell.getY(), clickedCell.getZ())) {
                    return;
                }
            } else if (existingPiece == null && blockItem == null) {
                // Clicked an EMPTY cell with a non-BlockItem — e.g. a water/lava bucket. Buckets
                // don't go through BlockItem placement at all (BucketItem isn't a BlockItem), so
                // without this they silently did nothing on an empty subgrid cell. Same generic
                // mechanism as above, but may CREATE a new piece instead of just updating one.
                if (PiecePlacement.runItemInteraction(existing, serverLevel, player, event.getHand(), stack, face, hit,
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
            SubgridBlockEntity created = SubgridBlockEntity.createAt(serverLevel, targetPos, minimizer.gridSize(player.getOffhandItem()));
            if (created == null) return;
            be = created;
            int[] cell = TinyPieceItem.computeGridCell(pos, face, hit.getLocation(), be.gridSize);
            nx = cell[0]; ny = cell[1]; nz = cell[2];
        }

        if (blockItem != null && blockItem.getBlock() instanceof ShaftConnectorBlock) {
            // Not a one-cell piece: a plate centred on the grid face nearest the target cell, along the clicked axis.
            if (ConnectorPlacement.place(be, serverLevel, face.getAxis(), new BlockPos(nx, ny, nz))
                    && !player.isCreative()) {
                stack.shrink(1);
            }
            return;
        }

        PlacedPiece occupant = be.getPieceAt(nx, ny, nz);
        // A block goes into a cell whose piece is replaceable (water, tall grass), as into such a block in a chunk.
        if (occupant != null && (blockItem == null || !PiecePlacement.replaceable(occupant, blockItem))) return;

        if (blockItem == null) {
            // Not a BlockItem (bucket, hoe, ...) — no getStateForPlacement/canSurvive path to run,
            // that's BlockItem-specific. Same generic useOn() mechanism as modifying an existing
            // subgrid, just against a freshly created (possibly still empty) one — lets e.g. a
            // water bucket create a brand new subgrid and place water into it in one click,
            // instead of needing an existing piece there first.
            PiecePlacement.runItemInteraction(be, serverLevel, player, event.getHand(), stack, face, hit, nx, ny, nz);
            return;
        }

        PiecePlacement.placeBlockItem(be, serverLevel, player, event.getHand(), stack, blockItem, face, hit, nx, ny, nz);
    }

    /**
     * Debug convenience: holding a real BlockItem and left-clicking a piece inside
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
        // In creative the client already mines the clicked piece (SubgridMiningInterceptor); removing here as
        // well took the piece behind it on the same click.
        if (player.isCreative()) return;
        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) return;

        HitResult hit = player.pick(5.0, 0f, false);
        if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(pos)) return;

        Vec3i cell = GridRay.cellAt(pos, bhr.getLocation(), bhr.getDirection(), be.gridSize);
        // A piece that breaks instantly is the client's to mine on the click (SubgridMiningInterceptor), and
        // holding the button sends this event every tick: removing here too took the pieces behind it.
        if (be.getPieceAt(cell.getX(), cell.getY(), cell.getZ()) != null
                && SubgridBlock.pieceDestroyProgress(be, cell.getX(), cell.getY(), cell.getZ(), player) >= 1f) {
            return;
        }
        PlacedPiece removed = be.removePieceAt(cell.getX(), cell.getY(), cell.getZ());
        if (removed == null) return;

        event.setCanceled(true);
        if (!player.isCreative()) {
            List<ItemStack> drops = removed.definition.drops(removed, level, pos, be, player.getMainHandItem());
            PiecePlacement.dropAt(level, be.realPositionOf(new BlockPos(cell)), drops);
        }
        if (be.getPieces().isEmpty()) {
            level.removeBlock(pos, false);
        } else {
            // Same NeoForge post-cancel resync race onBlockBreak already works around: schedule
            // a tick 2 ticks out so notifyUpdate() fires AFTER the client has processed that
            // resync, instead of being stomped by it.
            PiecePlacement.resyncLater(level, pos, level.getBlockState(pos).getBlock());
        }
    }
}
