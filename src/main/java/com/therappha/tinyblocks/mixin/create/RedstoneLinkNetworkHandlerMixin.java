package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.therappha.tinyblocks.v2.FakeSpace;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets a redstone link inside a subgrid talk to the ones outside. Create files link networks by level instance:
 * the tiny link's level is its grid's fake space, so it sat alone in a network of its own. Networks are filed
 * under the real level, and a network update runs against it (Sable's range check reads the update's level to
 * find sub-levels). The range itself is measured from where the SubgridBlock is: see LinkBehaviourMixin.
 */
@Mixin(RedstoneLinkNetworkHandler.class)
public abstract class RedstoneLinkNetworkHandlerMixin {

    @ModifyVariable(method = "networksIn", at = @At("HEAD"), argsOnly = true)
    private LevelAccessor tinyblocks$realLevel(LevelAccessor world) {
        return world instanceof FakeSpace fake ? fake.realLevel() : world;
    }

    @ModifyVariable(method = "updateNetworkOf", at = @At("HEAD"), argsOnly = true)
    private LevelAccessor tinyblocks$updateInRealLevel(LevelAccessor world) {
        return world instanceof FakeSpace fake ? fake.realLevel() : world;
    }
}
