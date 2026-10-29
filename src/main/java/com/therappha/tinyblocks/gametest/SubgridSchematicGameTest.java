package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * A grid in Create's schematics (CreateSchematics): what the schematicannon prints of it keeps the pieces but not a
 * chest piece's items, the kinetic speeds or the pending ticks; a grid saved and printed turned, through the same
 * calls the schematicannon makes, has its pieces turned and its cog driven by its motor; and printing it costs its
 * pieces, not the grid.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridSchematicGameTest {

    private static final BlockPos MOTOR = new BlockPos(4, 3, 5), COG = new BlockPos(5, 3, 5);
    private static final BlockPos CHEST = new BlockPos(1, 0, 1), STONE = new BlockPos(3, 0, 3), SLAB = new BlockPos(0, 0, 6);
    private static final BlockPos PORTAL = new BlockPos(6, 0, 6);

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void theSafeDataKeepsThePiecesOnly(GameTestHelper helper) {
        SubgridBlockEntity be = built(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(spinning(be, level, COG), "the cog doesn't turn before saving"))
                .thenExecute(() -> {
                    be.scheduleTick(STONE.getX(), STONE.getY(), STONE.getZ(), level.getGameTime() + 1000);
                    CompoundTag safe = BlockHelper.prepareBlockEntityData(level, be.getBlockState(), be);
                    helper.assertTrue(safe != null, "nothing of the grid is printed");
                    helper.assertTrue(!safe.contains("scheduledTicks"), "the pending ticks are printed");
                    ListTag pieces = safe.getList("pieces", Tag.TAG_COMPOUND);
                    helper.assertTrue(pieces.size() == be.getPieces().size(), "printed " + pieces.size() + " pieces of " + be.getPieces().size());
                    CompoundTag chest = pieceData(pieces, CHEST), motor = pieceData(pieces, MOTOR), cog = pieceData(pieces, COG);
                    helper.assertTrue(chest != null && !chest.getCompound("extra").getCompound("be").contains("Items"),
                            "the chest piece's items are printed: " + chest);
                    helper.assertTrue(motor != null && motor.getCompound("extra").contains("be"), "the motor's own data is gone: " + motor);
                    for (CompoundTag kinetic : new CompoundTag[] {motor, cog}) {
                        CompoundTag data = kinetic.getCompound("extra").getCompound("be");
                        helper.assertTrue(!data.contains("Speed") && !data.contains("Source") && !data.contains("Network"),
                                "a kinetic piece's speed is printed: " + data);
                    }
                })
                .thenSucceed();
    }

    /** Saved into a structure template and printed a quarter turn clockwise as the schematicannon does it. */
    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void aGridPrintedTurnedKeepsWorking(GameTestHelper helper) {
        SubgridBlockEntity source = built(helper, new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(3, 1, 3));
        Map<String, SubgridBlockEntity> printed = new HashMap<>();
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    StructureTemplate template = new StructureTemplate();
                    template.fillFromWorld(level, source.getBlockPos(), new Vec3i(1, 1, 1), false, null);
                    printed.put("grid", print(level, template, target, Rotation.CLOCKWISE_90));
                })
                .thenExecute(() -> {
                    SubgridBlockEntity be = printed.get("grid");
                    helper.assertTrue(be != null, "no grid printed");
                    helper.assertTrue(be.getPieces().size() == source.getPieces().size(),
                            "printed " + be.getPieces().size() + " pieces of " + source.getPieces().size());
                    BlockState motor = SubgridTestSupport.stateOf(be, new BlockPos(2, 3, 4));
                    helper.assertTrue(motor != null && AllBlocks.CREATIVE_MOTOR.has(motor)
                            && motor.getValue(DirectionalKineticBlock.FACING) == Direction.SOUTH, "printed motor: " + motor);
                    BlockState cog = SubgridTestSupport.stateOf(be, new BlockPos(2, 3, 5));
                    helper.assertTrue(cog != null && AllBlocks.COGWHEEL.has(cog)
                            && cog.getValue(RotatedPillarKineticBlock.AXIS) == Direction.Axis.Z, "printed cog: " + cog);
                    PlacedPiece chest = be.getPieceAt(6, 0, 1);
                    helper.assertTrue(chest != null && SubgridTestSupport.stateOf(be, chest.anchor).getValue(ChestBlock.FACING) == Direction.WEST,
                            "printed chest: " + (chest == null ? null : SubgridTestSupport.stateOf(be, chest.anchor)));
                    helper.assertTrue(SubgridTestSupport.blockEntityOf(be, chest, level) instanceof ChestBlockEntity c && c.isEmpty(),
                            "the printed chest piece isn't empty");
                })
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(spinning(printed.get("grid"), level, new BlockPos(2, 3, 5)),
                        "the printed cog doesn't turn with its motor"))
                .thenSucceed();
    }

    /** As the schematicannon asks it, of its own copy of the grid with no level: the pieces, not the grid. */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void printingCostsThePieces(GameTestHelper helper) {
        SubgridBlockEntity be = built(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    BlockState state = be.getBlockState();
                    BlockEntity copy = ((EntityBlock) state.getBlock()).newBlockEntity(be.getBlockPos(), state);
                    copy.loadWithComponents(BlockHelper.prepareBlockEntityData(level, state, be), level.registryAccess());
                    for (BlockEntity asked : new BlockEntity[] {be, copy}) {
                        ItemRequirement requirement = ItemRequirement.of(state, asked);
                        helper.assertTrue(!requirement.isInvalid(), "the grid can't be printed");
                        Map<Item, Integer> counts = new HashMap<>();
                        for (ItemRequirement.StackRequirement r : requirement.getRequiredItems())
                            counts.merge(r.stack.getItem(), r.stack.getCount(), Integer::sum);
                        Map<Item, Integer> expected = Map.of(Items.CHEST, 1, Items.STONE, 1, Items.OAK_SLAB, 2,
                                AllBlocks.COGWHEEL.get().asItem(), 1, AllBlocks.CREATIVE_MOTOR.get().asItem(), 1);
                        helper.assertTrue(counts.equals(expected), "printing costs " + counts + ", not " + expected);
                    }
                })
                .thenSucceed();
    }

    /** A grid with a motor driving a cog, a chest piece holding something, a stone, a double slab and an end portal. */
    private static SubgridBlockEntity built(GameTestHelper helper, BlockPos pos) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, pos);
        ServerLevel level = helper.getLevel();
        SubgridTestSupport.placePiece(be, level, COG,
                AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        SubgridTestSupport.placePiece(be, level, MOTOR,
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        PlacedPiece chest = SubgridTestSupport.placePiece(be, level, CHEST,
                Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        ((ChestBlockEntity) SubgridTestSupport.blockEntityOf(be, chest, level)).setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
        SubgridTestSupport.placePiece(be, level, STONE, Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(be, level, SLAB, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
        // Nothing places an end portal: left out of the cost, not making the grid unprintable.
        SubgridTestSupport.placePiece(be, level, PORTAL, Blocks.END_PORTAL.defaultBlockState());
        return be;
    }

    /**
     * What SchematicPrinter#loadSchematic and the schematicannon do with one block of a template: placed into Create's
     * SchematicLevel turned, its block entity turned by a StructureTransform, then its safe data placed in the level.
     */
    private static SubgridBlockEntity print(ServerLevel level, StructureTemplate template, BlockPos target, Rotation rotation) {
        SchematicLevel schematic = new SchematicLevel(target, level);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE);
        template.placeInWorld(schematic, target, target, settings, schematic.getRandom(), 2);
        StructureTransform transform = new StructureTransform(settings.getRotationPivot(), Direction.Axis.Y,
                settings.getRotation(), settings.getMirror());
        for (BlockEntity be : schematic.getBlockEntities()) transform.apply(be);
        BlockState state = schematic.getBlockState(target);
        CompoundTag data = BlockHelper.prepareBlockEntityData(level, state, schematic.getBlockEntity(target));
        BlockHelper.placeSchematicBlock(level, state, target, ItemStack.EMPTY, data);
        return level.getBlockEntity(target) instanceof SubgridBlockEntity be ? be : null;
    }

    private static CompoundTag pieceData(ListTag pieces, BlockPos anchor) {
        for (Tag t : pieces) {
            CompoundTag p = (CompoundTag) t;
            if (p.getInt("ax") == anchor.getX() && p.getInt("ay") == anchor.getY() && p.getInt("az") == anchor.getZ()) return p;
        }
        return null;
    }

    private static boolean spinning(SubgridBlockEntity be, ServerLevel level, BlockPos cell) {
        PlacedPiece piece = be.getPieceAt(cell.getX(), cell.getY(), cell.getZ());
        return piece != null && SubgridTestSupport.blockEntityOf(be, piece, level) instanceof KineticBlockEntity k && k.getSpeed() != 0;
    }
}
