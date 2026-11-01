package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.setup.GuideBook;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The guide is handed over once, whole, however many times its reader joins. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class GuideBookGameTest {

    /** Locked by its key: a grid of its size can go, a real block and another size can't. */
    @GameTest(template = "empty_platform", timeoutTicks = 20)
    public void aLockedMinimizerOnlyBreaksGridsOfItsSize(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos ofItsSize = new BlockPos(2, 1, 2), otherSize = new BlockPos(5, 1, 2), real = new BlockPos(2, 1, 5);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, ofItsSize);
        helper.setBlock(otherSize, net.minecraft.world.level.block.Blocks.AIR);
        SubgridBlockEntity.createAt(level, helper.absolutePos(otherSize), 4);
        helper.setBlock(real, net.minecraft.world.level.block.Blocks.STONE);
        var player = SubgridTestSupport.serverPlayer(helper, be);
        var stack = new net.minecraft.world.item.ItemStack(com.therappha.tinyblocks.setup.Registration.MINIMIZER.get());
        MinimizerItem.setGridSize(stack, be.getGridSize());
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, stack);

        helper.assertTrue(!stack.hasFoil(), "an unlocked Minimizer glows");
        helper.assertTrue(MinimizerItem.mayBreak(player, level, helper.absolutePos(real)),
                "an unlocked Minimizer keeps something; main " + player.getMainHandItem() + " off " + player.getOffhandItem());
        MinimizerItem.toggleLock(player);
        helper.assertTrue(stack.hasFoil(), "the key didn't lock it");
        helper.assertTrue(MinimizerItem.mayBreak(player, level, helper.absolutePos(ofItsSize)), "a grid of its size is kept");
        helper.assertTrue(!MinimizerItem.mayBreak(player, level, helper.absolutePos(otherSize)), "a grid of another size can go");
        player.gameMode.destroyBlock(helper.absolutePos(real));
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.STONE, real);

        MinimizerItem.toggleLock(player);
        helper.assertTrue(!stack.hasFoil(), "the second press didn't unlock it");
        player.gameMode.destroyBlock(helper.absolutePos(real));
        helper.assertBlockNotPresent(net.minecraft.world.level.block.Blocks.STONE, real);
        helper.succeed();
    }

    @GameTest(template = "empty_platform", timeoutTicks = 20)
    public void theGuideIsHandedOverOnce(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        var player = SubgridTestSupport.serverPlayer(helper, be);
        player.getInventory().clearContent();
        player.getPersistentData().remove(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);

        GuideBook.giveOnce(player);
        GuideBook.giveOnce(player);

        helper.assertTrue(player.getInventory().countItem(Items.WRITTEN_BOOK) == 1,
                "books handed over: " + player.getInventory().countItem(Items.WRITTEN_BOOK));
        int slot = player.getInventory().findSlotMatchingItem(GuideBook.create());
        helper.assertTrue(slot >= 0, "the book isn't the guide");
        var content = player.getInventory().getItem(slot).get(DataComponents.WRITTEN_BOOK_CONTENT);
        helper.assertTrue(content != null && content.pages().size() == GuideBook.PAGES, "the guide lost pages");
        helper.succeed();
    }
}
