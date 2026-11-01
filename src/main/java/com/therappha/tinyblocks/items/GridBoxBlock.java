package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;

/**
 * A grid on its way: what a full Grid Save puts down, as big as what it holds, one of these per block it takes up, the
 * first of them holding it all (GridBoxBlockEntity) and drawing the box over the rest (client GridBoxRenderer). A right
 * click on any of them opens it, as a Create package opens, and the grid is placed. Broken, it gives the full Grid Save
 * back, so nothing is lost.
 */
public class GridBoxBlock extends Block implements EntityBlock {

    public GridBoxBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GridBoxBlockEntity(pos, state);
    }

    /** Drawn by its block entity, stretched over all it takes up. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof GridBoxBlockEntity box) box.main().unbox(server, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof GridBoxBlockEntity box) {
            box.main().broken(server);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(Registration.GRID_SAVE.get());
    }
}
