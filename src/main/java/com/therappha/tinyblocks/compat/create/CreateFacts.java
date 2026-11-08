package com.therappha.tinyblocks.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.therappha.tinyblocks.mixin.create.KineticNetworkAccessor;
import com.therappha.tinyblocks.mixin.create.TorquePropagatorAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * What SpaceDiff compares of Create's block entities: how fast it turns, the stress its network carries against what it
 * can, and whether it is overstressed; on a belt which part of which belt it is and what lies on that
 * segment; which multiblock it is part of (a tank, a vault: Structures); and what it holds, through its item handler: a
 * depot's item, a basin's, what a deployer has in hand.
 */
public final class CreateFacts {

    private CreateFacts() {}

    public static void add(BlockEntity be, String prefix, Map<String, String> facts) {
        if (be instanceof KineticBlockEntity kinetic) {
            facts.put(prefix + "speed", Float.toString(kinetic.getSpeed()));
            facts.put(prefix + "stress", stress(kinetic));
            facts.put(prefix + "overstressed", Boolean.toString(kinetic.isOverStressed()));
        }
        if (be instanceof BeltBlockEntity belt) {
            facts.put(prefix + "belt", belt.index + " of " + belt.beltLength);
            facts.put(prefix + "on_belt", onSegment(belt));
        }
        if (be instanceof IMultiBlockEntityContainer part) facts.put(prefix + "structure", Structures.of(be, part));
        if (be instanceof SmartBlockEntity && !(be instanceof BeltBlockEntity) && be.getLevel() != null) {
            IItemHandler handler = be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, null);
            if (handler != null) facts.put(prefix + "held", held(handler));
        }
    }

    /**
     * The stress on the network {@code be} is in, against what it can take, as the network itself would count it now.
     * Not what the block was last told: after a reload that depends on which block of the network started first,
     * real or tiny alike, until the network next changes. Counted without the network's own pruning of members no
     * longer there, which would change it.
     */
    private static String stress(KineticBlockEntity be) {
        Map<Long, KineticNetwork> networks = be.network == null || be.getLevel() == null ? null
                : TorquePropagatorAccessor.tinyblocks$networks().get(KineticBridge.networkLevel(be.getLevel()));
        KineticNetwork network = networks == null ? null : networks.get(be.network);
        if (network == null) return "none";
        float stress = ((KineticNetworkAccessor) network).tinyblocks$unloadedStress();
        float capacity = ((KineticNetworkAccessor) network).tinyblocks$unloadedCapacity();
        for (Map.Entry<KineticBlockEntity, Float> member : network.members.entrySet()) {
            if (present(member.getKey())) stress += member.getValue() * Math.abs(member.getKey().getTheoreticalSpeed());
        }
        for (Map.Entry<KineticBlockEntity, Float> source : network.sources.entrySet()) {
            if (present(source.getKey())) capacity += source.getValue() * Math.abs(source.getKey().getGeneratedSpeed());
        }
        return stress + " of " + capacity;
    }

    /** Create's own test for a member still there, without loading a chunk to ask (a phantom's virtual position). */
    private static boolean present(KineticBlockEntity be) {
        return be.getLevel() != null && be.getLevel().isLoaded(be.getBlockPos()) && be.getLevel().getBlockEntity(be.getBlockPos()) == be;
    }

    /** What lies on this segment of its belt, by item: the controller holds the belt's whole inventory. */
    private static String onSegment(BeltBlockEntity belt) {
        BeltBlockEntity controller = belt.getControllerBE();
        Map<String, Integer> totals = new TreeMap<>();
        if (controller != null && controller.getInventory() != null) {
            for (TransportedItemStack item : controller.getInventory().getTransportedItems()) {
                if ((int) Math.floor(item.beltPosition) != belt.index) continue;
                totals.merge(BuiltInRegistries.ITEM.getKey(item.stack.getItem()).getPath(), item.stack.getCount(), Integer::sum);
            }
        }
        return totals.toString();
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
