package com.therappha.tinyblocks.v2;

import com.mojang.authlib.GameProfile;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mods often handle a right click on their block in a RightClickBlock listener rather than in the block's own
 * use methods: Create sets a redstone link's frequencies, and every other value box of its block entity
 * behaviours, this way. Such a listener looks up the block entity at the clicked position and checks exactly
 * where on the block the click landed — in the real world that is the SubgridBlock, which has none of it.
 *
 * So a click on a piece is posted again inside the piece's own fake space, where the clicked position is the
 * piece's phantom and the hit point is scaled into it, by a stand-in player placed just in front of that point
 * in the virtual frame, looking the same way and holding the same items (listeners re-trace the click from the
 * player's eyes). Nothing is specific to any mod: whatever listens to the click sees an ordinary click.
 */
public final class PieceClickDispatch {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final GameProfile PROXY = new GameProfile(
            UUID.fromString("7b1f3c2e-5d8a-4e6b-9c0d-1a2b3c4d5e6f"), "[TinyBlocks]");

    /** How far in front of the hit point (in virtual blocks) the stand-in's eyes are: well inside a re-trace's reach. */
    private static final double EYE_DISTANCE = 1.5;

    /** One stand-in per real player, moved into whichever piece's space that player clicks. Server thread only. */
    private static final Map<UUID, FakePlayer> STAND_INS = new HashMap<>();

    private PieceClickDispatch() {}

    /**
     * The stand-in for {@code player}'s clicks, now in {@code space}: built on the player's first click, then reused.
     * Its inventory is emptied, so nothing a listener gave it on an earlier click is carried over.
     */
    public static FakePlayer standInFor(Player player, net.minecraft.server.level.ServerLevel space) {
        FakePlayer proxy = STAND_INS.get(player.getUUID());
        if (proxy == null) {
            proxy = standIn(space);
            STAND_INS.put(player.getUUID(), proxy);
        } else if (proxy.level() != space) {
            proxy.setServerLevel(space);
        }
        proxy.getInventory().clearContent();
        return proxy;
    }

    /** Drops the stand-in of a player who left, so it doesn't hold on to a piece's space. */
    public static void forget(UUID player) {
        STAND_INS.remove(player);
    }

    /** Drops every stand-in, when the server stops. */
    public static void forgetAll() {
        STAND_INS.clear();
    }

    /**
     * A player's constructor looks for a spawn around the world's, from {@code level}. A piece's fake space answers every
     * cell of a 131072 block tall column, so that search held the server for seconds on each click.
     */
    public static FakePlayer standIn(net.minecraft.server.level.ServerLevel level) {
        return new FakePlayer(level, PROXY) {
            @Override
            public BlockPos adjustSpawnLocation(net.minecraft.server.level.ServerLevel spawnLevel, BlockPos pos) {
                return pos;
            }
        };
    }

    /** True for the stand-in a click on a piece is posted with: it aims where the real player did. */
    public static boolean isStandIn(Player player) {
        return player instanceof FakePlayer && PROXY.getId().equals(player.getUUID());
    }

    /** True if a listener took the click (cancelled the event). */
    public static boolean dispatch(SubgridBlockEntity be, PlacedPiece piece, FakeServerLevel space, Player player,
                                   InteractionHand hand, BlockHitResult hit) {
        Vec3 virtualHit = VirtualHit.toVirtual(hit.getLocation(), be.getBlockPos(), be.virtualOrigin(), be.getGridSize());
        BlockPos virtualPos = be.virtualPosOf(piece.anchor);

        FakePlayer proxy;
        try {
            proxy = standInFor(player, space);
        } catch (RuntimeException e) {
            LOGGER.warn("Could not create the stand-in player for a click on a piece at {}", be.getBlockPos(), e);
            return false;
        }
        proxy.setItemInHand(InteractionHand.MAIN_HAND, player.getMainHandItem());
        proxy.setItemInHand(InteractionHand.OFF_HAND, player.getOffhandItem());
        proxy.setShiftKeyDown(player.isShiftKeyDown());
        Vec3 eye = virtualHit.subtract(player.getLookAngle().scale(EYE_DISTANCE));
        proxy.moveTo(eye.x, eye.y - proxy.getEyeHeight(), eye.z, player.getYRot(), player.getXRot());
        proxy.setYHeadRot(player.getYHeadRot());

        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(proxy, hand, virtualPos,
                new BlockHitResult(virtualHit, hit.getDirection(), virtualPos, hit.isInside()));
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }
}
