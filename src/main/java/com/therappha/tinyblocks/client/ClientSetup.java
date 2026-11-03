package com.therappha.tinyblocks.client;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.setup.Registration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Registration.SUBGRID_BLOCK_ENTITY.get(), SubgridRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (com.therappha.tinyblocks.client.flywheel.FlywheelLoaded.isLoaded()) {
                com.therappha.tinyblocks.client.flywheel.FlywheelCompat.register();
            }
            // models/item/grid_save.json shows a full Grid Save with its own icon.
            net.minecraft.client.renderer.item.ItemProperties.register(Registration.GRID_SAVE.get(),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "full"),
                    (stack, level, entity, seed) -> com.therappha.tinyblocks.items.GridSaveItem.isFull(stack) ? 1f : 0f);
            // models/item/minimizer.json turns red while it is switched off.
            net.minecraft.client.renderer.item.ItemProperties.register(Registration.MINIMIZER.get(),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "off"),
                    (stack, level, entity, seed) -> MinimizerItem.isOn(stack) ? 0f : 1f);
        });
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerBlock(SubgridClientExtensions.INSTANCE, Registration.allSubgridBlocks());
    }
}
