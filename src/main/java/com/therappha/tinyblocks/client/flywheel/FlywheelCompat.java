package com.therappha.tinyblocks.client.flywheel;

import com.therappha.tinyblocks.setup.Registration;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * EXPERIMENT (feat/flywheel-create-experiment). The only class that touches Flywheel; everything
 * else guards calls behind {@link FlywheelLoaded#isLoaded()} so the mod still runs without it.
 */
public final class FlywheelCompat {

    private FlywheelCompat() {}

    /** Registers the SubgridBlockEntity's visualizer; call once on client setup when Flywheel is present. */
    public static void register() {
        var type = Registration.SUBGRID_BLOCK_ENTITY.get();
        VisualizerRegistry.setVisualizer(type, SimpleBlockEntityVisualizer.builder(type)
                .factory(SubgridVisual::new)
                // The subgrid's own static pieces still render through SubgridRenderer's BER.
                .neverSkipVanillaRender()
                .apply());
    }

    /** Tells Flywheel the subgrid's visual (and through it every hosted child) should refresh. */
    public static void queueUpdate(BlockEntity subgrid) {
        dev.engine_room.flywheel.lib.visualization.VisualizationHelper.queueUpdate(subgrid);
    }

    /**
     * True when a phantom piece block entity is drawn by a Flywheel visual hosted in a SubgridVisual,
     * so SubgridRenderer must not also run its vanilla BlockEntityRenderer (double render).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean drawnByVisual(Level level, BlockEntity phantom) {
        if (!VisualizationManager.supportsVisualization(level)) return false;
        BlockEntityVisualizer visualizer = VisualizerRegistry.getVisualizer(phantom.getType());
        return visualizer != null && visualizer.skipVanillaRender(phantom);
    }
}
