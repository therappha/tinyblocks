package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent client->server on Ctrl + scroll with a shape corner held: raise (lower) the second corner by {@code delta} cells. */
public record MinimizerLiftPayload(int delta) implements CustomPacketPayload {

    public static final Type<MinimizerLiftPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "minimizer_lift"));

    public static final StreamCodec<ByteBuf, MinimizerLiftPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(MinimizerLiftPayload::new, MinimizerLiftPayload::delta);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
