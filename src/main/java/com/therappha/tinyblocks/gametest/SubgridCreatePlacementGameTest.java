package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A Create block clicked with a block in hand inside a subgrid does what it does full size. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridCreatePlacementGameTest {

    private static final BlockState SHAFT_X =
            AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X);

    /** On top of {@code cell}, near its east edge: where a player aims to extend an east-west shaft eastwards. */
    private static BlockHitResult topNearEastEdge(SubgridBlockEntity be, BlockPos cell) {
        double size = be.getGridSize();
        Vec3 point = new Vec3(cell.getX() + 0.9, cell.getY() + 1, cell.getZ() + 0.5).scale(1 / size)
                .add(Vec3.atLowerCornerOf(be.getBlockPos()));
        return new BlockHitResult(point, Direction.UP, be.getBlockPos(), false);
    }

    private static Player holding(GameTestHelper helper, ItemStack stack) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    /** Create's shaft placement helper: a shaft clicked on its side goes on the end of the shaft line, not on top. */
    @GameTest(template = "empty_platform")
    public void aShaftClickedOnItsSideExtendsTheLine(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos clicked = new BlockPos(3, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), clicked, SHAFT_X);
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(4, 4, 4), SHAFT_X);
        Player player = holding(helper, AllBlocks.SHAFT.asStack(4));

        SubgridTestSupport.rightClick(be, player, topNearEastEdge(be, clicked));

        BlockState end = SubgridTestSupport.stateOf(be, new BlockPos(5, 4, 4));
        helper.assertTrue(end != null && end.is(AllBlocks.SHAFT.get())
                        && end.getValue(RotatedPillarKineticBlock.AXIS) == Direction.Axis.X,
                "no east-west shaft at the end of the line: " + end);
        helper.assertTrue(SubgridTestSupport.stateOf(be, clicked.above()) == null,
                "the shaft went on top instead: " + SubgridTestSupport.stateOf(be, clicked.above()));
        helper.assertTrue(player.getMainHandItem().getCount() == 3, "the shaft was not taken from the hand");
        helper.succeed();
    }

    /** The end of the line is past the grid's east edge: the shaft goes into a new grid in the block next to it. */
    @GameTest(template = "empty_platform")
    public void aShaftLineExtendsIntoTheNextBlock(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.AIR);
        int edge = be.getGridSize() - 1;
        BlockPos clicked = new BlockPos(edge, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), clicked, SHAFT_X);
        Player player = holding(helper, AllBlocks.SHAFT.asStack());

        SubgridTestSupport.rightClick(be, player, topNearEastEdge(be, clicked));

        helper.assertTrue(helper.getLevel().getBlockEntity(be.getBlockPos().east()) instanceof SubgridBlockEntity next
                        && SubgridTestSupport.stateOf(next, new BlockPos(0, 4, 4)) != null
                        && SubgridTestSupport.stateOf(next, new BlockPos(0, 4, 4)).is(AllBlocks.SHAFT.get()),
                "no shaft in the first cell of the grid east of it; east block "
                        + helper.getLevel().getBlockState(be.getBlockPos().east()) + ", pieces here "
                        + be.getPieces().stream().map(p -> p.anchor + "=" + p.runtimeState).toList()
                        + ", hand " + player.getMainHandItem());
        helper.succeed();
    }

    /** Andesite casing on a tiny shaft encases it, as on a full-size one. */
    @GameTest(template = "empty_platform")
    public void casingEncasesATinyShaft(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos shaft = new BlockPos(4, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), shaft, SHAFT_X);
        Player player = holding(helper, AllBlocks.ANDESITE_CASING.asStack());

        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.topHit(be, shaft));

        BlockState after = SubgridTestSupport.stateOf(be, shaft);
        helper.assertTrue(after != null && after.is(AllBlocks.ANDESITE_ENCASED_SHAFT.get()),
                "the shaft was not encased: " + after);
        helper.assertTrue(SubgridTestSupport.stateOf(be, shaft.above()) == null,
                "the casing was placed as a block on top instead");
        var phantom = SubgridTestSupport.blockEntityOf(be, be.getPieceAt(shaft.getX(), shaft.getY(), shaft.getZ()), helper.getLevel());
        helper.assertTrue(phantom != null && phantom.getType() == com.simibubi.create.AllBlockEntityTypes.ENCASED_SHAFT.get()
                        && phantom.getBlockState().equals(after),
                "the encased shaft kept the shaft's block entity: " + phantom + " " + (phantom == null ? "" : phantom.getBlockState()));
        helper.succeed();
    }

    /** A block with no use of its own still gets the clicked block placed against it. */
    @GameTest(template = "empty_platform")
    public void aPlainBlockStillGoesOnTheClickedFace(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos stone = new BlockPos(4, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), stone, Blocks.STONE.defaultBlockState());
        Player player = holding(helper, new ItemStack(Items.DIRT));

        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.topHit(be, stone));

        BlockState above = SubgridTestSupport.stateOf(be, stone.above());
        helper.assertTrue(above != null && above.is(Blocks.DIRT), "no dirt on top of the stone: " + above);
        helper.succeed();
    }

    /** The same click by a real (server) player, which other mods' place listeners treat differently from a mock one. */
    @GameTest(template = "empty_platform")
    public void aServerPlayersShaftExtendsTheLine(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos clicked = new BlockPos(3, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), clicked, SHAFT_X);
        net.minecraft.server.level.ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllBlocks.SHAFT.asStack(4));

        SubgridTestSupport.rightClick(be, player, topNearEastEdge(be, clicked));

        BlockState end = SubgridTestSupport.stateOf(be, clicked.east());
        helper.assertTrue(end != null && end.is(AllBlocks.SHAFT.get()),
                "no shaft at the end of the line: " + end + "; on top: " + SubgridTestSupport.stateOf(be, clicked.above()));
        helper.succeed();
    }

    /**
     * A cogwheel clicked onto a large cogwheel goes diagonal to it, where the two mesh — Create's cogwheel item
     * does that in its onItemUseFirst, before the clicked block's own use.
     */
    @GameTest(template = "empty_platform")
    public void aCogwheelOnALargeCogwheelGoesWhereTheyMesh(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos large = new BlockPos(4, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), large,
                AllBlocks.LARGE_COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Y));
        Player player = holding(helper, AllBlocks.COGWHEEL.asStack(4));

        SubgridTestSupport.rightClick(be, player, topNearEastEdge(be, large));

        helper.assertTrue(SubgridTestSupport.stateOf(be, large.above()) == null,
                "the cogwheel went straight on top: " + SubgridTestSupport.stateOf(be, large.above()));
        var placed = be.getPieces().stream().filter(p -> !p.anchor.equals(large)).map(p -> p.anchor + "=" + p.runtimeState).toList();
        helper.assertTrue(placed.size() == 1 && placed.get(0).contains("create:cogwheel"),
                "expected one cogwheel placed diagonally, got " + placed);
        helper.succeed();
    }

    /** Create's belt connector clicked on one tiny shaft, then on another in line: a belt runs between them. */
    @GameTest(template = "empty_platform", timeoutTicks = 60)
    public void aBeltConnectsTwoTinyShafts(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos first = new BlockPos(1, 3, 4);
        BlockPos second = new BlockPos(5, 3, 4);
        BlockState shaftZ = AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z);
        SubgridTestSupport.placePiece(be, helper.getLevel(), first, shaftZ);
        SubgridTestSupport.placePiece(be, helper.getLevel(), second, shaftZ);
        net.minecraft.server.level.ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.BELT_CONNECTOR.asStack(4));

        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.faceHit(be, first, Direction.SOUTH));
        helper.startSequence()
                .thenIdle(6) // the connector's cooldown after the first shaft
                .thenExecute(() -> {
                    player.getCooldowns().removeCooldown(AllItems.BELT_CONNECTOR.get());
                    SubgridTestSupport.rightClick(be, player, SubgridTestSupport.faceHit(be, second, Direction.SOUTH));
                    java.util.List<String> cells = new java.util.ArrayList<>();
                    for (int x = first.getX(); x <= second.getX(); x++) {
                        BlockState state = SubgridTestSupport.stateOf(be, new BlockPos(x, 3, 4));
                        cells.add(x + "=" + (state == null ? "empty" : state.getBlock() + (state.is(AllBlocks.BELT.get())
                                ? "[" + state.getValue(com.simibubi.create.content.kinetics.belt.BeltBlock.PART) + "]" : "")));
                    }
                    for (int x = first.getX(); x <= second.getX(); x++) {
                        BlockState state = SubgridTestSupport.stateOf(be, new BlockPos(x, 3, 4));
                        helper.assertTrue(state != null && state.is(AllBlocks.BELT.get()), "no belt all along: " + cells
                                + ", hand " + player.getMainHandItem());
                    }
                })
                .thenSucceed();
    }

    /** A 1/9 Minimizer makes 9-grids: its middle cell is the block's centre, where a shaft lines up with a real one. */
    @GameTest(template = "empty_platform")
    public void aNineGridHoldsACentredShaft(GameTestHelper helper) {
        BlockPos real = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        SubgridBlockEntity be = SubgridBlockEntity.createAt(helper.getLevel(), real, 9);
        helper.assertTrue(be != null && be.getGridSize() == 9
                        && helper.getLevel().getBlockState(real).is(com.therappha.tinyblocks.setup.Registration.SUBGRID_BLOCK_9.get()),
                "no 9-grid was made: " + helper.getLevel().getBlockState(real));
        BlockPos middle = new BlockPos(4, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), middle, SHAFT_X);
        helper.assertTrue(be.realPositionOf(middle).equals(Vec3.atCenterOf(real)),
                "the middle cell's centre " + be.realPositionOf(middle) + " is not the block's " + Vec3.atCenterOf(real));
        helper.succeed();
    }

    /** In an odd grid (1/5, 1/7, 1/9) the connector goes on, and its socket is the middle cell, on the real shaft's axis. */
    @GameTest(template = "empty_platform")
    public void aConnectorFitsOddGridsWithItsSocketOnTheAxis(GameTestHelper helper) {
        int[] sizes = {5, 7, 9};
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            BlockPos at = new BlockPos(1 + 2 * i, 2, 2);
            helper.setBlock(at, Blocks.AIR);
            SubgridBlockEntity be = SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(at), size);
            boolean placed = com.therappha.tinyblocks.compat.create.ConnectorPlacement.place(be, helper.getLevel(), Direction.EAST);
            BlockPos socket = com.therappha.tinyblocks.compat.create.ShaftConnectorPiece.socket(Direction.EAST, size);
            int middle = size / 2;
            helper.assertTrue(placed, "no connector went on a 1/" + size + " grid");
            helper.assertTrue(socket.getY() == middle && socket.getZ() == middle && middle * 2 + 1 == size,
                    "1/" + size + ": socket " + socket + " is not on the middle line");
        }
        helper.succeed();
    }

    /** A tiny motor on one end of a tiny belt turns the whole belt, its far end included. */
    @GameTest(template = "empty_platform", timeoutTicks = 300)
    public void aTinyMotorTurnsTheWholeBelt(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos first = new BlockPos(1, 3, 4);
        BlockPos second = new BlockPos(5, 3, 4);
        BlockState shaftZ = AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z);
        SubgridTestSupport.placePiece(be, helper.getLevel(), first, shaftZ);
        SubgridTestSupport.placePiece(be, helper.getLevel(), second, shaftZ);
        net.minecraft.server.level.ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.BELT_CONNECTOR.asStack(4));

        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.faceHit(be, first, Direction.SOUTH));
        helper.startSequence()
                .thenIdle(6)
                .thenExecute(() -> {
                    player.getCooldowns().removeCooldown(AllItems.BELT_CONNECTOR.get());
                    SubgridTestSupport.rightClick(be, player, SubgridTestSupport.faceHit(be, second, Direction.SOUTH));
                    // A motor behind the first end, along its shaft.
                    SubgridTestSupport.placePiece(be, helper.getLevel(), first.north(),
                            AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.SOUTH));
                })
                .thenIdle(120)
                .thenExecute(() -> {
                    java.util.List<String> speeds = new java.util.ArrayList<>();
                    boolean all = true;
                    for (int x = first.getX(); x <= second.getX(); x++) {
                        var piece = be.getPieceAt(x, 3, 4);
                        var phantom = piece == null ? null : SubgridTestSupport.blockEntityOf(be, piece, helper.getLevel());
                        float speed = phantom instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k ? k.getSpeed() : Float.NaN;
                        speeds.add(x + "=" + speed + (phantom instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity belt
                                ? "(len " + belt.beltLength + ", ctrl " + belt.getController() + ")" : ""));
                        all &= speed != 0 && !Float.isNaN(speed);
                    }
                    helper.assertTrue(all, "belt speeds along it: " + speeds);
                })
                .thenSucceed();
    }

    /** Speeds of the cells from {@code first} to {@code second} along x, for messages; and whether all turn. */
    private static String beltSpeeds(GameTestHelper helper, SubgridBlockEntity be, BlockPos first, BlockPos second, boolean[] allTurn) {
        java.util.List<String> speeds = new java.util.ArrayList<>();
        allTurn[0] = true;
        for (int x = first.getX(); x <= second.getX(); x++) {
            var piece = be.getPieceAt(x, first.getY(), first.getZ());
            var phantom = piece == null ? null : SubgridTestSupport.blockEntityOf(be, piece, helper.getLevel());
            float speed = phantom instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k ? k.getSpeed() : Float.NaN;
            speeds.add(x + "=" + speed);
            allTurn[0] &= speed != 0 && !Float.isNaN(speed);
        }
        return speeds.toString();
    }

    /**
     * The world is left and joined again (the chunk unloads, the grid is saved and loaded back): the belt turns as
     * before, end to end.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 400)
    public void aTinyBeltStillTurnsAfterTheGridIsReloaded(GameTestHelper helper) {
        SubgridBlockEntity[] grid = {SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2))};
        BlockPos first = new BlockPos(1, 3, 4);
        BlockPos second = new BlockPos(5, 3, 4);
        BlockState shaftZ = AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z);
        SubgridTestSupport.placePiece(grid[0], helper.getLevel(), first, shaftZ);
        SubgridTestSupport.placePiece(grid[0], helper.getLevel(), second, shaftZ);
        net.minecraft.server.level.ServerPlayer player = SubgridTestSupport.serverPlayer(helper, grid[0]);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.BELT_CONNECTOR.asStack(4));
        SubgridTestSupport.rightClick(grid[0], player, SubgridTestSupport.faceHit(grid[0], first, Direction.SOUTH));
        boolean[] turns = new boolean[1];

        helper.startSequence()
                .thenIdle(6)
                .thenExecute(() -> {
                    player.getCooldowns().removeCooldown(AllItems.BELT_CONNECTOR.get());
                    SubgridTestSupport.rightClick(grid[0], player, SubgridTestSupport.faceHit(grid[0], second, Direction.SOUTH));
                    SubgridTestSupport.placePiece(grid[0], helper.getLevel(), first.north(),
                            AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.SOUTH));
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    String before = beltSpeeds(helper, grid[0], first, second, turns);
                    helper.assertTrue(turns[0], "the belt didn't turn before the reload: " + before);
                    // Unload and load back, as leaving the world and joining again does.
                    var level = helper.getLevel();
                    BlockPos pos = grid[0].getBlockPos();
                    net.minecraft.nbt.CompoundTag saved = grid[0].saveWithFullMetadata(level.registryAccess());
                    grid[0].onChunkUnloaded();
                    level.removeBlockEntity(pos);
                    var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess());
                    level.setBlockEntity(loaded);
                    grid[0] = (SubgridBlockEntity) level.getBlockEntity(pos);
                })
                .thenIdle(150)
                .thenExecute(() -> {
                    String after = beltSpeeds(helper, grid[0], first, second, turns);
                    helper.assertTrue(turns[0], "after the reload the belt turns as " + after);
                })
                .thenSucceed();
    }

    /** A belt from one grid into the grid next to it, of the same size: a tiny motor at one end turns it all, both grids. */
    @GameTest(template = "empty_platform", timeoutTicks = 400)
    public void aTinyBeltAcrossTwoGridsTurnsEndToEnd(GameTestHelper helper) {
        SubgridBlockEntity a = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.AIR);
        SubgridBlockEntity b = SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 2)), a.getGridSize());
        BlockPos first = new BlockPos(5, 3, 4);  // in A
        BlockPos second = new BlockPos(2, 3, 4); // in B, past A's east edge
        BlockState shaftZ = AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z);
        SubgridTestSupport.placePiece(a, helper.getLevel(), first, shaftZ);
        SubgridTestSupport.placePiece(b, helper.getLevel(), second, shaftZ);
        net.minecraft.server.level.ServerPlayer player = SubgridTestSupport.serverPlayer(helper, a);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.BELT_CONNECTOR.asStack(4));
        SubgridTestSupport.rightClick(a, player, SubgridTestSupport.faceHit(a, first, Direction.SOUTH));

        helper.startSequence()
                .thenIdle(6)
                .thenExecute(() -> {
                    player.getCooldowns().removeCooldown(AllItems.BELT_CONNECTOR.get());
                    SubgridTestSupport.rightClick(b, player, SubgridTestSupport.faceHit(b, second, Direction.SOUTH));
                    SubgridTestSupport.placePiece(a, helper.getLevel(), first.north(),
                            AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.SOUTH));
                })
                .thenIdle(150)
                .thenExecute(() -> {
                    java.util.List<String> cells = new java.util.ArrayList<>();
                    boolean all = true;
                    int size = a.getGridSize();
                    for (int x = first.getX(); x < size + second.getX() + 1; x++) {
                        SubgridBlockEntity grid = x < size ? a : b;
                        var piece = grid.getPieceAt(x % size, 3, 4);
                        var phantom = piece == null ? null : SubgridTestSupport.blockEntityOf(grid, piece, helper.getLevel());
                        float speed = phantom instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k ? k.getSpeed() : Float.NaN;
                        cells.add((x < size ? "A" : "B") + (x % size) + "=" + (piece == null ? "none" : speed));
                        all &= speed != 0 && !Float.isNaN(speed);
                    }
                    helper.assertTrue(all, "belt across two grids: " + cells);
                })
                .thenSucceed();
    }
}
