package com.therappha.tinyblocks.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * What SpaceDiff compares of Create's block entities: how fast it turns, on a belt which part of which belt it is, and
 * what it holds, through its item handler: a depot's item, a basin's, what a deployer has in hand.
 */
public final class CreateFacts {

    private CreateFacts() {}

    public static void add(BlockEntity be, String prefix, Map<String, String> facts) {
        if (be instanceof KineticBlockEntity kinetic) facts.put(prefix + "speed", Float.toString(kinetic.getSpeed()));
        if (be instanceof BeltBlockEntity belt) facts.put(prefix + "belt", belt.index + " of " + belt.beltLength);
        if (be instanceof SmartBlockEntity && !(be instanceof BeltBlockEntity) && be.getLevel() != null) {
            IItemHandler handler = be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, null);
            if (handler != null) facts.put(prefix + "held", held(handler));
        }
    }

    /** By item and not by slot: which slot of a basin an item is in says nothing. */
    private static String held(IItemHandler handler) {
        Map<String, Integer> totals = new TreeMap<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
        }
        return totals.toString();
    }
}
