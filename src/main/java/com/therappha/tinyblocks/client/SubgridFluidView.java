package com.therappha.tinyblocks.client;

import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nullable;

/**
 * A fluid piece's BlockState has RenderShape.INVISIBLE (same as any real water/lava block) —
 * BlockRenderDispatcher#renderSingleBlock (what SubgridRenderer already uses for every other
 * piece) explicitly skips INVISIBLE render shapes, since fluids don't have a normal baked model
 * at all; they render through a completely separate path, BlockRenderDispatcher#renderLiquid,
 * which needs a genuine BlockAndTintGetter (to sample neighboring fluid heights for the corner-
 * height mesh calculation, plus light/tint) rather than a single BlockState. This is that view,
 * client-side and local-grid-cell-based (mirrors SubgridBlockEntity#realBlockStateAt, which
 * already has the right in-bounds/cross-grid-edge fallback logic), just enough of
 * BlockAndTintGetter for LiquidBlockRenderer#tesselate to work: block/fluid state lookups per
 * cell, light/tint substituted from the subgrid's own one real-world position (same pattern
 * v2.FakeLevel already uses for light/biome queries — a subgrid doesn't have per-cell sky access
 * of its own).
 */
final class SubgridFluidView implements BlockAndTintGetter {

    private final SubgridBlockEntity be;
    private final Level real;

    SubgridFluidView(SubgridBlockEntity be, Level real) {
        this.be = be;
        this.real = real;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return be.realBlockStateAt(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        // Past the edge, the next grid's piece, as getBlockState answers: a model asking whether its neighbour is part of
        // the same structure (a Create vault's connected textures) would otherwise be told no at every seam.
        var piece = switch (be.resolve(pos.getX(), pos.getY(), pos.getZ())) {
            case SubgridBlockEntity.CellRef.Local l -> be.getPieceAt(l.x(), l.y(), l.z());
            case SubgridBlockEntity.CellRef.InAdjacentGrid a -> a.grid().getPieceAt(a.x(), a.y(), a.z());
            default -> null;
        };
        return piece != null && piece.runtimeBlockEntity instanceof BlockEntity blockEntity ? blockEntity : null;
    }

    @Override
    public int getHeight() {
        return be.getGridSize();
    }

    @Override
    public int getMinBuildHeight() {
        return 0;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return real.getShade(direction, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return real.getLightEngine();
    }

    /**
     * LiquidBlockRenderer's own
     * lighting (LevelRenderer#getLightColor) reads brightness through these two BlockAndTintGetter
     * default methods, NOT getLightEngine() directly — overriding them to substitute the
     * subgrid's one real-world position (same pattern FakeLevel already uses for its own
     * getBrightness/getRawBrightness) avoids querying the real light engine at a fake, tiny local
     * coordinate, which would return whatever real light level happens to exist near world origin
     * rather than anything related to the subgrid's actual surroundings.
     */
    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return real.getBrightness(layer, be.getBlockPos());
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        return real.getRawBrightness(be.getBlockPos(), amount);
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return real.getBlockTint(be.getBlockPos(), resolver);
    }
}
