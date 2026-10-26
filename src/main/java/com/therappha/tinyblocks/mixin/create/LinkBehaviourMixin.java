package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.therappha.tinyblocks.v2.PhantomOwners;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A link stays in its network while its level still holds it at its position. A tiny link's level is whichever
 * space it was last bound to — between ticks possibly the real level, which has nothing at a virtual position —
 * so it would drop out. It is alive while it is still its piece's phantom.
 */
@Mixin(LinkBehaviour.class)
public abstract class LinkBehaviourMixin {

    @Inject(method = "isAlive", at = @At("HEAD"), cancellable = true)
    private void tinyblocks$aliveWhileItsPieceIs(CallbackInfoReturnable<Boolean> cir) {
        var blockEntity = ((BlockEntityBehaviour) (Object) this).blockEntity;
        if (PhantomOwners.of(blockEntity) != null) cir.setReturnValue(!blockEntity.isRemoved());
    }
}
