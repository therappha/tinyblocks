package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.redstone.link.LinkHandler;
import com.therappha.tinyblocks.v2.PieceClickDispatch;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A click on a tiny link comes in from PieceClickDispatch's stand-in, a FakePlayer. Create lets a fake player
 * (a deployer) set the slot nearest its hit as well as the one its ray hits, without stopping at the first: when
 * the two differ, one click set both frequencies. The stand-in re-traces a real player's aim, so it counts as one.
 */
@Mixin(value = LinkHandler.class, remap = false)
public abstract class LinkHandlerMixin {

    @ModifyVariable(method = "onBlockActivated", at = @At("STORE"), ordinal = 0)
    private static boolean tinyblocks$theStandInAimsLikeAPlayer(boolean fakePlayer, PlayerInteractEvent.RightClickBlock event) {
        return fakePlayer && !PieceClickDispatch.isStandIn(event.getEntity());
    }
}
