package com.therappha.tinyblocks.compat.create;

import com.simibubi.create.AllBlockEntityTypes;
import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the shaft connector (EXPERIMENT: Create is only a dependency on this branch). */
public final class CreateConnectorSetup {

    private CreateConnectorSetup() {}

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, TinyBlocks.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, TinyBlocks.MOD_ID);

    public static final DeferredHolder<Block, ShaftConnectorBlock> SHAFT_CONNECTOR =
            BLOCKS.register("shaft_connector", () -> new ShaftConnectorBlock(Block.Properties.of()
                    .mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredHolder<Item, BlockItem> SHAFT_CONNECTOR_ITEM =
            ITEMS.register("shaft_connector", () -> new BlockItem(SHAFT_CONNECTOR.get(), new Item.Properties()));

    public static void register(IEventBus modEventBus) {
        ShaftConnectorPiece.init();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(CreateConnectorSetup::addToShaftBlockEntity);
    }

    /** The connector's block entity is Create's own shaft block entity, which only accepts its listed blocks. */
    private static void addToShaftBlockEntity(BlockEntityTypeAddBlocksEvent event) {
        event.modify(AllBlockEntityTypes.BRACKETED_KINETIC.get(), SHAFT_CONNECTOR.get());
    }
}
