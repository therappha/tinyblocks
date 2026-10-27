package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent client->server on the "minimize me" key: shrink to the held Minimizer's size, or back (MinimizeMe). */
public record MinimizeMePayload() implements CustomPacketPayload {

    public static final MinimizeMePayload INSTANCE = new MinimizeMePayload();

    public static final Type<MinimizeMePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "minimize_me"));

    public static final StreamCodec<ByteBuf, MinimizeMePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
