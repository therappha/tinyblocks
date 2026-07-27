package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class Registration {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, TinyBlocks.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TinyBlocks.MOD_ID);

    public static final DeferredHolder<Block, SubgridBlock> SUBGRID_BLOCK =
            BLOCKS.register("subgrid_block", () -> new SubgridBlock(
                    Block.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion(), 8
            ));

    public static final DeferredHolder<Block, SubgridBlock> SUBGRID_BLOCK_16 =
            BLOCKS.register("subgrid_block_16", () -> new SubgridBlock(
                    Block.Properties.of().strength(2.0f).sound(SoundType.METAL).noOcclusion(), 16
            ));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SubgridBlockEntity>> SUBGRID_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("subgrid_block", () ->
                    BlockEntityType.Builder.of(
                            (pos, state) -> new SubgridBlockEntity(Registration.SUBGRID_BLOCK_ENTITY.get(), pos, state),
                            SUBGRID_BLOCK.get(), SUBGRID_BLOCK_16.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
    }
}
