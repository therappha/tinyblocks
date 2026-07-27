package com.therappha.tinyblocks.subgrid;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.menu.TinyFurnaceMenu;
import com.therappha.tinyblocks.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

public class TinyFurnaceDefinition extends PieceDefinition {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;

    public static final TinyFurnaceDefinition INSTANCE = new TinyFurnaceDefinition();

    private TinyFurnaceDefinition() {
        super(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "tiny_furnace"),
              new net.minecraft.core.Vec3i(1, 1, 1));
    }

    // -------------------------------------------------------------------------
    // FurnaceState — in-memory runtime state, lives in PlacedPiece.runtimeState
    // -------------------------------------------------------------------------

    public static class FurnaceState {
        public final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
        public int cookTime;
        public int cookTimeTotal = 200;
        public int burnTime;
        public int burnTimeTotal;
    }

    public static FurnaceState getState(PlacedPiece piece) {
        if (!(piece.runtimeState instanceof FurnaceState)) {
            piece.runtimeState = new FurnaceState();
        }
        return (FurnaceState) piece.runtimeState;
    }

    // -------------------------------------------------------------------------
    // PieceDefinition overrides
    // -------------------------------------------------------------------------

    @Override
    public BlockState renderState(Direction.Axis axis) {
        return Blocks.FURNACE.defaultBlockState();
    }

    @Override public float destroyTime() { return 3.5f; }
    @Override public boolean requiresCorrectTool() { return true; }

    @Override
    public List<ItemStack> drops(PlacedPiece piece) {
        return List.of(new ItemStack(Registration.TINY_FURNACE_ITEM.get()));
    }

    @Override
    public void onLoaded(PlacedPiece piece, HolderLookup.Provider registries) {
        FurnaceState state = new FurnaceState();
        for (int i = 0; i < 3; i++) {
            String key = "Slot" + i;
            if (piece.extraData.contains(key)) {
                int slot = i;
                ItemStack.parse(registries, piece.extraData.getCompound(key))
                    .ifPresent(stack -> state.items.set(slot, stack));
            }
        }
        state.cookTime = piece.extraData.getInt("CookTime");
        state.cookTimeTotal = piece.extraData.contains("CookTimeTotal") ? piece.extraData.getInt("CookTimeTotal") : 200;
        state.burnTime = piece.extraData.getInt("BurnTime");
        state.burnTimeTotal = piece.extraData.getInt("BurnTimeTotal");
        piece.runtimeState = state;
    }

    @Override
    public void onSaving(PlacedPiece piece, HolderLookup.Provider registries) {
        FurnaceState state = getState(piece);
        for (int i = 0; i < 3; i++) {
            ItemStack stack = state.items.get(i);
            if (!stack.isEmpty()) {
                piece.extraData.put("Slot" + i, stack.save(registries));
            } else {
                piece.extraData.remove("Slot" + i);
            }
        }
        piece.extraData.putInt("CookTime", state.cookTime);
        piece.extraData.putInt("CookTimeTotal", state.cookTimeTotal);
        piece.extraData.putInt("BurnTime", state.burnTime);
        piece.extraData.putInt("BurnTimeTotal", state.burnTimeTotal);
    }

    @Override
    public boolean requiresTick() { return true; }

    @Override
    public boolean tick(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be) {
        FurnaceState state = getState(piece);
        boolean changed = false;

        ItemStack inputItem = state.items.get(SLOT_INPUT);
        ItemStack fuelItem = state.items.get(SLOT_FUEL);

        Optional<RecipeHolder<SmeltingRecipe>> recipeOpt = findRecipe(level, inputItem);
        ItemStack result = recipeOpt.map(h -> h.value().getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
        boolean canOutput = !result.isEmpty() && canAcceptOutput(state, result);

        // Consume fuel when needed
        if (state.burnTime == 0 && canOutput && !fuelItem.isEmpty()) {
            int fuelDuration = AbstractFurnaceBlockEntity.getFuel().getOrDefault(fuelItem.getItem(), 0);
            if (fuelDuration > 0) {
                state.burnTimeTotal = fuelDuration;
                state.burnTime = fuelDuration;
                fuelItem.shrink(1);
                if (fuelItem.isEmpty()) state.items.set(SLOT_FUEL, ItemStack.EMPTY);
                changed = true;
            }
        }

        if (state.burnTime > 0) {
            state.burnTime--;
            if (canOutput) {
                state.cookTimeTotal = recipeOpt.get().value().getCookingTime();
                state.cookTime++;
                if (state.cookTime >= state.cookTimeTotal) {
                    state.cookTime = 0;
                    produceOutput(state, result.copy());
                    // Recheck after producing
                    canOutput = canAcceptOutput(state, result);
                }
            } else {
                state.cookTime = Math.max(0, state.cookTime - 2);
            }
            changed = true;
        } else if (state.cookTime > 0) {
            state.cookTime = Math.max(0, state.cookTime - 2);
            changed = true;
        }

        return changed;
    }

    @Override
    public InteractionResult onUse(PlacedPiece piece, Level level, BlockPos subgridPos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (!(level.getBlockEntity(subgridPos) instanceof SubgridBlockEntity be))
            return InteractionResult.PASS;

        FurnaceState state = getState(piece);
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> TinyFurnaceMenu.forPiece(id, inv, state, () -> be.setChanged()),
            Component.translatable("container.tinyblocks.tiny_furnace")
        ));
        return InteractionResult.SUCCESS;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Optional<RecipeHolder<SmeltingRecipe>> findRecipe(ServerLevel level, ItemStack input) {
        if (input.isEmpty()) return Optional.empty();
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level);
    }

    private boolean canAcceptOutput(FurnaceState state, ItemStack result) {
        ItemStack output = state.items.get(SLOT_OUTPUT);
        if (output.isEmpty()) return true;
        if (!ItemStack.isSameItemSameComponents(output, result)) return false;
        int combined = output.getCount() + result.getCount();
        return combined <= output.getMaxStackSize();
    }

    private void produceOutput(FurnaceState state, ItemStack result) {
        ItemStack output = state.items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            state.items.set(SLOT_OUTPUT, result);
        } else {
            output.grow(result.getCount());
        }
        state.items.get(SLOT_INPUT).shrink(1);
    }
}
