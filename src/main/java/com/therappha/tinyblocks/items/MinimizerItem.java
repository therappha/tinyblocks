package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import net.minecraft.world.item.Item;

/**
 * Held in the offhand: makes any BlockItem in the main hand create/place into a subgrid instead
 * of placing normally. Comes in the same four grid sizes as the debug pieces (default 1/8).
 */
public class MinimizerItem extends Item {

    public MinimizerItem(Properties properties) {
        super(properties);
    }

    /** Which SubgridBlock variant this Minimizer creates when auto-creating a new subgrid. */
    public SubgridBlock preferredSubgrid() {
        return Registration.SUBGRID_BLOCK.get();
    }
}
