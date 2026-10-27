package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.redstone.link.controller.LinkedControllerClientHandler;
import com.therappha.tinyblocks.compat.create.client.VirtualView;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A linked controller binding to a tiny link holds the link's virtual position; while binding, the client level
 * answers it from the link's grid (VirtualView#lookAt), so the key pressed binds to that link.
 */
@Mixin(value = LinkedControllerClientHandler.class, remap = false)
public abstract class LinkedControllerClientHandlerMixin {

    @Shadow private static BlockPos selectedLocation;

    @Unique private static boolean tinyblocks$looking;

    @Inject(method = "tick", at = @At("HEAD"))
    private static void tinyblocks$lookAtTheLink(CallbackInfo ci) {
        tinyblocks$looking = LinkedControllerClientHandler.MODE == LinkedControllerClientHandler.Mode.BIND
                && selectedLocation != null && VirtualView.lookAt(selectedLocation);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private static void tinyblocks$lookBack(CallbackInfo ci) {
        if (tinyblocks$looking) VirtualView.stopLookingAt();
        tinyblocks$looking = false;
    }
}
