package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.therappha.tinyblocks.v2.FakeSpace;
import com.therappha.tinyblocks.v2.PhantomOwners;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets a redstone link inside a subgrid talk to the ones outside. Create files link networks by level instance
 * and reaches a link by its own position: the tiny link's level is its grid's fake space and its position a
 * virtual one millions of blocks away, so it sat alone in a network of its own and was out of range of every
 * real link. Networks are filed under the real level, and a tiny link is where its SubgridBlock is.
 */
@Mixin(RedstoneLinkNetworkHandler.class)
public abstract class RedstoneLinkNetworkHandlerMixin {

    @ModifyVariable(method = "networksIn", at = @At("HEAD"), argsOnly = true)
    private LevelAccessor tinyblocks$realLevel(LevelAccessor world) {
        return world instanceof FakeSpace fake ? fake.realLevel() : world;
    }

    @Redirect(method = "withinRange", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/redstone/link/IRedstoneLinkable;getLocation()Lnet/minecraft/core/BlockPos;"))
    private static BlockPos tinyblocks$realLocation(IRedstoneLinkable link) {
        BlockPos real = link instanceof LinkBehaviour behaviour ? PhantomOwners.realPosOf(behaviour.blockEntity) : null;
        return real != null ? real : link.getLocation();
    }
}
