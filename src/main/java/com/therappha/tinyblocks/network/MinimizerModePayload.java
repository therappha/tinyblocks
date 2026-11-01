package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent client->server on the mode key (Shift+H): switches the mode of what is in hand (NetworkSetup#handleMinimizerMode). */
public record MinimizerModePayload() implements CustomPacketPayload {

    public static final MinimizerModePayload INSTANCE = new MinimizerModePayload();

    public static final Type<MinimizerModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "minimizer_mode"));

    public static final StreamCodec<ByteBuf, MinimizerModePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
