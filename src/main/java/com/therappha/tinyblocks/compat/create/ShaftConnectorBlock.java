package com.therappha.tinyblocks.compat.create;

import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;

/**
 * A Create shaft that marks itself as a subgrid connector. Placed into a subgrid it becomes a
 * ShaftConnectorPiece: a plate as wide as a full-size shaft on one face of the grid, joining a tiny shaft
 * behind it to the real shaft in front of it. Placed in the world it is an ordinary shaft. It reuses Create's
 * shaft block entity type (see CreateConnectorSetup#addToShaftBlockEntity), so the shaft's own renderer and
 * Flywheel visual draw it.
 */
public class ShaftConnectorBlock extends ShaftBlock {

    public ShaftConnectorBlock(Properties properties) {
        super(properties);
    }

    /**
     * Create's shaft swaps its baked model for one that only draws a bracket (BracketedKineticBlockModel); the
     * shaft itself is drawn turning by its block entity. Without that swap the plain shaft model drew a second,
     * still shaft over the turning one. Only the block entity draws this one.
     */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(net.minecraft.world.level.block.state.BlockState state) {
        return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
    }
}
