package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.BlockAccess;
import com.therappha.tinyblocks.v2.FakeLevel;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared setup for GameTest scenarios: places a real SubgridBlock and pieces inside it via the
 * SAME public engine entry points SubgridEventHandler's real click-driven placement uses
 * (SubgridBlockEntity#placePiece, VanillaBlockPiece#buildFakeSpace, BlockAccess#onPlace) — not a
 * shortcut. GameTestHelper's own placeAt/useBlock convenience methods call vanilla's interaction
 * code directly and never go through NeoForge's event bus, so they can't reach
 * SubgridEventHandler's PlayerInteractEvent.RightClickBlock listener at all; going through this
 * mod's own public API here is the only way to reach the real code, not a compromise.
 */
final class SubgridTestSupport {

    private SubgridTestSupport() {}

    static SubgridBlockEntity placeSubgrid(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Registration.SUBGRID_BLOCK.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof SubgridBlockEntity be)) {
            throw new IllegalStateException("SubgridBlock at " + pos + " didn't produce a SubgridBlockEntity");
        }
        return be;
    }

    /**
     * Places a single-cell VanillaBlockPiece holding state at anchor. SubgridBlockEntity#placePiece runs what follows a
     * real placement: onPlace, its first ticks, the updates to what is around it.
     */
    static PlacedPiece placePiece(SubgridBlockEntity be, ServerLevel level, BlockPos anchor, BlockState state) {
        PlacedPiece piece = new PlacedPiece(VanillaBlockPiece.INSTANCE, anchor, Direction.NORTH);
        piece.runtimeState = state;
        if (!be.placePiece(piece)) {
            throw new IllegalStateException("cell " + anchor + " in subgrid at " + be.getBlockPos() + " is already occupied");
        }
        return piece;
    }

    /**
     * Runs a held item's own useOn against an existing piece (hoe tilling, bone meal, ...) — the
     * same generic mechanism SubgridEventHandler#runItemInteraction uses for any non-BlockItem
     * interaction, minus its bucket-specific fallback (not needed for the items this helper is
     * used for). Updates the piece in place for whatever cell(s) the item's own logic actually
     * touched, mirroring the real production diff.
     */
    static boolean useItemOn(SubgridBlockEntity be, ServerLevel level, net.minecraft.world.entity.player.Player player,
                              net.minecraft.world.item.ItemStack stack, BlockPos anchor) {
        com.therappha.tinyblocks.v2.FakeServerLevel fakeLevel = VanillaBlockPiece.buildFakeServerSpace(be, level);
        java.util.Map<BlockPos, BlockState> before = VanillaBlockPiece.snapshotStates(be); // the fake space no longer holds every piece
        BlockPos at = fakeLevel.world(anchor); // world frame, see FakeSpace#world
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(at), Direction.UP, at, false);
        net.minecraft.world.InteractionResult result = stack.useOn(
                new net.minecraft.world.item.context.UseOnContext(fakeLevel, player, net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit));

        for (var touched : fakeLevel.cells().touchedCells().entrySet()) {
            BlockPos pos = touched.getKey();
            BlockState newState = touched.getValue();
            if (newState.equals(before.get(pos))) continue;
            PlacedPiece piece = be.getPieceAt(pos.getX(), pos.getY(), pos.getZ());
            if (piece != null && piece.definition instanceof VanillaBlockPiece) {
                piece.runtimeState = newState;
                be.notifyNeighbors(piece);
                be.notifyUpdate();
            }
        }
        return result != net.minecraft.world.InteractionResult.PASS;
    }

    /** Simulates a right-click on an existing piece (lever toggle, etc.) via its own onUse, on the middle of its top face. */
    static net.minecraft.world.InteractionResult useOnPiece(SubgridBlockEntity be, ServerLevel level,
                                                              PlacedPiece piece, net.minecraft.world.entity.player.Player player) {
        return piece.definition.onUse(piece, level, be.getBlockPos(), be, player, topHit(be, piece.anchor));
    }

    /** A real-level hit on the middle of the top face of {@code cell}. */
    static net.minecraft.world.phys.BlockHitResult topHit(SubgridBlockEntity be, BlockPos cell) {
        return faceHit(be, cell, Direction.UP);
    }

    /** A real-level hit on the middle of {@code face} of {@code cell}, as a player's click on it would be. */
    static net.minecraft.world.phys.BlockHitResult faceHit(SubgridBlockEntity be, BlockPos cell, Direction face) {
        double size = be.getGridSize();
        net.minecraft.world.phys.Vec3 point = net.minecraft.world.phys.Vec3.atCenterOf(cell)
                .add(net.minecraft.world.phys.Vec3.atLowerCornerOf(face.getNormal()).scale(0.5))
                .scale(1 / size)
                .add(net.minecraft.world.phys.Vec3.atLowerCornerOf(be.getBlockPos()));
        return new net.minecraft.world.phys.BlockHitResult(point, face, be.getBlockPos(), false);
    }

    /** A player's right click on the SubgridBlock at {@code hit}, posted as the event SubgridEventHandler listens to. */
    static void rightClick(SubgridBlockEntity be, net.minecraft.world.entity.player.Player player,
                           net.minecraft.world.phys.BlockHitResult hit) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                player, net.minecraft.world.InteractionHand.MAIN_HAND, be.getBlockPos(), hit));
    }

    /**
     * A server player of this test's own, standing by the grid. NeoForge's shared FakePlayerFactory#getMinecraft is
     * one player for all tests, and tests run side by side: one moving it took another's out of reach. A fake
     * player drops what other mods send it, where a mock player's missing connection refused it.
     */
    static net.minecraft.server.level.ServerPlayer serverPlayer(net.minecraft.gametest.framework.GameTestHelper helper,
                                                                 SubgridBlockEntity be) {
        net.minecraft.server.level.ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "tinyblocks-test"));
        player.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()).add(0, 0, 2));
        return player;
    }

    static BlockState stateOf(SubgridBlockEntity be, BlockPos anchor) {
        PlacedPiece piece = be.getPieceAt(anchor.getX(), anchor.getY(), anchor.getZ());
        return piece == null ? null : (BlockState) piece.runtimeState;
    }

    /** Forces (or returns the already-cached) real phantom BlockEntity for a piece — e.g. to seed a hopper's inventory directly. */
    static net.minecraft.world.level.block.entity.BlockEntity blockEntityOf(SubgridBlockEntity be, PlacedPiece piece, ServerLevel level) {
        return VanillaBlockPiece.blockEntityFor(piece, be, level);
    }
}
