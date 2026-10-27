package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent client->server when a grid size is picked on the Minimizer's size board: set the held Minimizer's size. */
public record MinimizerSizePayload(int size) implements CustomPacketPayload {

    public static final Type<MinimizerSizePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "minimizer_size"));

    public static final StreamCodec<ByteBuf, MinimizerSizePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(MinimizerSizePayload::new, MinimizerSizePayload::size);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
