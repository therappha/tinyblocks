package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent server->client when a single piece is added to a subgrid, carrying just that piece's own
 * NBT instead of routing through the vanilla block-entity resync (which re-serializes every
 * piece in the subgrid via SubgridBlockEntity#saveAdditional). See issue #27 — dense subgrids
 * were paying an O(pieces) resync on every single placement.
 */
public record SubgridPieceAddedPayload(BlockPos pos, CompoundTag pieceNbt) implements CustomPacketPayload {

    public static final Type<SubgridPieceAddedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "subgrid_piece_added"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubgridPieceAddedPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SubgridPieceAddedPayload::pos,
            ByteBufCodecs.COMPOUND_TAG, SubgridPieceAddedPayload::pieceNbt,
            SubgridPieceAddedPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
