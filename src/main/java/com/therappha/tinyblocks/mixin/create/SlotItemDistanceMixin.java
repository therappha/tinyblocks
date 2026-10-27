package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.redstone.link.LinkRenderer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.therappha.tinyblocks.client.SubgridRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The items shown in a link's frequency slots and a filter slot are skipped beyond a distance from the camera,
 * measured to the block entity's position — for a piece's, a virtual one far away — unless the block entity is a
 * virtual (Ponder) one. A piece being drawn counts as such here, so its slots show their items.
 */
@Mixin(value = {LinkRenderer.class, FilteringRenderer.class}, remap = false)
public abstract class SlotItemDistanceMixin {

    @Redirect(method = "renderOnBlockEntity", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;isVirtual()Z"))
    private static boolean tinyblocks$nearWhenAPiece(SmartBlockEntity be) {
        return be.isVirtual() || SubgridRenderer.drawingPhantom;
    }
}
