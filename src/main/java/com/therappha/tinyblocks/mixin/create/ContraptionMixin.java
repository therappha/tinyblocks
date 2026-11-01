package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import com.therappha.tinyblocks.v2.FakeSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A contraption can't assemble in a grid. It is an entity, and a fake space holds none: its blocks would leave the grid
 * and the entity meant to carry them would be dropped, losing them for good. Refused where every contraption starts
 * looking for its blocks, before any is touched, as Create itself refuses an assembly it can't do.
 */
@Mixin(value = Contraption.class, remap = false)
public abstract class ContraptionMixin {

    @Inject(method = "searchMovedStructure", at = @At("HEAD"))
    private void tinyblocks$noContraptionsInAGrid(Level world, BlockPos pos, Direction forcedDirection, CallbackInfoReturnable<Boolean> cir)
            throws AssemblyException {
        if (world instanceof FakeSpace) throw new AssemblyException(Component.translatable("tinyblocks.contraption.in_grid"));
    }
}
