package com.therappha.tinyblocks;

import com.mojang.logging.LogUtils;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.TinyFurnaceDefinition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(TinyBlocks.MOD_ID)
public class TinyBlocks {

    public static final String MOD_ID = "tinyblocks";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TinyBlocks(IEventBus modEventBus) {
        TinyFurnaceDefinition.INSTANCE.getClass(); // force-load static instance
        Registration.register(modEventBus);
        LOGGER.info("TinyBlocks loading...");
    }
}
