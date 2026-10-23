package com.therappha.tinyblocks.client.flywheel;

import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.task.TaskExecutor;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.SectionTrackedVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * EXPERIMENT (feat/flywheel-create-experiment). One Flywheel visual per SubgridBlockEntity that
 * hosts the visuals of its pieces' phantom block entities, generically: for each piece whose
 * phantom BE type has a registered visualizer (Create's kinetic blocks, anything else using
 * Flywheel), it asks the public VisualizerRegistry for that visualizer and creates the visual
 * inside its own VisualEmbedding, whose transform is the piece's cell translate + 1/gridSize scale.
 * No per-mod code. Flywheel only drives the visuals IT created, so this class forwards the
 * per-frame / per-tick plans to the children itself.
 */
public class SubgridVisual extends AbstractBlockEntityVisual<SubgridBlockEntity>
        implements SimpleDynamicVisual, SimpleTickableVisual {

    /** Runs a child's Plan inline on the calling (render/client) thread. */
    private static final TaskExecutor INLINE = new TaskExecutor() {
        @Override public void execute(Runnable runnable) { runnable.run(); }
        @Override public int threadCount() { return 1; }
    };

    private record Child(BlockState state, VisualEmbedding embedding, BlockEntityVisual<?> visual) {}

    private final VisualizationContext context;
    private final Map<PlacedPiece, Child> children = new IdentityHashMap<>();

    public SubgridVisual(VisualizationContext context, SubgridBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        this.context = context;
        reconcile(partialTick);
    }

    // --- child lifecycle ---------------------------------------------------------------------

    private void reconcile(float partialTick) {
        List<PlacedPiece> live = new ArrayList<>(blockEntity.getPieces());
        children.entrySet().removeIf(e -> {
            PlacedPiece piece = e.getKey();
            if (live.contains(piece) && piece.definition.renderState(piece) == e.getValue().state()) return false;
            destroy(e.getValue());
            return true;
        });
        for (PlacedPiece piece : live) {
            if (!children.containsKey(piece)) {
                Child child = createChild(piece, partialTick);
                if (child != null) children.put(piece, child);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Child createChild(PlacedPiece piece, float partialTick) {
        if (piece.definition != VanillaBlockPiece.INSTANCE) return null;
        BlockState state = piece.definition.renderState(piece);
        if (state == null || !(state.getBlock() instanceof EntityBlock)) return null;

        BlockEntity phantom = VanillaBlockPiece.blockEntityFor(piece, blockEntity, blockEntity.getLevel());
        if (phantom == null) return null;
        BlockEntityVisualizer visualizer = VisualizerRegistry.getVisualizer(phantom.getType());
        if (visualizer == null) return null;

        BlockPos pos = blockEntity.getBlockPos();
        VisualEmbedding embedding = context.createEmbedding(pos);
        embedding.transforms(cellTransform(piece), new Matrix3f());
        BlockEntityVisual<?> visual = visualizer.createVisual(embedding, phantom, partialTick);
        if (lightSections != null && visual instanceof SectionTrackedVisual tracked) {
            tracked.setSectionCollector(lightSections);
        }
        // Flywheel only calls updateLight on visuals IT created; a child built here would otherwise
        // never get its first light pass and render black (light 0).
        if (lightSections != null && visual instanceof dev.engine_room.flywheel.api.visual.LightUpdatedVisual lit) {
            lit.updateLight(partialTick);
        }
        return new Child(state, embedding, visual);
    }

    /** Embedded space -> world (relative to the parent's render origin): cell translate, then 1/gridSize scale. */
    private Matrix4f cellTransform(PlacedPiece piece) {
        float cell = 1f / blockEntity.gridSize;
        Vec3i origin = context.renderOrigin();
        BlockPos pos = blockEntity.getBlockPos();
        return new Matrix4f()
                .translate(pos.getX() - origin.getX() + piece.anchor.getX() * cell,
                           pos.getY() - origin.getY() + piece.anchor.getY() * cell,
                           pos.getZ() - origin.getZ() + piece.anchor.getZ() * cell)
                .scale(cell);
    }

    private static void destroy(Child child) {
        child.visual().delete();
        child.embedding().delete();
    }

    // --- Flywheel hooks ------------------------------------------------------------------------

    @Override
    public void update(float partialTick) {
        super.update(partialTick);
        reconcile(partialTick);
        for (Child child : children.values()) {
            child.visual().update(partialTick);
        }
    }

    @Override
    public void setSectionCollector(SectionTrackedVisual.SectionCollector collector) {
        super.setSectionCollector(collector);
        for (Child child : children.values()) {
            if (child.visual() instanceof SectionTrackedVisual tracked) tracked.setSectionCollector(collector);
            if (child.visual() instanceof dev.engine_room.flywheel.api.visual.LightUpdatedVisual lit) lit.updateLight(0f);
        }
    }

    @Override
    public void updateLight(float partialTick) {
        for (Child child : children.values()) {
            if (child.visual() instanceof dev.engine_room.flywheel.api.visual.LightUpdatedVisual lit) {
                lit.updateLight(partialTick);
            }
        }
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        reconcile(ctx.partialTick());
        for (Child child : children.values()) {
            if (child.visual() instanceof DynamicVisual dynamic) {
                Plan<DynamicVisual.Context> plan = dynamic.planFrame();
                plan.execute(INLINE, ctx);
            }
        }
    }

    @Override
    public void tick(TickableVisual.Context ctx) {
        for (Child child : children.values()) {
            if (child.visual() instanceof TickableVisual tickable) {
                Plan<TickableVisual.Context> plan = tickable.planTick();
                plan.execute(INLINE, ctx);
            }
        }
    }

    @Override
    public void collectCrumblingInstances(java.util.function.Consumer<dev.engine_room.flywheel.api.instance.Instance> consumer) {
        for (Child child : children.values()) {
            child.visual().collectCrumblingInstances(consumer);
        }
    }

    @Override
    protected void _delete() {
        children.values().forEach(SubgridVisual::destroy);
        children.clear();
    }
}
