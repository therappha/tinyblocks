package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.setup.SubgridEventHandler;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class NetworkSetup {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SubgridPieceAddedPayload.TYPE, SubgridPieceAddedPayload.STREAM_CODEC, NetworkSetup::handlePieceAdded);
        registrar.playToClient(SubgridPieceRemovedPayload.TYPE, SubgridPieceRemovedPayload.STREAM_CODEC, NetworkSetup::handlePieceRemoved);
        registrar.playToClient(PistonAnimationPayload.TYPE, PistonAnimationPayload.STREAM_CODEC, NetworkSetup::handlePistonAnimation);
        registrar.playToServer(SubgridMinePiecePayload.TYPE, SubgridMinePiecePayload.STREAM_CODEC, NetworkSetup::handleMinePiece);
    }

    private static void handlePieceAdded(SubgridPieceAddedPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.player().level();
            if (level.getBlockEntity(payload.pos()) instanceof SubgridBlockEntity subgrid) {
                subgrid.applyRemoteAdd(PlacedPiece.load(payload.pieceNbt()));
            }
        });
    }

    private static void handlePieceRemoved(SubgridPieceRemovedPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.player().level();
            if (level.getBlockEntity(payload.pos()) instanceof SubgridBlockEntity subgrid) {
                var cell = payload.cell();
                subgrid.removePieceAt(cell.getX(), cell.getY(), cell.getZ());
            }
            // This payload only ever reaches a client (playToClient) — safe to release the mining
            // interceptor's in-flight guard here (see SubgridMiningInterceptor for why it exists).
            com.therappha.tinyblocks.client.SubgridMiningInterceptor.onMineConfirmed();
        });
    }

    private static void handlePistonAnimation(PistonAnimationPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.player().level();
            if (level.getBlockEntity(payload.pos()) instanceof SubgridBlockEntity subgrid) {
                com.therappha.tinyblocks.v2.VanillaBlockPiece.applyClientAnimation(subgrid, payload.cell(), payload.beNbt(), level);
            }
        });
    }

    private static void handleMinePiece(SubgridMinePiecePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
                SubgridEventHandler.handleMinePiece(payload, serverPlayer, serverLevel);
            }
        });
    }
}
