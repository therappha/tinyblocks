package com.therappha.tinyblocks.compat.create;

import com.simibubi.create.api.contraption.transformable.MovedBlockTransformerRegistries;
import com.simibubi.create.api.schematic.nbt.SafeNbtWriterRegistry;
import com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A SubgridBlock in Create's schematics and contraptions: what of a grid a schematicannon prints (writeSafe), how a
 * printed or moved grid is turned (transform), and what it costs to print (requiredItems).
 */
public final class CreateSchematics {

    private CreateSchematics() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CreateSchematics::setup);
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SafeNbtWriterRegistry.REGISTRY.register(Registration.SUBGRID_BLOCK_ENTITY.get(), CreateSchematics::writeSafe);
            MovedBlockTransformerRegistries.BLOCK_ENTITY_TRANSFORMERS.register(Registration.SUBGRID_BLOCK_ENTITY.get(),
                    CreateSchematics::transform);
            SchematicRequirementRegistries.BLOCK_ENTITIES.register(Registration.SUBGRID_BLOCK_ENTITY.get(),
                    (be, state) -> requiredItems((SubgridBlockEntity) be));
            // The grid itself costs nothing: a minimizer makes one for free, and most sizes have no item at all.
            for (Block block : Registration.allSubgridBlocks()) {
                SchematicRequirementRegistries.BLOCKS.register(block, (state, be) -> ItemRequirement.NONE);
            }
        });
    }

    /**
     * What a schematicannon (or a creative instant print: both go through BlockHelper#prepareBlockEntityData) puts in a
     * printed grid. The pieces as they are, each piece's block entity data through the same filter a real block of it
     * gets (a chest piece prints empty, as a real chest does), without Create's kinetic speed, source and network (they
     * belong where the grid was) or the pending piece ticks (game times of where it was saved).
     */
    public static void writeSafe(BlockEntity be, CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag saved = ((SubgridBlockEntity) be).saveCustomOnly(registries);
        saved.remove("scheduledTicks");
        for (Tag t : saved.getList("pieces", Tag.TAG_COMPOUND)) {
            CompoundTag extra = ((CompoundTag) t).getCompound("extra");
            if (!extra.contains("be")) continue;
            CompoundTag safe = safePieceData(be.getLevel(), extra, registries);
            if (safe == null) extra.remove("be");
            else extra.put("be", safe);
        }
        tag.merge(saved);
    }

    /** A piece's block entity data as Create would print it for a real block of its state; null for none. */
    private static CompoundTag safePieceData(Level level, CompoundTag extra, HolderLookup.Provider registries) {
        if (level == null || !extra.contains("state")) return null;
        BlockState state = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), extra.getCompound("state"));
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) return null;
        // Read with no level, as the schematicannon reads the block entities it checks the cost of.
        BlockEntity read = entityBlock.newBlockEntity(BlockPos.ZERO, state);
        if (read == null) return null;
        read.loadCustomOnly(extra.getCompound("be"), registries);
        CompoundTag safe = BlockHelper.prepareBlockEntityData(level, state, read);
        if (safe == null) return null;
        for (String key : new String[] {"id", "x", "y", "z", "Speed", "Source", "Network"}) safe.remove(key);
        return safe;
    }

    /**
     * Turns a grid as Create turns the blocks around it: a schematic printed turned or mirrored (its block entities in
     * Create's SchematicLevel), or a contraption set down in the real level. That one is always rearranged, even
     * unturned (a piston, a gantry): the pieces' saved kinetic data belongs where the grid was.
     * A contraption turned about X or Z (a bearing on a wall) leaves the contents as they were: vanilla states only turn
     * about Y, and the grid's cells would need a turn of their own.
     */
    public static void transform(BlockEntity be, StructureTransform transform) {
        SubgridBlockEntity grid = (SubgridBlockEntity) be;
        Rotation rotation = transform.rotationAxis == Direction.Axis.Y && transform.rotation != null
                ? transform.rotation : Rotation.NONE;
        Mirror mirror = transform.mirror == null ? Mirror.NONE : transform.mirror;
        if (be.getLevel() instanceof ServerLevel) grid.transformMoved(rotation, mirror);
        else if (be.getLevel() != null) grid.transform(rotation, mirror);
    }

    /**
     * What printing a grid's pieces costs: what each one is made of, as for a real block of it (two slabs for a double
     * slab), or for a piece of this mod's own, what breaking it gives back. A piece made of nothing an item places
     * (water, fire) is left out rather than making the whole grid unprintable. Also read off the schematicannon's own
     * copy of the grid, which has no level.
     */
    public static ItemRequirement requiredItems(SubgridBlockEntity grid) {
        List<ItemRequirement.StackRequirement> items = new ArrayList<>();
        for (PlacedPiece piece : grid.getPieces()) {
            ItemRequirement requirement = piece.definition instanceof VanillaBlockPiece
                    ? ItemRequirement.of(piece.definition.renderState(piece), null)
                    : new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, piece.definition.drops(piece));
            if (!requirement.isEmpty() && !requirement.isInvalid()) items.addAll(requirement.getRequiredItems());
        }
        return items.isEmpty() ? ItemRequirement.NONE : new ItemRequirement(items);
    }
}
