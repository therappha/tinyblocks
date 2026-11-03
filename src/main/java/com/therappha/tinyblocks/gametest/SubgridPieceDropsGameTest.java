package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.network.SubgridMinePiecePayload;
import com.therappha.tinyblocks.setup.SubgridEventHandler;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.TreeMap;

/** What a piece leaves behind when a player mines it: what its loot table says for the tool in hand, and what it held. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridPieceDropsGameTest {

    private static final BlockPos GRID = new BlockPos(2, 2, 2), CELL = new BlockPos(4, 4, 4);

    private static SubgridBlockEntity grid(GameTestHelper helper) {
        helper.setBlock(GRID, Blocks.AIR);
        helper.setBlock(GRID.south(), Blocks.AIR); // the crosshair comes through here
        return SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(GRID), 8);
    }

    /** Standing next to the grid, reached by the mine packet's own handler. */
    private static ServerPlayer miner(GameTestHelper helper, SubgridBlockEntity be, GameType mode, ItemStack tool) {
        // Never joined, so it has no connection to tell of a game mode: it only says which one it is.
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "miner"), net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            public boolean isCreative() {
                return mode == GameType.CREATIVE;
            }
        };
        Vec3 cell = be.realPositionOf(CELL);
        player.moveTo(cell.x, cell.y - player.getEyeHeight(), cell.z + 1.3, 180f, 0f);
        player.setYHeadRot(180f);
        player.yHeadRotO = 180f;
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return player;
    }

    private static Map<String, Integer> dropsAround(GameTestHelper helper, SubgridBlockEntity be) {
        Map<String, Integer> drops = new TreeMap<>();
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(be.getBlockPos()).inflate(3))) {
            drops.merge(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).getPath(), item.getItem().getCount(), Integer::sum);
        }
        return drops;
    }

    private static void mined(GameTestHelper helper, BlockState state, GameType mode, ItemStack tool, String expected) {
        mined(helper, state, null, mode, tool, expected);
    }

    private static void mined(GameTestHelper helper, BlockState state, @javax.annotation.Nullable java.util.function.Consumer<PlacedPiece> setup, GameType mode, ItemStack tool, String expected) {
        SubgridBlockEntity be = grid(helper);
        // Something else in the grid, for it not to go, and the block behind so nothing lands on anything but the floor.
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
        PlacedPiece piece = SubgridTestSupport.placePiece(be, helper.getLevel(), CELL, state);
        ServerPlayer player = miner(helper, be, mode, tool);
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            if (setup != null) setup.accept(piece);
            SubgridEventHandler.handleMinePiece(new SubgridMinePiecePayload(be.getBlockPos(), CELL), player, helper.getLevel());
            helper.assertTrue(be.getPieceAt(CELL.getX(), CELL.getY(), CELL.getZ()) == null, "the piece wasn't mined");
        }).thenIdle(2).thenExecute(() -> helper.assertTrue(dropsAround(helper, be).toString().equals(expected), "dropped " + dropsAround(helper, be) + ", not " + expected)).thenSucceed();
    }

    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void stoneMinedWithAPickaxeDropsCobblestone(GameTestHelper helper) {
        mined(helper, Blocks.STONE.defaultBlockState(), GameType.SURVIVAL, new ItemStack(Items.IRON_PICKAXE), "{cobblestone=1}");
    }

    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void stoneMinedByHandDropsNothing(GameTestHelper helper) {
        mined(helper, Blocks.STONE.defaultBlockState(), GameType.SURVIVAL, ItemStack.EMPTY, "{}");
    }

    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void creativeMiningDropsNothing(GameTestHelper helper) {
        mined(helper, Blocks.DIRT.defaultBlockState(), GameType.CREATIVE, ItemStack.EMPTY, "{}");
    }

    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void silkTouchKeepsTheBlock(GameTestHelper helper) {
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        var silk = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH);
        pick.enchant(silk, 1);
        mined(helper, Blocks.STONE.defaultBlockState(), GameType.SURVIVAL, pick, "{stone=1}");
    }

    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void aChestPieceSpillsWhatItHeld(GameTestHelper helper) {
        SubgridBlockEntity[] grid = new SubgridBlockEntity[1];
        mined(helper, Blocks.CHEST.defaultBlockState(), piece -> {
            grid[0] = (SubgridBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(GRID));
            ((Container) SubgridTestSupport.blockEntityOf(grid[0], piece, helper.getLevel())).setItem(4, new ItemStack(Items.DIAMOND, 3));
        }, GameType.SURVIVAL, ItemStack.EMPTY, "{chest=1, diamond=3}");
    }

    /** A shulker box is the one that keeps what it holds, in the item it drops. */
    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void aShulkerBoxPieceDropsWithWhatItHeldInside(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper);
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
        PlacedPiece box = SubgridTestSupport.placePiece(be, helper.getLevel(), CELL, Blocks.SHULKER_BOX.defaultBlockState().setValue(net.minecraft.world.level.block.ShulkerBoxBlock.FACING, Direction.UP));
        ServerPlayer player = miner(helper, be, GameType.SURVIVAL, new ItemStack(Items.IRON_PICKAXE));
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            ((Container) SubgridTestSupport.blockEntityOf(be, box, helper.getLevel())).setItem(0, new ItemStack(Items.DIAMOND, 5));
            SubgridEventHandler.handleMinePiece(new SubgridMinePiecePayload(be.getBlockPos(), CELL), player, helper.getLevel());
        }).thenIdle(2).thenExecute(() -> {
            var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(be.getBlockPos()).inflate(3));
            helper.assertTrue(dropped.size() == 1 && dropped.get(0).getItem().is(Items.SHULKER_BOX), "dropped " + dropsAround(helper, be));
            var contents = dropped.get(0).getItem().get(net.minecraft.core.component.DataComponents.CONTAINER);
            helper.assertTrue(contents != null && contents.nonEmptyStream().anyMatch(s -> s.is(Items.DIAMOND) && s.getCount() == 5), "the box came out as " + contents);
        }).thenSucceed();
    }

    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void oneDropComesOutWhereThePieceWas(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper);
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(be, helper.getLevel(), CELL, Blocks.DIRT.defaultBlockState());
        ServerPlayer player = miner(helper, be, GameType.SURVIVAL, ItemStack.EMPTY);
        Vec3 where = be.realPositionOf(CELL);
        SubgridEventHandler.handleMinePiece(new SubgridMinePiecePayload(be.getBlockPos(), CELL), player, helper.getLevel());
        var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(be.getBlockPos()).inflate(3));
        helper.assertTrue(dropped.size() == 1 && dropped.get(0).position().distanceTo(where) < 0.5, "dropped at " + dropped + ", the piece was at " + where);
        helper.succeed();
    }
}
