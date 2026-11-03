package com.therappha.tinyblocks.v2;

import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The client's copy of a piston moving in a grid: built from PistonAnimationPayload and moved on each client tick.
 *
 * PistonMovingBlockEntity's progress/progressO/deathTicks fields are private with no public
 * setter (only the read-only getProgress(partialTick) getter) — vanilla's own real tick(Level,
 * BlockPos, BlockState, PistonMovingBlockEntity) static method is the only other way to drive
 * them, but that method's finalize branch calls level.setBlock/removeBlockEntity on whatever
 * position it's given, which would corrupt a REAL position if aimed at one (see
 * PistonAnimationPayload's doc comment for why the client copy can't safely reuse it). Direct
 * field access is the same technique FakeServerLevel already relies on elsewhere in this
 * package to bridge into vanilla internals with no other entry point.
 */
public final class PistonAnimation {

    private PistonAnimation() {}

    private static final java.lang.reflect.Field PISTON_PROGRESS;
    private static final java.lang.reflect.Field PISTON_PROGRESS_O;
    private static final java.lang.reflect.Field PISTON_DEATH_TICKS;

    static {
        try {
            Class<net.minecraft.world.level.block.piston.PistonMovingBlockEntity> type =
                    net.minecraft.world.level.block.piston.PistonMovingBlockEntity.class;
            PISTON_PROGRESS = type.getDeclaredField("progress");
            PISTON_PROGRESS.setAccessible(true);
            PISTON_PROGRESS_O = type.getDeclaredField("progressO");
            PISTON_PROGRESS_O.setAccessible(true);
            PISTON_DEATH_TICKS = type.getDeclaredField("deathTicks");
            PISTON_DEATH_TICKS.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * How many client ticks a fully-extended/retracted moving-block piece lingers before its
     * client-local animation copy is dropped — matches PistonMovingBlockEntity's own deathTicks
     * grace window (5), so this copy disappears on roughly the same schedule vanilla's would,
     * rather than popping away the instant progress hits 1.0.
     */
    private static final int PISTON_LINGER_TICKS = 5;
    /** Matches PistonMovingBlockEntity.tick's own per-tick step (TICKS_TO_EXTEND = 2). */
    private static final float PISTON_PROGRESS_PER_TICK = 0.5F;

    static void clientTick(PlacedPiece piece, BlockPos subgridPos,
                           net.minecraft.world.level.block.piston.PistonMovingBlockEntity moving) {
        try {
            float progress = PISTON_PROGRESS.getFloat(moving);
            if (progress >= 1.0F) {
                int lingered = PISTON_DEATH_TICKS.getInt(moving) + 1;
                if (lingered >= PISTON_LINGER_TICKS) {
                    // By now the server's real finalize (same 2-ish ticks) has already landed the
                    // piece's real end state via the normal update path — dropping the animation
                    // copy just lets SubgridRenderer fall back to rendering that real state's
                    // static model instead of a BER for it.
                    piece.runtimeBlockEntity = null;
                } else {
                    PISTON_DEATH_TICKS.setInt(moving, lingered);
                }
                return;
            }
            PISTON_PROGRESS_O.setFloat(moving, progress);
            PISTON_PROGRESS.setFloat(moving, Math.min(1.0F, progress + PISTON_PROGRESS_PER_TICK));
        } catch (ReflectiveOperationException e) {
            piece.runtimeBlockEntity = null;
        }
    }

    /**
     * Client-side entry point for PistonAnimationPayload: builds a local, animation-only copy of
     * the real PistonMovingBlockEntity the server captured, purely for SubgridRenderer's BER
     * dispatch and clientTick's local progress advance. Its finalize path (inside vanilla's
     * real tick(), which this class deliberately never calls) is never invoked on this copy — only
     * direct field mutation — so it can never write anywhere outside itself.
     */
    public static void applyClientAnimation(SubgridBlockEntity be, BlockPos cell, CompoundTag beNbt, Level level) {
        PlacedPiece piece = be.getPieceAt(cell.getX(), cell.getY(), cell.getZ());
        if (piece == null || !(piece.definition instanceof VanillaBlockPiece)) {
            // Can legitimately happen: this payload is sent right after placePieceCrossBoundary for
            // a BRAND NEW cell (piston head extending into empty space) — if the corresponding
            // SubgridPieceAddedPayload hasn't been processed on this client yet, the piece just
            // doesn't exist here yet.
            return;
        }
        // The outer MOVING_PISTON state embedded by GridWriteBack.syncBlockEntity — NOT stateOf(piece), which
        // reads whatever the piece's state happens to be RIGHT NOW on the client. That can already
        // differ from the MOVING_PISTON state true when this BE was captured server-side (a same-
        // tick full resync racing ahead of this payload), and PistonMovingBlockEntity's constructor
        // requires an exact match — see GridWriteBack.syncBlockEntity's comment for the crash this used to cause.
        BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), beNbt.getCompound("outerState"));
        if (state.isAir()) return;
        var moving = new net.minecraft.world.level.block.piston.PistonMovingBlockEntity(be.getBlockPos(), state);
        moving.loadCustomOnly(beNbt, level.registryAccess());
        moving.setLevel(level);
        piece.runtimeBlockEntity = moving;
        be.markPiecesChanged(); // the renderer draws this piece through its block entity now
    }
}
