package com.therappha.tinyblocks.client;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.client.flywheel.FlywheelCompat;
import com.therappha.tinyblocks.client.flywheel.FlywheelLoaded;
import com.therappha.tinyblocks.items.GridSaveItem;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.setup.Registration;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Registration.SUBGRID_BLOCK_ENTITY.get(), SubgridRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (FlywheelLoaded.isLoaded()) {
                FlywheelCompat.register();
            }
            // models/item/grid_save.json shows a full Grid Save with its own icon.
            ItemProperties.register(Registration.GRID_SAVE.get(),
                    ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "full"),
                    (stack, level, entity, seed) -> GridSaveItem.isFull(stack) ? 1f : 0f);
            // models/item/minimizer.json turns red while it is switched off.
            ItemProperties.register(Registration.MINIMIZER.get(),
                    ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "off"),
                    (stack, level, entity, seed) -> MinimizerItem.isOn(stack) ? 0f : 1f);
        });
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerBlock(SubgridClientExtensions.INSTANCE, Registration.allSubgridBlocks());
    }
}
