package com.therappha.tinyblocks.compat.create.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.mixin.create.OutlineParamsAccessor;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeLevel;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import com.therappha.tinyblocks.v2.VirtualHit;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import javax.annotation.Nullable;

/**
 * Create's client code for value boxes — the slot outlines and hover tips, the hold-to-edit value board, a link's
 * frequency slots — reads the crosshair target (Minecraft#hitResult) and looks the block entity up in the client
 * level at that position. Over a piece that finds the SubgridBlock, which has none of it.
 *
 * While such code runs (VirtualViewMixins wraps it in {@link #begin}/{@link #end}), the crosshair target is the
 * piece's hit in the grid's virtual frame (VirtualHit) and the client level answers positions in that grid's virtual
 * cube from its fake space, so the code finds the piece's phantom where it looks. What it draws goes into an
 * outliner of its own, drawn shrunk onto the grid; what it sends names the phantom's virtual position, which the
 * server resolves (BlockEntityConfigurationPacketMixin).
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT)
public final class VirtualView {

    private static final Outliner OUTLINER = new Outliner();

    private static int depth;
    @Nullable private static HitResult realHit;
    /** The view the level lookups answer from, or null when they are the real level's. */
    @Nullable private static View lookup;
    /** The grid the tiny outliner was last filled for: its outlines are in that grid's virtual frame. */
    @Nullable private static View drawn;

    private record View(Level level, FakeLevel space, BlockPos realPos, BlockPos origin, int size) {
        boolean covers(BlockPos pos) {
            return pos.getX() >= origin.getX() && pos.getX() < origin.getX() + size
                    && pos.getY() >= origin.getY() && pos.getY() < origin.getY() + size
                    && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + size;
        }
    }

    private VirtualView() {}

    /** Points the crosshair into the grid under it, if a piece is under it. Nests; pair with {@link #end}. */
    public static void begin() {
        if (depth++ > 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return;
        if (!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof SubgridBlockEntity grid)) return;
        VirtualHit virtual = VirtualHit.of(grid, hit);
        if (grid.getPieceAt(virtual.cell().getX(), virtual.cell().getY(), virtual.cell().getZ()) == null) return;

        View view = new View(mc.level, VanillaBlockPiece.buildClientFakeSpace(grid, mc.level), grid.getBlockPos(),
                grid.virtualOrigin(), grid.getGridSize());
        realHit = mc.hitResult;
        mc.hitResult = virtual.hit();
        lookup = view;
        drawn = view;
    }

    public static void end() {
        if (depth == 0 || --depth > 0) return;
        if (realHit != null) Minecraft.getInstance().hitResult = realHit;
        realHit = null;
        lookup = null;
    }

    /** True while the crosshair points into a grid. */
    public static boolean active() {
        return realHit != null;
    }

    /** The tiny outliner, for Create code run inside a view. */
    public static Outliner outliner() {
        return OUTLINER;
    }

    /** Whether {@code level} should answer {@code pos} from the view instead (see LevelVirtualViewMixin). */
    public static boolean answers(Level level, BlockPos pos) {
        View view = lookup;
        return view != null && view.level() == level && view.covers(pos);
    }

    @Nullable
    public static BlockEntity blockEntityAt(BlockPos pos) {
        View view = lookup;
        return view == null ? null : view.space().getBlockEntity(pos);
    }

    public static BlockState blockStateAt(BlockPos pos) {
        View view = lookup;
        return view == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : view.space().getBlockState(pos);
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        // A view left open by code that threw halfway must not outlive the tick.
        while (depth > 0) end();
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        OUTLINER.tickOutlines();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        View view = drawn;
        if (view == null || OUTLINER.getOutlines().isEmpty()) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack ms = event.getPoseStack();
        ms.pushPose();
        // Virtual block (0,0,0) of the grid is its SubgridBlock's corner, every virtual block a cell.
        ms.translate(view.realPos().getX() - camera.x, view.realPos().getY() - camera.y, view.realPos().getZ() - camera.z);
        float cell = 1f / view.size();
        ms.scale(cell, cell, cell);

        // Lines drawn this small would vanish: thicken them, to about a quarter of a full-size outline.
        float widen = view.size() / 4f;
        OUTLINER.getOutlines().values().forEach(entry -> {
            OutlineParamsAccessor params = (OutlineParamsAccessor) entry.getOutline().getParams();
            params.tinyblocks$setLineWidth(params.tinyblocks$lineWidth() * widen);
        });
        SuperRenderTypeBuffer buffer = DefaultSuperRenderTypeBuffer.getInstance();
        lookup = view; // a value box reads its block's state at the virtual position while it draws
        try {
            OUTLINER.renderOutlines(ms, buffer, Vec3.atLowerCornerOf(view.origin()),
                    event.getPartialTick().getGameTimeDeltaPartialTick(false));
            buffer.draw();
        } finally {
            lookup = null;
            OUTLINER.getOutlines().values().forEach(entry -> {
                OutlineParamsAccessor params = (OutlineParamsAccessor) entry.getOutline().getParams();
                params.tinyblocks$setLineWidth(params.tinyblocks$lineWidth() / widen);
            });
        }
        RenderSystem.enableCull();
        ms.popPose();
    }
}
