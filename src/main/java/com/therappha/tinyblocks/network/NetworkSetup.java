package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
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
    }

    private static void handlePieceAdded(SubgridPieceAddedPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.player().level();
            if (level.getBlockEntity(payload.pos()) instanceof SubgridBlockEntity subgrid) {
                subgrid.applyRemoteAdd(PlacedPiece.load(payload.pieceNbt()));
            }
        });
    }
}
