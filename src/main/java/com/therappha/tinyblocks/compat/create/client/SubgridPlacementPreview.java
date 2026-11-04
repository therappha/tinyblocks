package com.therappha.tinyblocks.compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.client.GhostVertexConsumer;
import com.therappha.tinyblocks.client.SubLevelView;
import com.therappha.tinyblocks.compat.create.ConnectorPlacement;
import com.therappha.tinyblocks.compat.create.ShaftConnectorBlock;
import com.therappha.tinyblocks.compat.create.ShaftConnectorPiece;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeLevel;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import com.therappha.tinyblocks.v2.VirtualHit;
import net.createmod.catnip.ghostblock.GhostBlocks;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementClient;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.createmod.ponder.render.VirtualRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import javax.annotation.Nullable;

/**
 * Create's placement assist inside a subgrid: holding a Create block over a piece shows, cell-sized, the ghost of
 * where and how the piece's placement helper would put it (a shaft at the end of a shaft line), as Create shows it
 * for full-size blocks.
 *
 * Create's own pass (PlacementClient#checkHelpers) asks about the block under the crosshair, which is the
 * SubgridBlock; PlacementClientMixin runs this one after it. The helper is run against the grid's fake space with
 * the hit in the virtual frame — what the server does on the click (VanillaBlockPiece#onUse hands the held block
 * to the clicked piece's own use, which runs the same helper), so the ghost shows where the block will go.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT)
public final class SubgridPlacementPreview {

    /** The ghost to draw: a block state, with the real-level corner and size of the cell it goes in. */
    public record Ghost(Vec3 corner, double cellSize, BlockState state) {}

    @Nullable private static Ghost ghost;

    private SubgridPlacementPreview() {}

    /** This tick's ghost, or null. */
    @Nullable
    public static Ghost current() {
        return ghost;
    }

    /**
     * Works out this tick's ghost, if a piece's placement helper takes the block in hand. Returns the real block
     * the ghost is in, for Create's on-screen arrow pointing at it, or null when there is none.
     */
    @Nullable
    public static BlockPos tick() {
        ghost = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.isShiftKeyDown()) return null;
        if (!(mc.hitResult instanceof BlockHitResult ray) || ray.getType() == HitResult.Type.MISS) return null;
        if (!(mc.level.getBlockEntity(ray.getBlockPos()) instanceof SubgridBlockEntity grid)) return null;
        if (MinimizerItem.isOff(mc.player.getOffhandItem())) return null; // whole blocks

        // Only the main hand: that's the hand VanillaBlockPiece#onUse hands to the piece.
        ItemStack held = mc.player.getMainHandItem();
        // The shaft connector clicked onto a tiny shaft goes on the grid face (ConnectorPlacement#placeFrom),
        // not where the shaft helper it also matches would put it.
        if (!(held.getItem() instanceof BlockItem blockItem) || blockItem.getBlock() instanceof ShaftConnectorBlock) return null;

        // Without a Minimizer, a connector's wide end takes a normal block outside the grid (SubgridEventHandler).
        if (!MinimizerItem.isOn(mc.player.getOffhandItem())
                && ConnectorPlacement.goesOutside(grid, blockItem.getBlock(), ray)) {
            BlockPos outside = ray.getBlockPos().relative(ray.getDirection());
            if (!mc.level.getBlockState(outside).canBeReplaced()) return null;
            BlockState outsideState = blockItem.getBlock().getStateForPlacement(new BlockPlaceContext(
                    mc.player, InteractionHand.MAIN_HAND, held, ray));
            if (outsideState == null) return null;
            ghost = new Ghost(Vec3.atLowerCornerOf(outside), 1,
                    ConnectorPlacement.alignedOutside(outsideState, ray.getDirection()));
            return outside;
        }

        VirtualHit hit = VirtualHit.of(grid, ray);
        PlacedPiece piece = grid.getPieceAt(hit.cell().getX(), hit.cell().getY(), hit.cell().getZ());
        if (piece == null || !(piece.definition instanceof VanillaBlockPiece)
                || !(piece.runtimeState instanceof BlockState state)) return null;

        double cellSize = 1.0 / grid.getGridSize();
        if (ShaftConnectorPiece.faceOf(piece) != null) {
            // A connector places by its own rule (into a funnel's socket, a small cogwheel onto a gear's side), not
            // by the shaft it stands in as: show where that puts the block.
            var target = ConnectorPlacement.targetOn(grid, piece, blockItem.getBlock(), ray.getLocation());
            if (target == null) return null;
            Vec3 corner = hit.realCornerOf(grid.virtualPosOf(target.cell()));
            ghost = new Ghost(corner, cellSize, target.state());
            return BlockPos.containing(corner.add(cellSize / 2, cellSize / 2, cellSize / 2));
        }

        FakeLevel space = null;
        for (IPlacementHelper helper : PlacementHelpers.getHelpersView()) {
            if (!helper.matchesItem(held) || !helper.matchesState(state)) continue;
            if (space == null) space = VanillaBlockPiece.buildClientFakeSpace(grid, mc.level);
            PlacementOffset offset = helper.getOffset(mc.player, space, state, hit.hit().getBlockPos(), hit.hit(), held);
            if (!offset.isSuccessful() || !offset.hasGhostState()) continue;

            Vec3 corner = hit.realCornerOf(offset.getBlockPos());
            ghost = new Ghost(corner, cellSize, offset.getTransform().apply(offset.getGhostState()));
            return BlockPos.containing(corner.add(cellSize / 2, cellSize / 2, cellSize / 2));
        }
        return null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Ghost current = ghost;
        if (current == null) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = SubLevelView.enter(event, BlockPos.containing(current.corner()));
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // Create's transparent ghost: breathing, fading in with the placement indicator, a little smaller than the cell.
        float alpha = (float) GhostBlocks.getBreathingAlpha() * 0.75f * PlacementClient.getCurrentAlpha();
        GhostVertexConsumer consumer = new GhostVertexConsumer(buffers.getBuffer(RenderType.translucent()), (int) (alpha * 255));

        poseStack.pushPose();
        poseStack.translate(current.corner().x - camera.x, current.corner().y - camera.y, current.corner().z - camera.z);
        poseStack.scale((float) current.cellSize(), (float) current.cellSize(), (float) current.cellSize());
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.scale(0.85f, 0.85f, 0.85f);
        poseStack.translate(-0.5, -0.5, -0.5);
        // A model hands out its quads per render type (a shaft's are all solid): ask for each of its own types,
        // and draw them all see-through. Virtual model data, as Create's own ghosts and Ponder scenes use: a model
        // that dresses the block from the world around it (a shaft's bracket) otherwise draws nothing without it.
        var model = mc.getBlockRenderer().getBlockModel(current.state());
        ModelData data = VirtualRenderHelper.VIRTUAL_DATA;
        for (RenderType layer : model.getRenderTypes(current.state(), RandomSource.create(42L), data)) {
            mc.getBlockRenderer().getModelRenderer().renderModel(poseStack.last(), consumer, current.state(), model,
                    1f, 1f, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, data, layer);
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.translucent());
        SubLevelView.exit(event);
    }

}
