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
 * Server->client: a piece's phantom BlockEntity data changed (e.g. a Create kinetic block's speed
 * after a hand crank turn). A real BlockEntity would reach the client through the chunk's
 * level.sendBlockUpdated -> getUpdatePacket path, but phantoms aren't in the chunk and the fake
 * levels' sendBlockUpdated is a no-op, so BlockEntity#sendData-style syncs (Create's
 * SmartBlockEntity#sendData) used to vanish. The client loads this straight into its own cached
 * phantom for the piece at {@code cell}.
 *
 * {@code clientUpdate}: the tag is the block entity's own update tag (what a chunk sends a client for it) and the
 * client loads it as a chunk would, through handleUpdateTag; otherwise it is the saved data, loaded as such.
 */
public record PhantomDataPayload(BlockPos pos, BlockPos cell, CompoundTag nbt, boolean clientUpdate) implements CustomPacketPayload {

    public static final Type<PhantomDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "phantom_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhantomDataPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PhantomDataPayload::pos,
            BlockPos.STREAM_CODEC, PhantomDataPayload::cell,
            ByteBufCodecs.COMPOUND_TAG, PhantomDataPayload::nbt,
            ByteBufCodecs.BOOL, PhantomDataPayload::clientUpdate,
            PhantomDataPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
