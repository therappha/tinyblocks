package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A book explaining each item, handed once to a player on first joining a world, and in the creative tab to get it
 * again. A plain written book: its pages are translation keys ({@link #PAGE_KEY}), read in the reader's own language.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID)
public final class GuideBook {

    /** Page {@code n} from 1 to {@link #PAGES}. */
    public static final String PAGE_KEY = "tinyblocks.guide.page.";
    public static final int PAGES = 11;

    /** In a player's persisted data once they were handed the book, so it survives death and isn't given again. */
    private static final String GIVEN_KEY = "tinyblocks_guide_given";

    private GuideBook() {}

    public static ItemStack create() {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int page = 1; page <= PAGES; page++) pages.add(Filterable.passThrough(Component.translatable(PAGE_KEY + page)));
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough("TinyBlocks Guide"), "TinyBlocks", 0, pages, true));
        return book;
    }

    /** Hands {@code player} the book unless they already had it. */
    public static void giveOnce(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN_KEY)) return;
        persisted.putBoolean(GIVEN_KEY, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack book = create();
        if (!player.addItem(book)) player.drop(book, false);
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) giveOnce(player);
    }
}
