package com.therappha.tinyblocks.menu;

import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.TinyFurnaceDefinition;
import com.therappha.tinyblocks.subgrid.TinyFurnaceDefinition.FurnaceState;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public class TinyFurnaceMenu extends AbstractContainerMenu {

    private final Container furnaceSlots;
    private final ContainerData data;

    /** Server-side: backed by the real FurnaceState. */
    public static TinyFurnaceMenu forPiece(int id, Inventory inv, FurnaceState state, Runnable onChange) {
        Container container = stateContainer(state.items, onChange);
        ContainerData data = stateData(state);
        return new TinyFurnaceMenu(id, inv, container, data);
    }

    /** Client-side: empty placeholders, synced by vanilla. */
    public TinyFurnaceMenu(int id, Inventory inv) {
        this(id, inv, emptyContainer(), new SimpleContainerData(4));
    }

    private TinyFurnaceMenu(int id, Inventory playerInv, Container container, ContainerData data) {
        super(Registration.TINY_FURNACE_MENU.get(), id);
        this.furnaceSlots = container;
        this.data = data;
        checkContainerSize(container, 3);
        checkContainerDataCount(data, 4);

        // Furnace slots
        this.addSlot(new Slot(container, TinyFurnaceDefinition.SLOT_INPUT, 56, 17));
        this.addSlot(new FuelSlot(container, TinyFurnaceDefinition.SLOT_FUEL, 56, 53));
        this.addSlot(new ResultOnlySlot(playerInv.player, container, TinyFurnaceDefinition.SLOT_OUTPUT, 116, 35));

        // Player inventory
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));

        this.addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == TinyFurnaceDefinition.SLOT_OUTPUT) {
            // Output → player inventory
            if (!this.moveItemStackTo(stack, 3, 39, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, original);
        } else if (index < 3) {
            // Furnace slots → player inventory
            if (!this.moveItemStackTo(stack, 3, 39, false)) return ItemStack.EMPTY;
        } else {
            // Player inventory → furnace
            if (AbstractFurnaceBlockEntity.getFuel().containsKey(stack.getItem())) {
                if (!this.moveItemStackTo(stack, TinyFurnaceDefinition.SLOT_FUEL, TinyFurnaceDefinition.SLOT_FUEL + 1, false))
                    return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stack, TinyFurnaceDefinition.SLOT_INPUT, TinyFurnaceDefinition.SLOT_INPUT + 1, false))
                    return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    // ---- Data accessors for the screen ----

    public boolean isLit() { return data.get(0) > 0; }

    public int getBurnProgress() {
        int burnTime = data.get(0);
        int burnTimeTotal = data.get(1);
        return (burnTimeTotal > 0 && burnTime > 0) ? burnTime * 13 / burnTimeTotal : 0;
    }

    public int getCookProgress() {
        int cookTime = data.get(2);
        int cookTimeTotal = data.get(3);
        return cookTimeTotal > 0 ? cookTime * 24 / cookTimeTotal : 0;
    }

    // ---- Helpers ----

    private static Container stateContainer(NonNullList<ItemStack> items, Runnable onChange) {
        return new Container() {
            @Override public int getContainerSize() { return 3; }
            @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
            @Override public ItemStack getItem(int slot) { return items.get(slot); }
            @Override public ItemStack removeItem(int slot, int amount) {
                ItemStack r = net.minecraft.world.ContainerHelper.removeItem(items, slot, amount);
                if (!r.isEmpty()) onChange.run();
                return r;
            }
            @Override public ItemStack removeItemNoUpdate(int slot) {
                ItemStack r = net.minecraft.world.ContainerHelper.takeItem(items, slot);
                if (!r.isEmpty()) onChange.run();
                return r;
            }
            @Override public void setItem(int slot, ItemStack stack) {
                items.set(slot, stack);
                if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
                onChange.run();
            }
            @Override public void setChanged() { onChange.run(); }
            @Override public boolean stillValid(Player player) { return true; }
            @Override public void clearContent() { items.clear(); onChange.run(); }
        };
    }

    private static Container emptyContainer() {
        NonNullList<ItemStack> empty = NonNullList.withSize(3, ItemStack.EMPTY);
        return stateContainer(empty, () -> {});
    }

    private static ContainerData stateData(FurnaceState state) {
        return new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> state.burnTime;
                    case 1 -> state.burnTimeTotal;
                    case 2 -> state.cookTime;
                    case 3 -> state.cookTimeTotal;
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) {
                switch (i) {
                    case 0 -> state.burnTime = v;
                    case 1 -> state.burnTimeTotal = v;
                    case 2 -> state.cookTime = v;
                    case 3 -> state.cookTimeTotal = v;
                }
            }
            @Override public int getCount() { return 4; }
        };
    }

    // ---- Custom slots ----

    private static class FuelSlot extends Slot {
        FuelSlot(Container container, int slot, int x, int y) { super(container, slot, x, y); }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return AbstractFurnaceBlockEntity.getFuel().containsKey(stack.getItem());
        }
    }

    private static class ResultOnlySlot extends Slot {
        private final Player player;
        ResultOnlySlot(Player player, Container container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.player = player;
        }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
