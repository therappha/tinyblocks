package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
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
