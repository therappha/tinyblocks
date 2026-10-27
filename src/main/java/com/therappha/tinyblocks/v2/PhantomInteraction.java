package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lets a menu opened on a phantom block entity pass vanilla's distance check. Phantoms live in the
 * global virtual frame (SubgridBlockEntity#virtualOrigin), nowhere near the player, so
 * Container.stillValidBlockEntity -> Player.canInteractWithBlock would close the menu on the next tick.
 * The grid the player last interacted with is remembered here and {@link com.therappha.tinyblocks.mixin.PlayerMixin} swaps any position
 * inside that grid's virtual cube for the SubgridBlock's real position.
 */
public final class PhantomInteraction {

    private record Entry(BlockPos origin, int size, BlockPos realPos) {
        boolean contains(BlockPos pos) {
            return pos.getX() >= origin.getX() && pos.getX() < origin.getX() + size
                    && pos.getY() >= origin.getY() && pos.getY() < origin.getY() + size
                    && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + size;
        }
    }

    private static final Map<Player, Entry> ACTIVE = new WeakHashMap<>();

    private PhantomInteraction() {}

    public static void remember(Player player, BlockPos virtualOrigin, int gridSize, BlockPos realPos) {
        synchronized (ACTIVE) {
            ACTIVE.put(player, new Entry(virtualOrigin.immutable(), gridSize, realPos.immutable()));
        }
    }

    /** {@code pos} itself unless it lies in the virtual cube of the grid this player is working with. */
    public static BlockPos redirect(Player player, BlockPos pos) {
        synchronized (ACTIVE) {
            Entry entry = ACTIVE.get(player);
            if (entry == null) return pos;
            if (player.containerMenu == player.inventoryMenu) {
                ACTIVE.remove(player);
                return pos;
            }
            return entry.contains(pos) ? entry.realPos() : pos;
        }
    }
}
