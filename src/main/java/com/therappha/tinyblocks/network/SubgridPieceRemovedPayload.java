package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent server->client when a single piece is removed from a subgrid via SubgridMinePiecePayload,
 * carrying just the removed cell instead of routing through the vanilla block-entity resync (the
 * same reasoning as SubgridPieceAddedPayload, applied to the remove side). Only used for this
 * flicker-free mining path — the vanilla-mining fallback in SubgridEventHandler#onBlockBreak still
 * uses the old full resync, since it's entangled with the break-cancel client-wipe workaround.
 */
public record SubgridPieceRemovedPayload(BlockPos pos, BlockPos cell) implements CustomPacketPayload {

    public static final Type<SubgridPieceRemovedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "subgrid_piece_removed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubgridPieceRemovedPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SubgridPieceRemovedPayload::pos,
            BlockPos.STREAM_CODEC, SubgridPieceRemovedPayload::cell,
            SubgridPieceRemovedPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
