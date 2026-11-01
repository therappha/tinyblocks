package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client->server on a turn of the wheel while sneaking: moves the held Minimizer's size by {@code steps}. The step,
 * not the size: the server's stack is the one that counts, and a fast wheel runs ahead of what the client has back.
 */
public record MinimizerSizeStepPayload(int steps) implements CustomPacketPayload {

    public static final Type<MinimizerSizeStepPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "minimizer_size_step"));

    public static final StreamCodec<ByteBuf, MinimizerSizeStepPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(MinimizerSizeStepPayload::new, MinimizerSizeStepPayload::steps);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
