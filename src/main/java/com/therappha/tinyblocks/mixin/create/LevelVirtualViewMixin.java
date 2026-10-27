package com.therappha.tinyblocks.mixin.create;

import com.therappha.tinyblocks.compat.create.client.VirtualView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inside a VirtualView, the client level answers the viewed grid's virtual cube from the grid's fake space. */
@Mixin(Level.class)
public abstract class LevelVirtualViewMixin {

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void tinyblocks$phantomInView(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (VirtualView.answers((Level) (Object) this, pos)) cir.setReturnValue(VirtualView.blockEntityAt(pos));
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void tinyblocks$pieceInView(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (VirtualView.answers((Level) (Object) this, pos)) cir.setReturnValue(VirtualView.blockStateAt(pos));
    }
}
