package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent client->server on the Grid Save turn key (R): the held Grid Save places its grid a quarter turn more. */
public record GridSaveTurnPayload() implements CustomPacketPayload {

    public static final GridSaveTurnPayload INSTANCE = new GridSaveTurnPayload();

    public static final Type<GridSaveTurnPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "grid_save_turn"));

    public static final StreamCodec<ByteBuf, GridSaveTurnPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
