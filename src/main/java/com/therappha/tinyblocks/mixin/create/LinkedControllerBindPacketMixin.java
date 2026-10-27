package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.redstone.link.controller.LinkedControllerBindPacket;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import com.therappha.tinyblocks.v2.VirtualLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Binding a linked controller to a tiny link: the packet names the link's virtual position (see BlockEntityConfigurationPacketMixin). */
@Mixin(value = LinkedControllerBindPacket.class, remap = false)
public abstract class LinkedControllerBindPacketMixin {

    @Redirect(method = "handleItem", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/BlockEntityBehaviour;get(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lcom/simibubi/create/foundation/blockEntity/behaviour/BehaviourType;)Lcom/simibubi/create/foundation/blockEntity/behaviour/BlockEntityBehaviour;"))
    private <T extends BlockEntityBehaviour> T tinyblocks$theTinyLink(BlockGetter level, BlockPos pos, BehaviourType<T> type,
                                                                     ServerPlayer player, ItemStack heldItem) {
        if (player.level() instanceof ServerLevel serverLevel) {
            VirtualLookup.Found found = VirtualLookup.find(serverLevel, pos, player.position());
            if (found != null) {
                return BlockEntityBehaviour.get(VanillaBlockPiece.boundPhantom(found.piece(), found.grid(), serverLevel), type);
            }
        }
        return BlockEntityBehaviour.get(level, pos, type);
    }
}
