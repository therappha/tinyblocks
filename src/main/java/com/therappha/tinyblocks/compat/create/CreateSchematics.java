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
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

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
            // The grid itself costs its own item where its size has one (it gives it back when broken), else nothing.
            for (Block block : Registration.allSubgridBlocks()) {
                Item item = block.asItem();
                ItemRequirement own = item == Items.AIR ? ItemRequirement.NONE
                        : new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, item);
                SchematicRequirementRegistries.BLOCKS.register(block, (state, be) -> own);
            }
        });
    }

    /**
     * What a schematicannon (or a creative instant print: both go through BlockHelper#prepareBlockEntityData) puts in a
     * printed grid. The pieces as they are, each piece's block entity data through the same filter a real block of it
     * gets (a chest piece prints empty, as a real chest does), without Create's kinetic speed, source and network or
     * the piece's own speed (they belong where the grid was) or the pending piece ticks (game times of where it was
     * saved). A piece made of nothing an item places (lava, fire, a portal) is left out, as the schematicannon skips
     * such a real block: printed, it would come for free.
     */
    public static void writeSafe(BlockEntity be, CompoundTag tag, HolderLookup.Provider registries) {
        SubgridBlockEntity grid = (SubgridBlockEntity) be;
        CompoundTag saved = grid.saveCustomOnly(registries);
        saved.remove("scheduledTicks");
        // Saved in the grid's own order: the i-th tag is the i-th piece.
        ListTag pieces = saved.getList("pieces", Tag.TAG_COMPOUND), printed = new ListTag();
        for (int i = 0; i < pieces.size(); i++) {
            CompoundTag piece = pieces.getCompound(i), extra = piece.getCompound("extra");
            CompoundTag safe = extra.contains("be") ? safePieceData(be.getLevel(), extra, registries) : null;
            if (safe == null) extra.remove("be");
            else extra.put("be", safe);
            if (requirement(grid.getPieces().get(i), safe, registries).isInvalid()) continue;
            piece.remove("speed");
            printed.add(piece);
        }
        saved.put("pieces", printed);
        tag.merge(saved);
    }

    /** A piece's block entity data as Create would print it for a real block of its state; null for none. */
    private static CompoundTag safePieceData(Level level, CompoundTag extra, HolderLookup.Provider registries) {
        if (level == null || !extra.contains("state")) return null;
        BlockState state = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), extra.getCompound("state"));
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) return null;
        // In the level the grid's block entity is in, as Create's own (a schematic's) are when their data is filtered.
        BlockEntity read = entityBlock.newBlockEntity(BlockPos.ZERO, state);
        if (read == null) return null;
        read.setLevel(level);
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
     * slab, a filter's item), or for a piece of this mod's own, what breaking it gives back. A piece made of nothing an
     * item places is left out, as it is of the print (writeSafe). Also read off the schematicannon's own copy of the
     * grid, which has no level and whose pieces' data is already the printed one.
     */
    public static ItemRequirement requiredItems(SubgridBlockEntity grid) {
        Level level = grid.getLevel();
        HolderLookup.Provider registries = registries(level);
        List<ItemRequirement.StackRequirement> items = new ArrayList<>();
        for (PlacedPiece piece : grid.getPieces()) {
            CompoundTag data = !piece.extraData.contains("be") ? null
                    : level != null ? safePieceData(level, piece.extraData, registries)
                    : piece.extraData.getCompound("be");
            ItemRequirement requirement = requirement(piece, data, registries);
            if (!requirement.isEmpty() && !requirement.isInvalid()) items.addAll(requirement.getRequiredItems());
        }
        return items.isEmpty() ? ItemRequirement.NONE : new ItemRequirement(items);
    }

    /**
     * What one piece costs: a vanilla piece as Create asks a real block of its state, with a block entity read from
     * the given (printed) data, whose behaviours add what they hold (a filter, a bracket); any other, its drops.
     */
    private static ItemRequirement requirement(PlacedPiece piece, @Nullable CompoundTag data, HolderLookup.Provider registries) {
        if (!(piece.definition instanceof VanillaBlockPiece))
            return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, piece.definition.drops(piece));
        BlockState state = piece.definition.renderState(piece);
        BlockEntity be = state.getBlock() instanceof EntityBlock entityBlock ? entityBlock.newBlockEntity(BlockPos.ZERO, state) : null;
        if (be != null && data != null) be.loadCustomOnly(data, registries);
        return ItemRequirement.of(state, be);
    }

    /** The registries a grid's data is read with; the schematicannon's copy has no level, so the server's. */
    private static HolderLookup.Provider registries(@Nullable Level level) {
        if (level != null) return level.registryAccess();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.registryAccess() : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
}
