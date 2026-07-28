package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PieceRemoverTool;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class TinyBlocksCapabilities {

    public static final ItemCapability<PieceRemoverTool, Void> PIECE_REMOVER =
            ItemCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "piece_remover"),
                    PieceRemoverTool.class);

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerItem(PIECE_REMOVER, (stack, ctx) -> PieceRemoverTool.INSTANCE,
                Registration.PIECE_REMOVER_STICK.get());
    }
}
