package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent client->server on the lock key: locks or unlocks the Minimizer in hand (MinimizerItem#toggleLock). */
public record MinimizerLockPayload() implements CustomPacketPayload {

    public static final MinimizerLockPayload INSTANCE = new MinimizerLockPayload();

    public static final Type<MinimizerLockPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "minimizer_lock"));

    public static final StreamCodec<ByteBuf, MinimizerLockPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
