package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import com.therappha.tinyblocks.v2.VirtualLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Create configuration packet (a value box set, a filter, ...) names the block entity by position. For a piece
 * that is its phantom's virtual position, which the client got its hit and block entity from (VirtualView); the
 * server finds the piece from it and configures the phantom, with the range checked to the SubgridBlock. A
 * position that is a real block in reach is left to Create.
 */
@Mixin(value = BlockEntityConfigurationPacket.class, remap = false)
public abstract class BlockEntityConfigurationPacketMixin {

    @Shadow @Final protected BlockPos pos;

    @Shadow protected abstract void applySettings(ServerPlayer player, SyncedBlockEntity be);

    @Shadow protected abstract int maxRange();

    @Shadow protected abstract boolean causeUpdate();

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void tinyblocks$configureAPiece(ServerPlayer player, CallbackInfo ci) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        if (level.isLoaded(pos) && player.canInteractWithBlock(pos, maxRange())) return;
        VirtualLookup.Found found = VirtualLookup.find(level, pos, player.position());
        if (found == null) return;
        ci.cancel();
        if (player.isSpectator() || !player.mayBuild()) return;
        if (!player.canInteractWithBlock(found.grid().getBlockPos(), maxRange())) return;
        BlockEntity phantom = VanillaBlockPiece.boundPhantom(found.piece(), found.grid(), level);
        if (!(phantom instanceof SyncedBlockEntity synced)) return;
        applySettings(player, synced);
        found.grid().setChanged(); // the settings live in the phantom, saved with the grid
        if (!causeUpdate()) return;
        synced.sendData();
        phantom.setChanged();
    }
}
