package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeSpace;
import com.therappha.tinyblocks.v2.PhantomOwners;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create syncs a block entity by marking its position changed in the real chunk cache. A phantom's position is a
 * virtual one (its grid's position times the grid size): near the world origin that lands in a loaded chunk at a
 * height past the world's top, and the chunk holder throws — every tick, so the piece never finishes ticking and
 * rotation stops spreading. A phantom's data goes to clients with its grid's own payload instead.
 */
@Mixin(value = SyncedBlockEntity.class, remap = false)
public abstract class SyncedBlockEntityMixin {

    @Inject(method = "sendData", at = @At("HEAD"), cancellable = true)
    private void tinyblocks$syncThroughTheGrid(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        PhantomOwners.Owner owner = PhantomOwners.of(self);
        SubgridBlockEntity grid = owner == null ? null : owner.grid();
        if (grid != null) {
            grid.markPhantomDataDirty();
            ci.cancel();
        } else if (self.getLevel() instanceof FakeSpace) {
            ci.cancel();
        }
    }
}
