package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class TinyBlocksTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TinyBlocks.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tinyblocks.main"))
                    .icon(() -> new ItemStack(Registration.MINIMIZER.get()))
                    .displayItems((params, output) -> {
                        output.accept(Registration.MINIMIZER_2.get());
                        output.accept(Registration.MINIMIZER_4.get());
                        output.accept(Registration.MINIMIZER.get());
                        output.accept(Registration.MINIMIZER_16.get());
                    })
                    .build());

    public static void register(net.neoforged.bus.api.IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
