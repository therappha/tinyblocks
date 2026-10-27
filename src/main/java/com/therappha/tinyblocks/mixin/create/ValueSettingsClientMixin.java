package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsClient;
import com.therappha.tinyblocks.compat.create.client.VirtualView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A value box held down on a piece: Create checks every tick that the crosshair is still on it and opens the value
 * board from it, against the phantom's virtual position the click started with (see VirtualView).
 */
@Mixin(value = ValueSettingsClient.class, remap = false)
public abstract class ValueSettingsClientMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void tinyblocks$lookIntoTheGrid(CallbackInfo ci) {
        VirtualView.begin();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void tinyblocks$lookBack(CallbackInfo ci) {
        VirtualView.end();
    }
}
