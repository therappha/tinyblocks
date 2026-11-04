package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.GridBoxBlock;
import com.therappha.tinyblocks.items.GridBoxBlockEntity;
import com.therappha.tinyblocks.items.GridSaveItem;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;
import java.util.TreeMap;

public class Registration {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, TinyBlocks.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, TinyBlocks.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TinyBlocks.MOD_ID);

    // Subgrid blocks — 1/2, 1/4, 1/8, 1/16, and 1/9: an odd size has a middle cell, so a piece can sit centred on the
    // block (a tiny shaft in line with a full-size one)
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

    public static final DeferredHolder<Block, SubgridBlock> SUBGRID_BLOCK_9 =
            BLOCKS.register("subgrid_block_9", () -> new SubgridBlock(
                    Block.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion(), 9));

    // What a full Grid Save puts down: a closed box, opened to place the grid (GridBoxBlock)
    public static final DeferredHolder<Block, GridBoxBlock> GRID_BOX =
            BLOCKS.register("grid_box", () -> new GridBoxBlock(
                    Block.Properties.of().strength(0.5f).sound(SoundType.WOOL).noOcclusion()));

    /** Smallest and largest grid size: cells of 1/2 to 1/16 of a block. */
    public static final int MIN_GRID_SIZE = 2, MAX_GRID_SIZE = 16;

    /** Every other size in that range: the SubgridBlock's size is its block's, so each size is a block of its own. */
    private static final Map<Integer, DeferredHolder<Block, SubgridBlock>> OTHER_SUBGRID_BLOCKS = new TreeMap<>();

    static {
        for (int size = MIN_GRID_SIZE; size <= MAX_GRID_SIZE; size++) {
            if (size == 2 || size == 4 || size == 8 || size == 9 || size == 16) continue;
            int gridSize = size;
            OTHER_SUBGRID_BLOCKS.put(size, BLOCKS.register("subgrid_block_" + size, () -> new SubgridBlock(
                    Block.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion(), gridSize)));
        }
    }

    /** The SubgridBlock of grid size {@code size} (MIN_GRID_SIZE..MAX_GRID_SIZE; anything else gets 8). */
    public static SubgridBlock subgridBlock(int size) {
        return switch (size) {
            case 2 -> SUBGRID_BLOCK_2.get();
            case 4 -> SUBGRID_BLOCK_4.get();
            case 9 -> SUBGRID_BLOCK_9.get();
            case 16 -> SUBGRID_BLOCK_16.get();
            default -> OTHER_SUBGRID_BLOCKS.containsKey(size) ? OTHER_SUBGRID_BLOCKS.get(size).get() : SUBGRID_BLOCK.get();
        };
    }

    /** Every SubgridBlock, one per grid size. */
    public static Block[] allSubgridBlocks() {
        Block[] all = new Block[MAX_GRID_SIZE - MIN_GRID_SIZE + 1];
        for (int size = MIN_GRID_SIZE; size <= MAX_GRID_SIZE; size++) all[size - MIN_GRID_SIZE] = subgridBlock(size);
        return all;
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SubgridBlockEntity>> SUBGRID_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("subgrid_block", () ->
                    BlockEntityType.Builder.of(
                            (pos, state) -> new SubgridBlockEntity(Registration.SUBGRID_BLOCK_ENTITY.get(), pos, state),
                            allSubgridBlocks()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GridBoxBlockEntity>> GRID_BOX_ENTITY =
            BLOCK_ENTITIES.register("grid_box", () -> BlockEntityType.Builder.of(GridBoxBlockEntity::new, GRID_BOX.get()).build(null));

    // Subgrid block items — only obtainable via /give, not in any creative tab
    public static final DeferredHolder<Item, BlockItem> SUBGRID_BLOCK_2_ITEM =
            ITEMS.register("subgrid_block_2", () -> new BlockItem(SUBGRID_BLOCK_2.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> SUBGRID_BLOCK_4_ITEM =
            ITEMS.register("subgrid_block_4", () -> new BlockItem(SUBGRID_BLOCK_4.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> SUBGRID_BLOCK_ITEM =
            ITEMS.register("subgrid_block", () -> new BlockItem(SUBGRID_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> SUBGRID_BLOCK_16_ITEM =
            ITEMS.register("subgrid_block_16", () -> new BlockItem(SUBGRID_BLOCK_16.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> SUBGRID_BLOCK_9_ITEM =
            ITEMS.register("subgrid_block_9", () -> new BlockItem(SUBGRID_BLOCK_9.get(), new Item.Properties()));

    // Carries a whole grid to somewhere else (GridSaveItem)
    public static final DeferredHolder<Item, GridSaveItem> GRID_SAVE =
            ITEMS.register("grid_save", () -> new GridSaveItem(new Item.Properties()));

    // Held in the offhand, makes any BlockItem in the main hand place into a subgrid. Its size is kept on the stack.
    private static Item.Properties minimizerProperties() {
        // No glint of its own: a glint says it is locked (MinimizerItem#isFoil).
        return new Item.Properties();
    }

    public static final DeferredHolder<Item, MinimizerItem> MINIMIZER =
            ITEMS.register("minimizer", () -> new MinimizerItem(minimizerProperties()));

    /** The fixed-size Minimizers of older versions: one in a saved world turns into the one Minimizer there is now. */
    private static final String[] OLD_MINIMIZERS = {"minimizer_2", "minimizer_4", "minimizer_9", "minimizer_16"};

    public static void register(IEventBus eventBus) {
        for (String old : OLD_MINIMIZERS) {
            ITEMS.addAlias(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, old),
                    ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "minimizer"));
        }
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
    }
}
