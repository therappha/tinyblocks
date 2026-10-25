package com.therappha.tinyblocks.mixin;

import com.therappha.tinyblocks.v2.PhantomInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @ModifyVariable(method = "canInteractWithBlock", at = @At("HEAD"), argsOnly = true)
    private BlockPos tinyblocks$redirectPhantomPosition(BlockPos pos) {
        return PhantomInteraction.redirect((Player) (Object) this, pos);
    }
}
