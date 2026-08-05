package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client->server when SubgridMiningInterceptor decides (client-side, mirroring
 * SubgridBlock.pieceDestroyProgress) that the piece at (pos, cell) just finished mining. Replaces
 * vanilla's own STOP_DESTROY_BLOCK signal for this specific completion, which the interceptor
 * suppressed precisely to stop the client's own speculative destroyBlock() call — see that class
 * for the full reasoning (issue #8 / the mining-flicker follow-up).
 */
public record SubgridMinePiecePayload(BlockPos pos, BlockPos cell) implements CustomPacketPayload {

    public static final Type<SubgridMinePiecePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "subgrid_mine_piece"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubgridMinePiecePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SubgridMinePiecePayload::pos,
            BlockPos.STREAM_CODEC, SubgridMinePiecePayload::cell,
            SubgridMinePiecePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
