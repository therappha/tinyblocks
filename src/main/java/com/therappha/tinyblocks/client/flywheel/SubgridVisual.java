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

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static int lightLogBudget = 3;

    private final VisualizationContext context;
    /** Level the hosted phantoms are bound to; answers light at this SubgridBlock's real position. */
    private final net.minecraft.world.level.Level hostLevel;
    private int frames = 0;
    /** One child per slice of the piece's model (PieceTransforms): one for most pieces, several for a funnel. */
    private final Map<PlacedPiece, List<Child>> children = new IdentityHashMap<>();
    /** For a shaft connector's children, the grid size across its face they were built for (its funnel's start). */
    private final Map<PlacedPiece, Integer> builtAcross = new IdentityHashMap<>();

    private List<Child> allChildren() {
        List<Child> all = new ArrayList<>();
        children.values().forEach(all::addAll);
        return all;
    }

    public SubgridVisual(VisualizationContext context, SubgridBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        this.context = context;
        this.hostLevel = VanillaBlockPiece.buildClientFakeSpace(blockEntity, blockEntity.getLevel());
        reconcile(partialTick);
    }

    // --- child lifecycle ---------------------------------------------------------------------

    private void reconcile(float partialTick) {
        List<PlacedPiece> live = new ArrayList<>(blockEntity.getPieces());
        children.entrySet().removeIf(e -> {
            PlacedPiece piece = e.getKey();
            // A connector whose neighbour across changed (a grid placed or broken there) is rebuilt to match it.
            if (live.contains(piece) && piece.definition.renderState(piece) == e.getValue().get(0).state()
                    && builtAcross.getOrDefault(piece, 0) == com.therappha.tinyblocks.client.SubgridRenderer.acrossOf(blockEntity, piece)) {
                return false;
            }
            e.getValue().forEach(SubgridVisual::destroy);
            builtAcross.remove(piece);
            return true;
        });
        for (PlacedPiece piece : live) {
            if (!children.containsKey(piece)) {
                List<Child> slices = createChildren(piece, partialTick);
                if (!slices.isEmpty()) children.put(piece, slices);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Child> createChildren(PlacedPiece piece, float partialTick) {
        if (!(piece.definition instanceof VanillaBlockPiece)) return List.of();
        BlockState state = piece.definition.renderState(piece);
        if (state == null || !(state.getBlock() instanceof EntityBlock)) return List.of();

        BlockEntity phantom = VanillaBlockPiece.blockEntityFor(piece, blockEntity, hostLevel);
        if (phantom == null) return List.of();
        BlockEntityVisualizer visualizer = VisualizerRegistry.getVisualizer(phantom.getType());
        if (visualizer == null) return List.of();

        List<Child> slices = new ArrayList<>();
        int across = com.therappha.tinyblocks.client.SubgridRenderer.acrossOf(blockEntity, piece);
        builtAcross.put(piece, across);
        List<com.therappha.tinyblocks.client.PieceTransforms.Slice> transforms =
                com.therappha.tinyblocks.client.PieceTransforms.slices(piece, blockEntity.gridSize, across);
        boolean gear = com.therappha.tinyblocks.compat.create.ShaftConnectorPiece.faceOf(piece) != null
                && com.therappha.tinyblocks.compat.create.ShaftConnectorPiece.isGear(blockEntity.gridSize)
                && phantom instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity;
        for (int i = 0; i < transforms.size(); i++) {
            int slice = i;
            // A gear connector is drawn from Create's parts (shaftless gear, shaft end), not as the cogwheel it stands in as.
            slices.add(createChild(gear
                    ? embedding -> com.therappha.tinyblocks.compat.create.client.GearConnectorDrawing.visual(embedding,
                            (com.simibubi.create.content.kinetics.base.KineticBlockEntity) phantom, slice, partialTick,
                            blockEntity.getBlockPos().relative(piece.facing))
                    : embedding -> visualizer.createVisual(embedding, phantom, partialTick),
                    phantom, state, transforms.get(i), partialTick));
        }
        return slices;
    }

    private Child createChild(java.util.function.Function<VisualizationContext, BlockEntityVisual<?>> visualOf, BlockEntity phantom,
                              BlockState state, com.therappha.tinyblocks.client.PieceTransforms.Slice slice, float partialTick) {
        // Anchor the embedding at the phantom's OWN position so the hosted visual (which places itself at
        // blockEntity.getBlockPos() - renderOrigin) sits at the embedding's origin; sliceTransform then
        // carries it to the slice's real-world place.
        VisualEmbedding embedding = context.createEmbedding(phantom.getBlockPos());
        embedding.transforms(sliceTransform(slice), new Matrix3f());
        BlockEntityVisual<?> visual = visualOf.apply(embedding);
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

    /** Embedded space -> world (relative to the parent's render origin): the slice's translate, then its scale. */
    private Matrix4f sliceTransform(com.therappha.tinyblocks.client.PieceTransforms.Slice slice) {
        Vec3i origin = context.renderOrigin();
        BlockPos pos = blockEntity.getBlockPos();
        return new Matrix4f()
                .translate(pos.getX() - origin.getX() + slice.translation().x,
                           pos.getY() - origin.getY() + slice.translation().y,
                           pos.getZ() - origin.getZ() + slice.translation().z)
                .scale(slice.scale());
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
        for (Child child : allChildren()) {
            child.visual().update(partialTick);
            // The hosted visuals' own update() can reset an instance's light; Flywheel only re-runs
            // updateLight when a light section changes, so redo it right away instead of leaving the
            // piece dark until the periodic pass below.
            if (lightSections != null
                    && child.visual() instanceof dev.engine_room.flywheel.api.visual.LightUpdatedVisual lit) {
                lit.updateLight(partialTick);
            }
        }
    }

    @Override
    public void setSectionCollector(SectionTrackedVisual.SectionCollector collector) {
        super.setSectionCollector(collector);
        for (Child child : allChildren()) {
            if (child.visual() instanceof SectionTrackedVisual tracked) tracked.setSectionCollector(collector);
            if (child.visual() instanceof dev.engine_room.flywheel.api.visual.LightUpdatedVisual lit) lit.updateLight(0f);
        }
    }

    @Override
    public void updateLight(float partialTick) {
        logLight("updateLight");
        for (Child child : allChildren()) {
            if (child.visual() instanceof dev.engine_room.flywheel.api.visual.LightUpdatedVisual lit) {
                lit.updateLight(partialTick);
            }
        }
    }

    private void logLight(String where) {
        if (lightLogBudget <= 0) return;
        lightLogBudget--;
        int packed = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, blockEntity.getBlockPos());
        LOGGER.info("[fw-light] {} subgrid={} sections={} packed=0x{} (block={} sky={}) children={}",
                where, blockEntity.getBlockPos(), lightSections != null, Integer.toHexString(packed),
                (packed >> 4) & 0xF, (packed >> 20) & 0xF, children.size());
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        reconcile(ctx.partialTick());
        // Safety net: re-run the children's first-light pass about once a second, in case anything
        // reset or skipped it. Cheap (one relight per hosted visual).
        if (++frames % 120 == 0 && lightSections != null) {
            for (Child child : allChildren()) {
                if (child.visual() instanceof dev.engine_room.flywheel.api.visual.LightUpdatedVisual lit) {
                    lit.updateLight(ctx.partialTick());
                }
            }
        }
        for (Child child : allChildren()) {
            if (child.visual() instanceof DynamicVisual dynamic) {
                Plan<DynamicVisual.Context> plan = dynamic.planFrame();
                plan.execute(INLINE, ctx);
            }
        }
    }

    @Override
    public void tick(TickableVisual.Context ctx) {
        for (Child child : allChildren()) {
            if (child.visual() instanceof TickableVisual tickable) {
                Plan<TickableVisual.Context> plan = tickable.planTick();
                plan.execute(INLINE, ctx);
            }
        }
    }

    @Override
    public void collectCrumblingInstances(java.util.function.Consumer<dev.engine_room.flywheel.api.instance.Instance> consumer) {
        for (Child child : allChildren()) {
            child.visual().collectCrumblingInstances(consumer);
        }
    }

    @Override
    protected void _delete() {
        allChildren().forEach(SubgridVisual::destroy);
        children.clear();
        builtAcross.clear();
    }
}
