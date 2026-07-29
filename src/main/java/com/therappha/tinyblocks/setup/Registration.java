package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.items.TinyPistonItem;
import com.therappha.tinyblocks.items.TinyRedstoneBlockItem;
import com.therappha.tinyblocks.items.TinyRedstoneDustItem;
import com.therappha.tinyblocks.items.TinyStoneBlockItem;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class Registration {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, TinyBlocks.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, TinyBlocks.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TinyBlocks.MOD_ID);

    // Subgrid blocks — 1/2, 1/4, 1/8, 1/16
    public static final DeferredHolder<Block, SubgridBlock> SUBGRID_BLOCK_2 =
            BLOCKS.register("subgrid_block_2", () -> new SubgridBlock(
                    Block.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion(), 2));

    public static final DeferredHolder<Block, SubgridBlock> SUBGRID_BLOCK_4 =
            BLOCKS.register("subgrid_block_4", () -> new SubgridBlock(
                    Block.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion(), 4));

    public static final DeferredHolder<Block, SubgridBlock> SUBGRID_BLOCK =
            BLOCKS.register("subgrid_block", () -> new SubgridBlock(
                    Block.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion(), 8));

    public static final DeferredHolder<Block, SubgridBlock> SUBGRID_BLOCK_16 =
            BLOCKS.register("subgrid_block_16", () -> new SubgridBlock(
                    Block.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion(), 16));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SubgridBlockEntity>> SUBGRID_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("subgrid_block", () ->
                    BlockEntityType.Builder.of(
                            (pos, state) -> new SubgridBlockEntity(Registration.SUBGRID_BLOCK_ENTITY.get(), pos, state),
                            SUBGRID_BLOCK_2.get(), SUBGRID_BLOCK_4.get(),
                            SUBGRID_BLOCK.get(), SUBGRID_BLOCK_16.get()).build(null));

    // Subgrid block items — only obtainable via /give, not in any creative tab
    public static final DeferredHolder<Item, BlockItem> SUBGRID_BLOCK_2_ITEM =
            ITEMS.register("subgrid_block_2", () -> new BlockItem(SUBGRID_BLOCK_2.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> SUBGRID_BLOCK_4_ITEM =
            ITEMS.register("subgrid_block_4", () -> new BlockItem(SUBGRID_BLOCK_4.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> SUBGRID_BLOCK_ITEM =
            ITEMS.register("subgrid_block", () -> new BlockItem(SUBGRID_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> SUBGRID_BLOCK_16_ITEM =
            ITEMS.register("subgrid_block_16", () -> new BlockItem(SUBGRID_BLOCK_16.get(), new Item.Properties()));

    // Piece debug items — only obtainable via /give, not in any creative tab
    public static final DeferredHolder<Item, TinyStoneBlockItem> TINY_STONE_BLOCK_2 =
            ITEMS.register("tiny_stone_block_2", () -> new TinyStoneBlockItem(new Item.Properties()) {
                @Override public SubgridBlock preferredSubgrid() { return SUBGRID_BLOCK_2.get(); }
            });

    public static final DeferredHolder<Item, TinyStoneBlockItem> TINY_STONE_BLOCK_4 =
            ITEMS.register("tiny_stone_block_4", () -> new TinyStoneBlockItem(new Item.Properties()) {
                @Override public SubgridBlock preferredSubgrid() { return SUBGRID_BLOCK_4.get(); }
            });

    public static final DeferredHolder<Item, TinyStoneBlockItem> TINY_STONE_BLOCK =
            ITEMS.register("tiny_stone_block", () -> new TinyStoneBlockItem(new Item.Properties()));

    public static final DeferredHolder<Item, TinyStoneBlockItem> TINY_STONE_BLOCK_16 =
            ITEMS.register("tiny_stone_block_16", () -> new TinyStoneBlockItem(new Item.Properties()) {
                @Override public SubgridBlock preferredSubgrid() { return SUBGRID_BLOCK_16.get(); }
            });

    // Test tool for the PieceRemoverTool capability — only obtainable via /give
    public static final DeferredHolder<Item, Item> PIECE_REMOVER_STICK =
            ITEMS.register("piece_remover_stick", () -> new Item(new Item.Properties()));

    // v2 debug tool: held in the offhand, lets any BlockItem in the main hand create/place into
    // a subgrid instead of placing normally — only obtainable via /give. Same four grid sizes as
    // the subgrid blocks themselves; "minimizer" (1/8) is the default.
    private static Item.Properties minimizerProperties() {
        return new Item.Properties()
                .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    public static final DeferredHolder<Item, MinimizerItem> MINIMIZER_2 =
            ITEMS.register("minimizer_2", () -> new MinimizerItem(minimizerProperties()) {
                @Override public SubgridBlock preferredSubgrid() { return SUBGRID_BLOCK_2.get(); }
            });

    public static final DeferredHolder<Item, MinimizerItem> MINIMIZER_4 =
            ITEMS.register("minimizer_4", () -> new MinimizerItem(minimizerProperties()) {
                @Override public SubgridBlock preferredSubgrid() { return SUBGRID_BLOCK_4.get(); }
            });

    public static final DeferredHolder<Item, MinimizerItem> MINIMIZER =
            ITEMS.register("minimizer", () -> new MinimizerItem(minimizerProperties()));

    public static final DeferredHolder<Item, MinimizerItem> MINIMIZER_16 =
            ITEMS.register("minimizer_16", () -> new MinimizerItem(minimizerProperties()) {
                @Override public SubgridBlock preferredSubgrid() { return SUBGRID_BLOCK_16.get(); }
            });

    // Debug pieces for testing neighbor propagation — 1/8 grid, only obtainable via /give
    public static final DeferredHolder<Item, TinyRedstoneBlockItem> TINY_REDSTONE_BLOCK =
            ITEMS.register("tiny_redstone_block", () -> new TinyRedstoneBlockItem(new Item.Properties()));

    public static final DeferredHolder<Item, TinyRedstoneDustItem> TINY_REDSTONE_DUST =
            ITEMS.register("tiny_redstone_dust", () -> new TinyRedstoneDustItem(new Item.Properties()));

    public static final DeferredHolder<Item, TinyPistonItem> TINY_PISTON =
            ITEMS.register("tiny_piston", () -> new TinyPistonItem(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
    }
}
