package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent client->server by the resize key: turn the grid the player looks at to the held Minimizer's size. */
public record MinimizerResizePayload() implements CustomPacketPayload {

    public static final MinimizerResizePayload INSTANCE = new MinimizerResizePayload();

    public static final Type<MinimizerResizePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "minimizer_resize"));

    public static final StreamCodec<ByteBuf, MinimizerResizePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
