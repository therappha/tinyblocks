package com.therappha.tinyblocks.client;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts each grid's StaticMesh in the chunk section holding it, so vanilla keeps it on the GPU, culls it
 * and draws it to the render distance. Light is the grid's own, as SubgridRenderer gives it: one value per grid.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SubgridChunkMesh {

    private SubgridChunkMesh() {}

    private record Grid(StaticMesh mesh, BlockPos pos) {}

    /** On the render thread, so the meshes are built here; the renderer added runs on a compile thread and only reads them. */
    @SubscribeEvent
    public static void onSectionCompile(AddSectionGeometryEvent event) {
        BlockPos origin = event.getSectionOrigin();
        SectionPos section = SectionPos.of(origin);
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        List<Grid> grids = new ArrayList<>();
        for (BlockEntity be : event.getLevel().getChunkAt(origin).getBlockEntities().values()) {
            if (be instanceof SubgridBlockEntity grid && SectionPos.of(be.getBlockPos()).equals(section)) {
                StaticMesh mesh = StaticMesh.of(grid, blockRenderer);
                mesh.inChunk = true;
                grids.add(new Grid(mesh, be.getBlockPos().immutable()));
            }
        }
        if (grids.isEmpty()) return;
        event.addRenderer(context -> {
            for (Grid grid : grids) {
                BlockPos pos = grid.pos();
                grid.mesh().writeToChunk(context, pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ(),
                        LevelRenderer.getLightColor(context.getRegion(), pos));
            }
        });
    }
}
