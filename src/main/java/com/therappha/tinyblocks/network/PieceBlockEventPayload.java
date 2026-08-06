package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent server->client whenever a piece's own vanilla logic calls level.blockEvent(pos, block, id,
 * param) — vanilla's own generic "notify nearby clients of a small, non-block-state state change"
 * signal (a chest's opener count, a bell's ring, a piston's own extend/retract trigger, a note
 * block's play, ...). FakeLevel/FakeServerLevel don't get this for free the way a real
 * ServerLevel/ClientLevel pair would (see FakeLevel#blockEvent's own doc comment for why), so it's
 * rebuilt here as an explicit payload: the client replays it directly against this piece's own
 * cached BlockEntity (BlockEntity#triggerEvent(int,int)), which is exactly what
 * BlockState#triggerEvent's default implementation would have done with it anyway.
 *
 * Generic — not piston-specific, not chest-specific. Whatever BlockEntity subclass a piece has
 * decides what its own triggerEvent(id, param) does with these two ints, same as it always would.
 */
public record PieceBlockEventPayload(BlockPos pos, BlockPos cell, int eventId, int eventParam) implements CustomPacketPayload {

    public static final Type<PieceBlockEventPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "piece_block_event"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PieceBlockEventPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PieceBlockEventPayload::pos,
            BlockPos.STREAM_CODEC, PieceBlockEventPayload::cell,
            ByteBufCodecs.VAR_INT, PieceBlockEventPayload::eventId,
            ByteBufCodecs.VAR_INT, PieceBlockEventPayload::eventParam,
            PieceBlockEventPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
