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
     * Places a single-cell VanillaBlockPiece holding state at anchor, firing onPlace like a real
     * placement would — including draining any scheduleTick it makes (e.g. LiquidBlock.onPlace
     * scheduling water's very first spread step) into be's own persistent queue, same as
     * SubgridEventHandler's real placement flow does. Skipping that drain would leave a
     * freshly-placed water/growing piece permanently inert — nothing would ever fire its first tick.
     */
    static PlacedPiece placePiece(SubgridBlockEntity be, ServerLevel level, BlockPos anchor, BlockState state) {
        PlacedPiece piece = new PlacedPiece(VanillaBlockPiece.INSTANCE, anchor, Direction.NORTH);
        piece.runtimeState = state;
        if (!be.placePiece(piece)) {
            throw new IllegalStateException("cell " + anchor + " in subgrid at " + be.getBlockPos() + " is already occupied");
        }
        FakeLevel fakeLevel = VanillaBlockPiece.buildFakeSpace(be, level);
        BlockAccess.onPlace(state, fakeLevel, anchor, Blocks.AIR.defaultBlockState(), false);
        for (com.therappha.tinyblocks.v2.FakeSpace.ScheduledEntry entry : fakeLevel.scheduledTicks()) {
            be.scheduleTick(entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(), level.getGameTime() + entry.delay());
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
        java.util.Map<BlockPos, BlockState> before = new java.util.HashMap<>(fakeLevel.cells().touchedCells());
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(anchor), Direction.UP, anchor, false);
        net.minecraft.world.InteractionResult result = stack.useOn(
                new net.minecraft.world.item.context.UseOnContext(fakeLevel, player, net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit));

        for (var touched : fakeLevel.cells().touchedCells().entrySet()) {
            BlockPos pos = touched.getKey();
            BlockState newState = touched.getValue();
            if (newState.equals(before.get(pos))) continue;
            PlacedPiece piece = be.getPieceAt(pos.getX(), pos.getY(), pos.getZ());
            if (piece != null && piece.definition == VanillaBlockPiece.INSTANCE) {
                piece.runtimeState = newState;
                be.notifyNeighbors(piece);
                be.notifyUpdate();
            }
        }
        return result != net.minecraft.world.InteractionResult.PASS;
    }

    /** Simulates a right-click on an existing piece (lever toggle, etc.) via its own onUse. */
    static net.minecraft.world.InteractionResult useOnPiece(SubgridBlockEntity be, ServerLevel level,
                                                              PlacedPiece piece, net.minecraft.world.entity.player.Player player) {
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()), Direction.UP, be.getBlockPos(), false);
        return piece.definition.onUse(piece, level, be.getBlockPos(), be, player, hit);
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
