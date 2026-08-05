package com.therappha.tinyblocks.client;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.network.SubgridMinePiecePayload;
import com.therappha.tinyblocks.subgrid.GridRay;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Vanilla mining is client-predicted: once accumulated destroy progress reaches 1.0, the CLIENT
 * itself removes the block locally (MultiPlayerGameMode#continueDestroyBlock -> destroyBlock(),
 * confirmed by decompiling 1.21.1) before the server ever sees a completion signal. Our
 * BlockEvent.BreakEvent handler cancels the server-side destroy since only one piece should go,
 * not the whole SubgridBlock — but the client already tore down its BlockEntity via that local
 * prediction, which is what actually causes the "everything flickers" symptom.
 *
 * IMPORTANT, found via live debugging: vanilla's own destroyProgress accumulator lives in
 * MultiPlayerGameMode and is keyed by BLOCK POSITION only (sameDestroyTarget), not by which piece
 * is targeted. It only ever resets to 0 inside the branch that runs when nothing cancels that
 * tick's completion (continueDestroyBlock's `if (destroyProgress >= 1.0F) { ...; destroyProgress
 * = 0.0F; }`) — cancelling via event.setUseItem(FALSE) returns BEFORE that reset. So if we only
 * cancel the ONE tick that completes a piece, vanilla's own counter is left sitting >= 1.0
 * forever (all pieces share the same real BlockPos, so switching which piece is targeted never
 * resets it either) — the very next tick we DON'T intercept, vanilla completes on its own,
 * uncancelled, for whatever piece happens to be targeted then. That is exactly what "3 pieces
 * broke like a piercing shot" was: not simultaneous, but vanilla's own stale counter firing again
 * a few ticks after our first interception, on a different (now-exposed) piece.
 *
 * Fix: once we intercept the first CLIENT_HOLD tick for a given SubgridBlock, we cancel EVERY
 * subsequent tick targeting that same block for as long as the hold continues — vanilla's counter
 * never gets a chance to matter again. We track our own per-piece progress independently (reset
 * per cell, since a fresh piece should need its own full digging time, not inherit leftover
 * progress from a different piece at the same position) and drive the crack overlay ourselves
 * each tick, since vanilla's own overlay update lives in the same now-always-cancelled branch.
 *
 * Creative mode doesn't accumulate at all (vanilla breaks it instantly on Action.START, no
 * getDestroyProgress involved), so it doesn't share this bug — handled separately below, simply
 * cancelling that one instant click and asking the server to remove the piece.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class SubgridMiningInterceptor {

    private static BlockPos trackedPos;
    private static Vec3i trackedCell;
    private static float accumulated;
    private static long lastTick = Long.MIN_VALUE;

    /**
     * At most one mine request may be in-flight — nothing new is sent until the server's removal
     * delta confirms the previous one via onMineConfirmed, with a short timeout as a failsafe.
     */
    private static boolean requestInFlight = false;
    private static long requestSentTick = Long.MIN_VALUE;
    private static final long REQUEST_TIMEOUT_TICKS = 10;

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        if (!level.isClientSide()) return;

        BlockPos pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof SubgridBlock)) {
            reset();
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) return;

        Player player = event.getEntity();
        HitResult hit = player.pick(5.0, 0f, false);
        if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(pos)) return;

        Vec3i cell = GridRay.cellAt(pos, bhr.getLocation(), bhr.getDirection(), be.gridSize);

        if (player.isCreative()) {
            if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
            if (be.getPieceAt(cell.getX(), cell.getY(), cell.getZ()) == null) return;
            event.setCanceled(true);
            if (requestInFlight) return;
            sendMineRequest(level, pos, cell);
            return;
        }

        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.CLIENT_HOLD) return;

        // Every tick targeting a SubgridBlock is ours from here on — see class doc for why
        // vanilla's own completion must never be allowed to run again once we start intercepting.
        event.setUseItem(TriState.FALSE);

        long now = level.getGameTime();
        if (requestInFlight && now - requestSentTick > REQUEST_TIMEOUT_TICKS) requestInFlight = false;
        if (requestInFlight) return;

        if (!pos.equals(trackedPos) || now - lastTick > 1) {
            trackedPos = pos;
            trackedCell = null;
            accumulated = 0f;
        }
        lastTick = now;

        if (be.getPieceAt(cell.getX(), cell.getY(), cell.getZ()) == null) {
            trackedCell = null;
            accumulated = 0f;
            Minecraft.getInstance().level.destroyBlockProgress(player.getId(), pos, -1);
            return;
        }

        if (!cell.equals(trackedCell)) {
            trackedCell = cell;
            accumulated = 0f;
        }

        accumulated += SubgridBlock.pieceDestroyProgress(be, cell.getX(), cell.getY(), cell.getZ(), player);
        int stage = accumulated > 0f ? (int) (Mth.clamp(accumulated, 0f, 1f) * 10f) : -1;
        Minecraft.getInstance().level.destroyBlockProgress(player.getId(), pos, stage);

        if (accumulated >= 1f) {
            trackedCell = null;
            accumulated = 0f;
            Minecraft.getInstance().level.destroyBlockProgress(player.getId(), pos, -1);
            sendMineRequest(level, pos, cell);
        }
    }

    /** Called from NetworkSetup once the server's removal delta lands, releasing the guard above. */
    public static void onMineConfirmed() {
        requestInFlight = false;
    }

    private static void reset() {
        trackedPos = null;
        trackedCell = null;
        accumulated = 0f;
        lastTick = Long.MIN_VALUE;
    }

    private static void sendMineRequest(Level level, BlockPos pos, Vec3i cell) {
        requestInFlight = true;
        requestSentTick = level.getGameTime();
        PacketDistributor.sendToServer(
                new SubgridMinePiecePayload(pos, new BlockPos(cell.getX(), cell.getY(), cell.getZ())));
    }
}
