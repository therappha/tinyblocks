package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent client->server by the on/off key: switches the held Minimizer off or on. */
public record MinimizerTogglePayload() implements CustomPacketPayload {

    public static final MinimizerTogglePayload INSTANCE = new MinimizerTogglePayload();

    public static final Type<MinimizerTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "minimizer_toggle"));

    public static final StreamCodec<ByteBuf, MinimizerTogglePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
