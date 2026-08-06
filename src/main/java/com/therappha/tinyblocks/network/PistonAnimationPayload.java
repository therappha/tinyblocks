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
 * Sent server->client the moment a piece's fake-space setBlockEntity call is captured as a real
 * PistonMovingBlockEntity (see VanillaBlockPiece.syncBlockEntity) — carries that BE's own NBT
 * (PistonMovingBlockEntity#saveAdditional's shape: blockState/facing/progress/extending/source) so
 * the client can build its own local copy purely for rendering the extend/retract animation.
 *
 * Not routed through the normal piece-state resync: a piece's runtimeState only visibly changes
 * once the animation FINISHES (MOVING_PISTON -> the real end state), so nothing about the
 * in-between animation frames would ever reach the client otherwise. The client-side copy is
 * driven forward locally (SubgridBlockEntity#clientTick) rather than streamed frame-by-frame —
 * matches vanilla's own client-side-prediction approach to piston animation without needing this
 * mod's fake-space simulation to run a second time on the client.
 */
public record PistonAnimationPayload(BlockPos pos, BlockPos cell, CompoundTag beNbt) implements CustomPacketPayload {

    public static final Type<PistonAnimationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "piston_animation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PistonAnimationPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PistonAnimationPayload::pos,
            BlockPos.STREAM_CODEC, PistonAnimationPayload::cell,
            ByteBufCodecs.COMPOUND_TAG, PistonAnimationPayload::beNbt,
            PistonAnimationPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
