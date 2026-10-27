package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * "Minimize me": a player holding a Minimizer shrinks to its size — 1/7 of a block tall at 1/7 — and back. Size and
 * hitbox, walking speed, jump height and step height all scale together, through vanilla's own attributes (so it
 * lasts through a relog until undone); reach stays, to keep building.
 */
public final class MinimizeMe {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "minimize_me");

    /** The attributes scaled, each by how much it moves with the size: jump height goes as the square of jump speed. */
    private record Scaled(Holder<Attribute> attribute, boolean squareRoot) {}

    private static final List<Scaled> SCALED = List.of(
            new Scaled(Attributes.SCALE, false),
            new Scaled(Attributes.MOVEMENT_SPEED, false),
            new Scaled(Attributes.STEP_HEIGHT, false),
            new Scaled(Attributes.JUMP_STRENGTH, true));

    private MinimizeMe() {}

    public static boolean isMinimized(Player player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        return scale != null && scale.getModifier(ID) != null;
    }

    /** Back to full size if minimized; else shrinks to the size of a Minimizer in hand. Says what it did. */
    public static void toggle(Player player) {
        if (isMinimized(player)) {
            restore(player);
            player.displayClientMessage(Component.translatable("tinyblocks.minimize_me.restored"), true);
            return;
        }
        ItemStack minimizer = player.getMainHandItem().getItem() instanceof MinimizerItem ? player.getMainHandItem() : player.getOffhandItem();
        if (!(minimizer.getItem() instanceof MinimizerItem item)) return;
        int size = item.gridSize(minimizer);
        if (size <= MinimizerItem.NORMAL_SIZE) return;
        minimize(player, size);
        player.displayClientMessage(Component.translatable("tinyblocks.minimize_me.minimized", size), true);
    }

    /** Shrinks {@code player} to 1/{@code size}. */
    public static void minimize(Player player, int size) {
        restore(player);
        double factor = 1.0 / size;
        for (Scaled scaled : SCALED) {
            AttributeInstance instance = player.getAttribute(scaled.attribute());
            if (instance == null) continue;
            double by = scaled.squareRoot() ? Math.sqrt(factor) : factor;
            instance.addPermanentModifier(new AttributeModifier(ID, by - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    public static void restore(Player player) {
        for (Scaled scaled : SCALED) {
            AttributeInstance instance = player.getAttribute(scaled.attribute());
            if (instance != null) instance.removeModifier(ID);
        }
    }
}
