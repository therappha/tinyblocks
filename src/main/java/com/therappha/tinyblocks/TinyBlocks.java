package com.therappha.tinyblocks;

import com.mojang.logging.LogUtils;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PieceDefinitions;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(TinyBlocks.MOD_ID)
public class TinyBlocks {

    public static final String MOD_ID = "tinyblocks";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TinyBlocks(IEventBus modEventBus) {
        PieceDefinitions.init();
        VanillaBlockPiece.init();
        Registration.register(modEventBus);
        LOGGER.info("TinyBlocks loading...");
    }
}
